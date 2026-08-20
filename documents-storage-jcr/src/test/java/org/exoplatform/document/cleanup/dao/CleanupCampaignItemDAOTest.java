/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.exoplatform.document.cleanup.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.query.parser.PartTree;

import org.exoplatform.document.cleanup.constant.CleanupAction;
import org.exoplatform.document.cleanup.constant.CleanupItemState;
import org.exoplatform.document.cleanup.entity.CleanupCampaignEntity;
import org.exoplatform.document.cleanup.entity.CleanupCampaignItemEntity;
import org.exoplatform.document.cleanup.rest.CleanupCampaignRest;
import org.exoplatform.document.cleanup.storage.CleanupCampaignStorage;
import org.exoplatform.document.cleanup.util.CleanupConstants;

import javax.xml.parsers.DocumentBuilderFactory;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Parameter;
import jakarta.persistence.metamodel.EntityType;

/**
 * Guards the hand-written {@code @Query} JPQL of the cleanup DAOs, which nothing
 * else in this module's plain-JUnit suite executes.
 * <p>
 * The core guard is a REAL PARSE: every {@code @Query} value of both cleanup
 * repositories is handed to Hibernate through a minimal in-memory
 * {@code EntityManagerFactory} built over the two cleanup entities only (no
 * PortalContainer, no Spring context, no schema). Hibernate resolves the entity
 * names, the aliases and every {@code alias.field} path while parsing, so a
 * renamed entity, a renamed/removed field and a plain syntax error all fail HERE
 * instead of at Spring Data repository bootstrap in production. The parsed
 * query's own named-parameter list is then compared, in BOTH directions, with the
 * method's {@code @Param} values — that catches the one mutant a parse cannot see
 * (a {@code @Param} renamed while the query keeps the old {@code :token}, which
 * only blows up when Spring Data binds the arguments).
 * <p>
 * A second, narrower guard EXECUTES instead of parsing, over a schema-generating
 * bootstrap kept apart from the parse-only one: the reclaimable arithmetic
 * against real rows, and — through a real Spring Data repository proxy obtained
 * from that same {@code EntityManager} — the whole ordering path of
 * {@code GET published/my-items}, which nothing else in this module executes end
 * to end.
 * <p>
 * NOT covered, by construction: anything that needs the PRODUCTION database —
 * dialect specifics, collation-dependent {@code LIKE}/{@code LOWER} behaviour and
 * index usage.
 */
class CleanupCampaignItemDAOTest {

  /**
   * Both cleanup repositories: the guard must cover EVERY {@code @Query} of the
   * package, not only the two search ones, and must start covering a query added
   * to {@link CleanupCampaignDAO} later without anybody remembering this test.
   */
  private static final List<Class<?>> CLEANUP_DAOS = List.of(CleanupCampaignItemDAO.class, CleanupCampaignDAO.class);

  /** Each cleanup repository with the entity its derived names must resolve against. */
  private static final Map<Class<?>, Class<?>> CLEANUP_DAO_ENTITIES =
                                                                    Map.of(CleanupCampaignItemDAO.class,
                                                                           CleanupCampaignItemEntity.class,
                                                                           CleanupCampaignDAO.class,
                                                                           CleanupCampaignEntity.class);

  private static final String        ITEM_TABLE         = "DOCUMENTS_CLEANUP_CAMPAIGN_ITEM";

  private static final String        CHANGELOG_RESOURCE = "db/changelog/documents-cleanup-rdbms.db.changelog-1.1.0.xml";

  /** One megabyte, so the fixtures below read as the sizes the finding described. */
  private static final long          MB                 = 1024L * 1024L;

  /**
   * The owner every fixture row below belongs to — the one the review list is
   * queried for, {@code findByOwnersAndSearch} being an OWNER-scoped query.
   */
  private static final long          OWNER_IDENTITY_ID  = 1L;

  private static SessionFactory       sessionFactory;

  /**
   * A SECOND Hibernate bootstrap over the same two entities, this one WITH a
   * generated schema: the tests below do not parse the reclaimable expression,
   * they RUN it over rows. Kept apart from the parse-only factory above so the
   * guards that need no table keep needing none.
   */
  private static SessionFactory       executionSessionFactory;

  /**
   * The smallest thing able to PARSE JPQL: Hibernate bootstrapped over the two
   * cleanup entities, against an in-memory HSQLDB (both already on this module's
   * test classpath). No schema is created — a parse never touches a table.
   */
  @BeforeAll
  static void bootMinimalHibernate() {
    Configuration configuration = new Configuration();
    configuration.addAnnotatedClass(CleanupCampaignEntity.class);
    configuration.addAnnotatedClass(CleanupCampaignItemEntity.class);
    // No explicit dialect: Hibernate selects HSQLDialect from the connection and
    // warns (HHH90000025) when it is named redundantly
    configuration.setProperty("hibernate.connection.driver_class", "org.hsqldb.jdbc.JDBCDriver");
    configuration.setProperty("hibernate.connection.url", "jdbc:hsqldb:mem:cleanupDaoQueryParse");
    configuration.setProperty("hibernate.connection.username", "sa");
    configuration.setProperty("hibernate.connection.password", "");
    configuration.setProperty("hibernate.hbm2ddl.auto", "none");
    sessionFactory = configuration.buildSessionFactory();
    executionSessionFactory = executionConfiguration().buildSessionFactory();
  }

  private static Configuration executionConfiguration() {
    Configuration configuration = new Configuration();
    configuration.addAnnotatedClass(CleanupCampaignEntity.class);
    configuration.addAnnotatedClass(CleanupCampaignItemEntity.class);
    configuration.setProperty("hibernate.connection.driver_class", "org.hsqldb.jdbc.JDBCDriver");
    configuration.setProperty("hibernate.connection.url", "jdbc:hsqldb:mem:cleanupDaoQueryExecution");
    configuration.setProperty("hibernate.connection.username", "sa");
    configuration.setProperty("hibernate.connection.password", "");
    configuration.setProperty("hibernate.hbm2ddl.auto", "create");
    return configuration;
  }

  @AfterAll
  static void closeMinimalHibernate() {
    if (sessionFactory != null) {
      sessionFactory.close();
    }
    if (executionSessionFactory != null) {
      executionSessionFactory.close();
    }
  }

  @Test
  void shouldKeepReclaimableBytesFragmentAlignedWithDeleteActionName() {
    // The JPQL fragment must embed the enum name as a string literal (the
    // entity stores the action as a plain string): a CleanupAction.DELETE
    // rename must break this test, never silently break the queries
    assertTrue(normalize(CleanupCampaignItemDAO.RECLAIMABLE_BYTES).contains("i.action = '" + CleanupAction.DELETE.name() + "'"),
               "RECLAIMABLE_BYTES JPQL fragment no longer matches CleanupAction.DELETE.name()");
  }

  /**
   * The two branches of the reclaimable expression, asserted on the JPQL text
   * because nothing in this plain-JUnit suite executes it against rows: a DELETE
   * frees its content AND the whole version history it destroys, a
   * PURGE_VERSIONS frees the versions alone. Dropping the {@code + i.versionsSize}
   * addend parses perfectly and silently under-reports every DELETE candidate by
   * the entire weight of its versions — on the one figure an administrator
   * publishes a campaign on, and the reason the completion summary's reclaimed
   * total exceeded the reclaimable it had announced.
   */
  @Test
  void shouldSumTheContentAndTheWholeVersionHistoryForADeleteAction() {
    String fragment = normalize(CleanupCampaignItemDAO.RECLAIMABLE_BYTES);

    assertTrue(fragment.contains("THEN i.fileSize + i.versionsSize"),
               "A DELETE destroys the content AND the whole version history: both terms must be summed — " + fragment);
    assertTrue(fragment.contains("ELSE i.versionsSize END"),
               "A PURGE_VERSIONS leaves the content in place: the versions size alone — " + fragment);
  }

  /**
   * DOCUMENTATION of intent, not the guard: the ORDER BY form of the reclaimable
   * expression must BE the expression the aggregates sum, not a second copy of
   * it — the review list is ranked by one definition of 'reclaimable' and the
   * campaign banner totals another the moment the two texts drift, and a restated
   * CASE drifts silently (it parses, it sums, it just ranks by something else).
   * <p>
   * It also states WHY the parentheses are there. It does NOT prove they work:
   * this is a comparison of the constant against its own shape, so it would keep
   * passing if the Spring Data internal it works around
   * ({@code JpaQueryTransformerSupport#shouldPrefixWithAlias}) ever changed. The
   * guard that would fail is
   * {@link #shouldRankARealRepositoryPageByReclaimableBytesThroughTheProductionSortTranslation()},
   * which EXECUTES the whole translation — delete this test if the shape ever
   * stops being the intent, never that one.
   */
  @Test
  void shouldOrderByTheVeryExpressionTheReclaimableAggregatesSum() {
    assertEquals("(" + CleanupCampaignItemDAO.RECLAIMABLE_BYTES + ")",
                 CleanupCampaignItemDAO.RECLAIMABLE_BYTES_ORDER_BY,
                 "The ORDER BY key must be the aggregated expression itself, parenthesized — never a restatement of it");
    assertTrue(CleanupCampaignItemDAO.RECLAIMABLE_BYTES_ORDER_BY.startsWith("("),
               "Spring Data would prefix an unparenthesized sort expression with the query alias");
  }

  /**
   * THE guard of the reclaimable ordering: the production path
   * {@code REST default -> CleanupCampaignStorage#querySort -> JpaSort.unsafe ->
   * rendered SQL}, run end to end against real rows through a REAL Spring Data
   * repository proxy — built straight off the execution {@code EntityManager}
   * ({@link JpaRepositoryFactory}), no Spring context involved.
   * <p>
   * Everything else that pins this ordering pins a STRING: the sort translation is
   * asserted structurally by {@code CleanupCampaignStorageTest} (which mocks the
   * DAO, so nothing is ever executed), the ORDER BY constant is compared against
   * its own shape above, and
   * {@link #shouldRankItemsByWhatTheirOwnActionActuallyReclaims()} hand-writes the
   * expression into its own query — exercising the EXPRESSION while bypassing
   * {@code querySort} and {@code JpaSort.unsafe}. So the one step nothing executed
   * was the rendering, and that step rests on an UNPUBLISHED Spring Data internal
   * ({@code JpaQueryTransformerSupport#shouldPrefixWithAlias}: an unsafe sort
   * property gets the query alias prefixed unless it holds a '('). Were that
   * internal to change across an upgrade — spring-data-jpa moves with Spring Boot,
   * and a Boot 4.1 upgrade is on the roadmap — the string tests would all stay
   * green while {@code GET published/my-items} answered a 500 on its DEFAULT
   * ordering, for every user, during the grace period that list exists for.
   * <p>
   * Hence: the requested ordering is the one the endpoint defaults to (read from
   * the REST constants), it is translated by production's OWN
   * {@code querySort}, and the assertions are that the query PARSES AND RUNS, that
   * it ranks rows by what each one's action really frees (the finding's case: a
   * 1 MB file carrying 500 MB of history above a 100 MB file carrying none, ties
   * broken on the id), and that the rendered {@code ORDER BY} carries the CASE
   * with NO alias prefix and still ends on the {@code id} tiebreaker.
   */
  @Test
  void shouldRankARealRepositoryPageByReclaimableBytesThroughTheProductionSortTranslation() throws ReflectiveOperationException {
    long campaignId = 9003L;
    List<String> executedStatements = new ArrayList<>();
    StatementInspector inspector = sql -> {
      executedStatements.add(sql);
      return sql;
    };
    // A session of the EXISTING execution factory (no third bootstrap), opened
    // with its own statement inspector so the rendered ORDER BY can be read back
    try (Session session = executionSessionFactory.withOptions().statementInspector(inspector).openSession()) {
      long smallFileHugeHistory = persist(session, campaignId, "/a-small-huge-history", CleanupAction.DELETE, MB, 500 * MB);
      long bigFileNoHistory = persist(session, campaignId, "/b-big-no-history", CleanupAction.DELETE, 100 * MB, 0);
      long emptyFileTiedHistory = persist(session, campaignId, "/c-tied", CleanupAction.DELETE, 0, 100 * MB);
      long hugeFilePurgedVersions = persist(session, campaignId, "/d-purge", CleanupAction.PURGE_VERSIONS, 900 * MB, 2 * MB);

      CleanupCampaignItemDAO dao = new JpaRepositoryFactory(session).getRepository(CleanupCampaignItemDAO.class);
      Pageable pageable = PageRequest.of(0, 10, storageQuerySort(myItemsDefaultSort()));
      executedStatements.clear();

      Page<CleanupCampaignItemEntity> page;
      try {
        // The very call getItemsByOwners() makes for the single-chunk case
        page = dao.findByOwnersAndSearch(campaignId, List.of(OWNER_IDENTITY_ID), null, pageable);
        page.getContent();
      } catch (RuntimeException e) {
        throw new AssertionError("The default ordering of GET published/my-items no longer renders into an executable"
            + " query. Either CleanupCampaignStorage#querySort stopped translating the reclaimable key into the JPQL"
            + " expression, or CleanupCampaignItemDAO.RECLAIMABLE_BYTES_ORDER_BY lost its PARENTHESES — Spring Data"
            + " prefixes an unsafe sort property with the query alias unless it holds a '(', so the CASE would render as"
            + " 'i.CASE WHEN ...'. If spring-data-jpa itself changed, replace the workaround by whatever it now needs;"
            + " do NOT relax this test: " + e.getMessage(), e);
      }

      assertEquals(List.of(smallFileHugeHistory, bigFileNoHistory, emptyFileTiedHistory, hugeFilePurgedVersions),
                   page.getContent().stream().map(CleanupCampaignItemEntity::getId).toList(),
                   "The repository page must rank rows by what each action frees, ties broken on the id");

      String renderedOrderBy = renderedOrderBy(executedStatements);
      assertTrue(renderedOrderBy.contains("case when"),
                 "The computed reclaimable key must reach the database's ORDER BY, not be dropped or replaced by a column: "
                     + renderedOrderBy);
      assertFalse(renderedOrderBy.matches(".*\\w+\\.case\\b.*"),
                  "An alias prefix leaked into the rendered ORDER BY — the parenthesization no longer holds it back: "
                      + renderedOrderBy);
      assertTrue(renderedOrderBy.matches(".*\\.id( asc)?$"),
                 "The id tiebreaker must stay the LAST rendered order key: without it the ordering is not total, and offset"
                     + " paging over the block of ties repeats rows while dropping others — " + renderedOrderBy);
    }
  }

  /**
   * The finding's exact case, executed on real rows instead of asserted on query
   * text: a 1 MB file carrying 500 MB of version history must rank ABOVE a 100 MB
   * file carrying none, because deleting it frees 501 MB. Ordering on
   * {@code fileSize} — what this list did before — puts it second to last.
   * <p>
   * A PURGE_VERSIONS row is in the fixture on purpose, and it is the LARGEST file
   * of the four: it reclaims its versions alone (2 MB), so it must rank last. The
   * two rows tied at 100 MB pin the {@code id} tiebreaker, without which offset
   * paging over a block of ties repeats rows and drops others.
   */
  @Test
  void shouldRankItemsByWhatTheirOwnActionActuallyReclaims() {
    long campaignId = 9001L;
    try (EntityManager entityManager = executionSessionFactory.createEntityManager()) {
      // Reclaimable: 501 MB, 100 MB, 100 MB (tie), 2 MB — file sizes: 1, 100, 0, 900
      long smallFileHugeHistory = persist(entityManager, campaignId, "/a-small-huge-history", CleanupAction.DELETE, MB, 500 * MB);
      long bigFileNoHistory = persist(entityManager, campaignId, "/b-big-no-history", CleanupAction.DELETE, 100 * MB, 0);
      long emptyFileTiedHistory = persist(entityManager, campaignId, "/c-tied", CleanupAction.DELETE, 0, 100 * MB);
      long hugeFilePurgedVersions =
                                  persist(entityManager, campaignId, "/d-purge", CleanupAction.PURGE_VERSIONS, 900 * MB, 2 * MB);

      assertEquals(List.of(smallFileHugeHistory, bigFileNoHistory, emptyFileTiedHistory, hugeFilePurgedVersions),
                   reclaimableOrderedIds(entityManager, campaignId),
                   "Rows must be ranked by what each action frees, ties broken on the id");
    }
  }

  /**
   * Finding B, from the aggregate side: a row whose sizes are ZERO must still be
   * counted in — and contribute 0 to — the reclaimable sum. It is the readable
   * half of what the NOT NULL constraint of changeset -11 protects: were such a
   * column NULL, {@code NULL + n} would be NULL, {@code SUM} would SKIP the row
   * and the campaign would under-report by that WHOLE row, not by its versions.
   */
  @Test
  void shouldStillSumEveryRowWhenOneCarriesZeroSizes() {
    long campaignId = 9002L;
    try (EntityManager entityManager = executionSessionFactory.createEntityManager()) {
      persist(entityManager, campaignId, "/zero", CleanupAction.DELETE, 0, 0);
      persist(entityManager, campaignId, "/delete", CleanupAction.DELETE, MB, 2 * MB);
      persist(entityManager, campaignId, "/purge", CleanupAction.PURGE_VERSIONS, 900 * MB, 4 * MB);

      Object[] row = (Object[]) entityManager.createQuery("SELECT COUNT(i), COALESCE(SUM("
          + CleanupCampaignItemDAO.RECLAIMABLE_BYTES + "), 0) FROM CleanupCampaignItem i WHERE i.campaignId = :campaignId")
                                            .setParameter("campaignId", campaignId)
                                            .getSingleResult();

      assertEquals(3L, ((Number) row[0]).longValue(), "The zero-sized row must stay in the population");
      assertEquals(3 * MB + 4 * MB, ((Number) row[1]).longValue(), "3 MB from the delete, 4 MB from the purge, 0 from the zero row");
    }
  }

  /**
   * The constraint itself, read from the changelog: the two columns the
   * reclaimable expression ADDS must be NOT NULL with a zero backfill, added by a
   * new changeset rather than by amending the table creation (-2), which a
   * deployed platform has already applied. {@code columnDataType} is asserted
   * because several databases require it to alter a column's nullability.
   */
  @Test
  void shouldConstrainTheSummedSizeColumnsNotNullInTheChangelog() throws Exception {
    NodeList constraints = changelog().getElementsByTagName("addNotNullConstraint");
    Map<String, Element> byColumn = new java.util.HashMap<>();
    for (int index = 0; index < constraints.getLength(); index++) {
      Element constraint = (Element) constraints.item(index);
      if (ITEM_TABLE.equals(constraint.getAttribute("tableName"))) {
        byColumn.put(constraint.getAttribute("columnName"), constraint);
      }
    }
    for (String column : List.of("FILE_SIZE", "VERSIONS_SIZE")) {
      Element constraint = byColumn.get(column);
      assertNotNull(constraint,
                    column + " feeds the reclaimable arithmetic: a NULL there vanishes from every SUM, so it must be NOT NULL");
      assertEquals("0", constraint.getAttribute("defaultNullValue"), column + ": existing NULLs must be backfilled with 0");
      assertFalse(constraint.getAttribute("columnDataType").isBlank(),
                  column + ": the column data type is required by some databases to alter nullability");
    }
  }

  /**
   * The guard that actually replaces "nothing parses these queries": a renamed
   * entity, an unknown field path or a syntax error in ANY {@code @Query} of the
   * cleanup DAOs fails here, at test time, instead of failing the Spring Data
   * repository bootstrap of a deployed platform.
   */
  @Test
  void shouldParseEveryQueryOfTheCleanupDaos() {
    List<Method> queryMethods = queryMethods();
    assertFalse(queryMethods.isEmpty(), "No @Query method found: the reflective guard would be vacuous");
    try (EntityManager entityManager = sessionFactory.createEntityManager()) {
      for (Method method : queryMethods) {
        Query queryAnnotation = method.getAnnotation(Query.class);
        assertFalse(queryAnnotation.nativeQuery(),
                    method.getName() + " is a native query: Hibernate cannot parse it, add a dedicated guard");
        for (String jpql : jpqlOf(queryAnnotation)) {
          try {
            entityManager.createQuery(jpql);
          } catch (RuntimeException e) {
            fail("The JPQL of " + method.getName() + " does not parse against the cleanup entities: " + jpql + " -> "
                + e.getMessage());
          }
        }
      }
    }
  }

  /**
   * The mutant a parse cannot catch: renaming a {@code @Param} while the query
   * keeps the old {@code :token} (or the reverse) parses perfectly and only
   * blows up when Spring Data binds the arguments at repository bootstrap. The
   * comparison is a SET EQUALITY, so an unused {@code @Param} and an unbound
   * {@code :token} both fail — and the parameter names come from Hibernate's own
   * parse of the query, not from a regex over its text.
   */
  @Test
  void shouldBindEveryNamedParameterOfEveryQueryToAMatchingParam() {
    try (EntityManager entityManager = sessionFactory.createEntityManager()) {
      for (Method method : queryMethods()) {
        Set<String> queryParameters = new LinkedHashSet<>();
        for (String jpql : jpqlOf(method.getAnnotation(Query.class))) {
          entityManager.createQuery(jpql)
                       .getParameters()
                       .stream()
                       .map(Parameter::getName)
                       .filter(Objects::nonNull)
                       .forEach(queryParameters::add);
        }
        Set<String> annotatedParameters = Arrays.stream(method.getParameters())
                                                .map(parameter -> parameter.getAnnotation(Param.class))
                                                .filter(Objects::nonNull)
                                                .map(Param::value)
                                                .collect(Collectors.toCollection(LinkedHashSet::new));
        assertEquals(queryParameters.stream().sorted().toList(),
                     annotatedParameters.stream().sorted().toList(),
                     method.getName() + ": every :token of the query must have a matching @Param and vice versa");
      }
    }
  }

  /**
   * The path-search contract is SPLIT across two layers: the Storage builds the
   * pattern (trimmed, lower-cased, '%'/'_'/'|' escaped with '|' — pinned by
   * {@code CleanupCampaignStorageTest}) and the query here supplies the matching
   * halves. A parse can't see any of it (all three halves are semantic, not
   * syntactic), so each is asserted on the WHITESPACE-NORMALIZED query, so a
   * harmless reformat of the text block never fails the test:
   * <ul>
   * <li>{@code LOWER(i.path)} — without it the search becomes case SENSITIVE,
   * while the pattern arrives lower-cased, i.e. 'Invoice' stops matching</li>
   * <li>{@code ESCAPE '|'} — without it the escaping becomes double-escaping: a
   * term holding '_' or '%' matches nothing at all</li>
   * <li>{@code :searchPattern IS NULL} — the null-tolerance that makes a blank
   * term mean NO filter instead of matching nothing</li>
   * </ul>
   */
  @Test
  void shouldKeepTheSearchClauseHalvesTheStorageEscapingRelieson() throws NoSuchMethodException {
    for (String jpql : List.of(queryOf("findByFilters",
                                       long.class,
                                       Long.class,
                                       String.class,
                                       String.class,
                                       Long.class,
                                       String.class,
                                       Pageable.class),
                               queryOf("findByOwnersAndSearch", long.class, List.class, String.class, Pageable.class))) {
      assertTrue(jpql.contains("LOWER(i.path)"), "The path search must stay case-insensitive: " + jpql);
      assertTrue(jpql.contains("ESCAPE '|'"), "The LIKE must declare the '|' escape character the Storage escapes with: " + jpql);
      assertTrue(jpql.contains(":searchPattern IS NULL"), "A null pattern must mean NO filter: " + jpql);
    }
  }

  /**
   * The other half of these interfaces: the DERIVED-name methods carry no
   * {@code @Query}, so their property expressions are resolved for the first
   * time at Spring Data repository bootstrap — the very failure mode the parse
   * above front-runs. Spring Data's own {@link PartTree} resolves them here
   * against the entity, so a method named after a property that does not exist
   * (or a typo in one) fails at test time instead of breaking the deployment.
   */
  @Test
  void shouldResolveEveryDerivedQueryMethodAgainstItsEntity() {
    for (Map.Entry<Class<?>, Class<?>> dao : CLEANUP_DAO_ENTITIES.entrySet()) {
      List<Method> derivedMethods = derivedQueryMethods(dao.getKey());
      assertFalse(derivedMethods.isEmpty(),
                  "No derived-name method found on " + dao.getKey().getSimpleName() + ": the guard would be vacuous");
      for (Method method : derivedMethods) {
        try {
          new PartTree(method.getName(), dao.getValue());
        } catch (RuntimeException e) {
          fail(dao.getKey().getSimpleName() + "." + method.getName() + " does not resolve against "
              + dao.getValue().getSimpleName() + ": " + e.getMessage());
        }
      }
    }
  }

  /**
   * The sortable-field allowlist the REST layer accepts is a set of STRINGS
   * compared against nothing: a typo there passes every test and answers a
   * runtime 500 when a client sorts by that column. Resolved here against the
   * entity metamodel, together with the tiebreaker the whole pagination
   * stability rests on.
   */
  @Test
  void shouldKeepTheRestSortableFieldsResolvableOnTheItemEntity() throws ReflectiveOperationException {
    EntityType<CleanupCampaignItemEntity> itemEntity = sessionFactory.getMetamodel().entity(CleanupCampaignItemEntity.class);
    assertTrue(restSortableItemFields().contains(CleanupConstants.RECLAIMABLE_SORT_KEY),
               "The reclaimable ordering must stay an ASKABLE sort key, not only the default of one endpoint");
    for (String field : restSortableItemFields()) {
      if (CleanupConstants.RECLAIMABLE_SORT_KEY.equals(field)) {
        // The ONE key that is deliberately NOT a column: it names the reclaimable
        // CASE expression, which the Storage turns into a query-level ORDER BY
        // (pinned by CleanupCampaignStorageTest). Resolving it here would mean it
        // had silently become a plain field again — and a plain field cannot rank
        // a DELETE row by content PLUS versions
        continue;
      }
      try {
        itemEntity.getAttribute(field);
      } catch (IllegalArgumentException e) {
        fail("CleanupCampaignRest.SORTABLE_ITEM_FIELDS accepts '" + field + "', which is not an attribute of "
            + CleanupCampaignItemEntity.class.getSimpleName());
      }
    }
    String tiebreaker = restConstant("TIEBREAKER_FIELD", String.class);
    assertNotNull(itemEntity.getAttribute(tiebreaker), "The pagination tiebreaker must be an attribute of the item entity");
    assertTrue(itemEntity.getId(Long.class).getName().equals(tiebreaker),
               "The tiebreaker must stay the primary key: it is the only column the schema guarantees unique per campaign");
  }

  /**
   * Every {@code @Query}-annotated method of both cleanup repositories, inherited
   * {@link org.springframework.data.jpa.repository.JpaRepository} methods
   * included (they carry none, so they simply drop out).
   */
  /**
   * Methods DECLARED by the repository (inherited {@code JpaRepository} ones
   * drop out — Spring Data resolves those itself) that carry no
   * {@code @Query}, i.e. the derived-name ones.
   */
  private List<Method> derivedQueryMethods(Class<?> dao) {
    List<Method> methods = new ArrayList<>();
    for (Method method : dao.getDeclaredMethods()) {
      if (method.getAnnotation(Query.class) == null) {
        methods.add(method);
      }
    }
    return methods;
  }

  /**
   * The ordering {@code GET published/my-items} opens on, rebuilt from the REST
   * layer's OWN constants: the reclaimable key DESCENDING (the endpoint's default
   * direction) made total by the {@code id} tiebreaker it appends to every
   * ordering. Renaming either constant, or pointing the default at another field,
   * fails the guard below instead of silently moving it off the path under test.
   */
  private static Sort myItemsDefaultSort() throws ReflectiveOperationException {
    String defaultField = restConstant("DEFAULT_MY_ITEMS_SORT", String.class);
    String tiebreaker = restConstant("TIEBREAKER_FIELD", String.class);
    assertEquals(CleanupConstants.RECLAIMABLE_SORT_KEY,
                 defaultField,
                 "GET published/my-items must keep opening on the reclaimable ranking");
    return Sort.by(Sort.Direction.DESC, defaultField).and(Sort.by(Sort.Direction.ASC, tiebreaker));
  }

  /**
   * PRODUCTION's own translation of a requested ordering into the one the
   * database can apply, reached reflectively: {@code CleanupCampaignStorage#querySort}
   * is package visible in the storage package — a Storage internal this guard
   * deliberately does not widen further — while the harness able to EXECUTE what
   * it produces lives here. Reconstructing a {@code JpaSort.unsafe} in the test
   * instead would cover the test's idea of the translation, not the one the
   * endpoint performs.
   */
  private static Sort storageQuerySort(Sort requestedSort) throws ReflectiveOperationException {
    Method querySort = CleanupCampaignStorage.class.getDeclaredMethod("querySort", Sort.class);
    querySort.setAccessible(true); // NOSONAR test-only reach into a package-visible Storage internal
    return (Sort) querySort.invoke(null, requestedSort);
  }

  /**
   * The {@code ORDER BY} keys of the paged SELECT the repository actually sent to
   * the database, lower-cased. Read from the statements the session's inspector
   * captured, skipping the COUNT query a {@link Page} also issues (it carries no
   * ordering), and with the dialect's row-limiting tail cut off so the returned
   * clause holds nothing but the keys — that is what lets the assertions pin the
   * LAST of them.
   */
  private static String renderedOrderBy(List<String> executedStatements) {
    String orderBy = executedStatements.stream()
                                       .map(statement -> statement.toLowerCase())
                                       .filter(statement -> statement.contains("order by"))
                                       .findFirst()
                                       .orElse(null);
    assertNotNull(orderBy,
                  "No ordered SELECT reached the database, so the ordering under test was never rendered: "
                      + executedStatements);
    String keys = orderBy.substring(orderBy.lastIndexOf("order by"));
    for (String limitClause : List.of(" fetch ", " limit ", " offset ")) {
      int limitIndex = keys.indexOf(limitClause);
      if (limitIndex > 0) {
        keys = keys.substring(0, limitIndex);
      }
    }
    return keys.trim();
  }

  /**
   * The REST layer's sortable-field allowlist, read reflectively: the allowlist
   * lives there (it is a REST contract), while the metamodel able to validate
   * it lives here.
   */
  @SuppressWarnings("unchecked")
  private Set<String> restSortableItemFields() throws ReflectiveOperationException {
    Field field = CleanupCampaignRest.class.getDeclaredField("SORTABLE_ITEM_FIELDS");
    field.setAccessible(true); // NOSONAR test-only read of a REST contract constant
    return (Set<String>) field.get(null);
  }

  /**
   * Reads a private constant of the REST layer, same rationale as above.
   */
  private static <T> T restConstant(String name, Class<T> type) throws ReflectiveOperationException {
    Field field = CleanupCampaignRest.class.getDeclaredField(name);
    field.setAccessible(true); // NOSONAR test-only read of a REST contract constant
    return type.cast(field.get(null));
  }

  private List<Method> queryMethods() {
    List<Method> methods = new ArrayList<>();
    for (Class<?> dao : CLEANUP_DAOS) {
      for (Method method : dao.getMethods()) {
        if (method.getAnnotation(Query.class) != null) {
          methods.add(method);
        }
      }
    }
    return methods;
  }

  /**
   * The JPQL statements a {@code @Query} carries: its value, plus its explicit
   * count query when it declares one — a broken count query fails a paginated
   * endpoint just as hard.
   */
  private List<String> jpqlOf(Query query) {
    List<String> statements = new ArrayList<>();
    statements.add(query.value());
    if (!query.countQuery().isBlank()) {
      statements.add(query.countQuery());
    }
    return statements;
  }

  private String queryOf(String methodName, Class<?>... parameterTypes) throws NoSuchMethodException {
    Query query = CleanupCampaignItemDAO.class.getMethod(methodName, parameterTypes).getAnnotation(Query.class);
    assertNotNull(query, methodName + " must stay annotated @Query");
    return normalize(query.value());
  }

  /**
   * Persists one candidate row and returns its generated id, which the ordering
   * assertions use as the tiebreaker: rows are inserted in a deliberate order, so
   * a tie must resolve to the one inserted FIRST.
   */
  private long persist(EntityManager entityManager,
                       long campaignId,
                       String path,
                       CleanupAction action,
                       long fileSize,
                       long versionsSize) {
    CleanupCampaignItemEntity item = new CleanupCampaignItemEntity();
    item.setCampaignId(campaignId);
    item.setNodeUuid("uuid" + campaignId + path);
    item.setPath(path);
    item.setOwnerIdentityId(OWNER_IDENTITY_ID);
    item.setFileSize(fileSize);
    item.setVersionsSize(versionsSize);
    item.setAction(action.name());
    item.setState(CleanupItemState.CANDIDATE.name());
    entityManager.getTransaction().begin();
    entityManager.persist(item);
    entityManager.getTransaction().commit();
    return item.getId();
  }

  /**
   * The item ids of a campaign in the ORDER the database applies for the
   * reclaimable ranking: the production ORDER BY key, followed by the {@code id}
   * tiebreaker the REST layer appends to every non-unique ordering.
   */
  private List<Long> reclaimableOrderedIds(EntityManager entityManager, long campaignId) {
    return entityManager.createQuery("SELECT i FROM CleanupCampaignItem i WHERE i.campaignId = :campaignId ORDER BY "
        + CleanupCampaignItemDAO.RECLAIMABLE_BYTES_ORDER_BY + " DESC, i.id ASC", CleanupCampaignItemEntity.class)
                        .setParameter("campaignId", campaignId)
                        .getResultList()
                        .stream()
                        .map(CleanupCampaignItemEntity::getId)
                        .toList();
  }

  /** The cleanup changelog, parsed from the classpath resource the addon ships. */
  private Document changelog() throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    try (java.io.InputStream changelog = getClass().getClassLoader().getResourceAsStream(CHANGELOG_RESOURCE)) {
      assertNotNull(changelog, CHANGELOG_RESOURCE + " must stay on the classpath");
      return factory.newDocumentBuilder().parse(changelog);
    }
  }

  /**
   * Collapses runs of whitespace (the text-block queries span several indented
   * lines) so the substring assertions above pin the CLAUSES, not the formatting.
   */
  private static String normalize(String jpql) {
    return jpql.replaceAll("\\s+", " ").trim();
  }

}
