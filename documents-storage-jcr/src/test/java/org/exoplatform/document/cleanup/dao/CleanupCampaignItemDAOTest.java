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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.exoplatform.document.cleanup.constant.CleanupAction;
import org.exoplatform.document.cleanup.entity.CleanupCampaignEntity;
import org.exoplatform.document.cleanup.entity.CleanupCampaignItemEntity;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Parameter;

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
 * NOT covered, by construction: anything that needs the real database — dialect
 * specifics, collation-dependent {@code LIKE}/{@code LOWER} behaviour, index
 * usage and the actual rows a query returns.
 */
class CleanupCampaignItemDAOTest {

  /**
   * Both cleanup repositories: the guard must cover EVERY {@code @Query} of the
   * package, not only the two search ones, and must start covering a query added
   * to {@link CleanupCampaignDAO} later without anybody remembering this test.
   */
  private static final List<Class<?>> CLEANUP_DAOS = List.of(CleanupCampaignItemDAO.class, CleanupCampaignDAO.class);

  private static SessionFactory       sessionFactory;

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
  }

  @AfterAll
  static void closeMinimalHibernate() {
    if (sessionFactory != null) {
      sessionFactory.close();
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
   * Every {@code @Query}-annotated method of both cleanup repositories, inherited
   * {@link org.springframework.data.jpa.repository.JpaRepository} methods
   * included (they carry none, so they simply drop out).
   */
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
   * Collapses runs of whitespace (the text-block queries span several indented
   * lines) so the substring assertions above pin the CLAUSES, not the formatting.
   */
  private static String normalize(String jpql) {
    return jpql.replaceAll("\\s+", " ").trim();
  }

}
