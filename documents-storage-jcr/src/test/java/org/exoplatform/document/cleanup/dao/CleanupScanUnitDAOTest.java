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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import org.exoplatform.document.cleanup.constant.CleanupScanUnitState;
import org.exoplatform.document.cleanup.entity.CleanupCampaignEntity;
import org.exoplatform.document.cleanup.entity.CleanupScanUnitEntity;

import jakarta.persistence.EntityManager;

/**
 * Guards the scan-unit work-list query on ROWS, and not on a parse: it is the
 * only place in this module's plain-JUnit suite where
 * {@code findUnitsToProcess} and {@code countSettledFailures} run against a real
 * database, over rows chosen to hit every state/attempt combination a resume can
 * meet.
 * <p>
 * Why a row-level guard and not a Storage unit test: the bug this pins is
 * PREDICATE semantics. The Storage's DAO is a mock in
 * {@code CleanupScanUnitStorageTest}, so a work-list query filtering on
 * {@code state <> DONE} alone — or filtering state-BLINDLY on the attempt count —
 * returns exactly what the mock was told either way, and the whole class of
 * failures is invisible there. Here it is a returned row or a missing one.
 * <p>
 * The two queries are read REFLECTIVELY from the repository's own {@code @Query}
 * annotations and executed verbatim, so this test can never drift into asserting
 * a copy of the production JPQL: renaming a {@code :token} or dropping a clause
 * fails here.
 * <p>
 * What it pins, in order of importance:
 * <ol>
 * <li>a settled-failed unit (FAILED, every attempt spent) is OUT of the work
 * list — nothing re-claims it, so its ATTEMPT_COUNT can never grow past the
 * bound and an unreadable subtree is never re-walked</li>
 * <li>a unit RUNNING at or past the bound is STILL in the work list: the
 * exclusion is state-aware, and a state-blind {@code attemptCount <
 * maxAttemptCount} would strand a thrice-interrupted unit — neither DONE nor
 * settled-failed, and no longer walked, i.e. a dry-run held open forever</li>
 * <li>the two rules AGREE: every unit of a campaign is either in the work list,
 * DONE, or counted as a settled failure — never in none of them (a unit nothing
 * walks and nothing settles blocks completion for good) and never in two</li>
 * </ol>
 * <p>
 * NOT covered, by construction: dialect specifics and index usage. The parse and
 * {@code @Param} guards of the other cleanup repositories live in
 * {@code CleanupCampaignItemDAOTest}; an execution subsumes both for the two
 * queries below.
 */
class CleanupScanUnitDAOTest {

  private static final long          CAMPAIGN_ID       = 7L;

  private static final long          OTHER_CAMPAIGN_ID = 8L;

  /** The bound, as {@code CleanupScanService#MAX_SCAN_UNIT_ATTEMPTS} sets it. */
  private static final long          MAX_ATTEMPTS      = 3L;

  private static SessionFactory      sessionFactory;

  /**
   * Hibernate over the scan-unit entity alone, against an in-memory HSQLDB
   * (already on this module's test classpath), with a schema this time: the
   * queries are EXECUTED here, not merely parsed.
   */
  @BeforeAll
  static void bootMinimalHibernate() {
    Configuration configuration = new Configuration();
    configuration.addAnnotatedClass(CleanupScanUnitEntity.class);
    // The campaign entity too, for the orphan query alone: it is the one query
    // here that spans both tables, and a sweep that deleted a LIVE campaign's
    // units would be far worse than the leftovers it exists to collect
    configuration.addAnnotatedClass(CleanupCampaignEntity.class);
    configuration.setProperty("hibernate.connection.driver_class", "org.hsqldb.jdbc.JDBCDriver");
    configuration.setProperty("hibernate.connection.url", "jdbc:hsqldb:mem:cleanupScanUnitDaoRows");
    configuration.setProperty("hibernate.connection.username", "sa");
    configuration.setProperty("hibernate.connection.password", "");
    configuration.setProperty("hibernate.hbm2ddl.auto", "create");
    sessionFactory = configuration.buildSessionFactory();
    insertUnits();
  }

  @AfterAll
  static void closeMinimalHibernate() {
    if (sessionFactory != null) {
      sessionFactory.close();
    }
  }

  /**
   * One row per state/attempt combination a resume can meet, inserted in the
   * order the work list must come back in (the query orders by id, and the
   * sequence hands them out ascending).
   */
  private static void insertUnits() {
    try (EntityManager entityManager = sessionFactory.createEntityManager()) {
      entityManager.getTransaction().begin();
      // In the work list
      entityManager.persist(unit(CAMPAIGN_ID, "/Trash", CleanupScanUnitState.PENDING, 0));
      // Interrupted AT the bound: still not settled, so still walked
      entityManager.persist(unit(CAMPAIGN_ID, "/Users/a___", CleanupScanUnitState.RUNNING, MAX_ATTEMPTS));
      // Interrupted PAST the bound, same reason
      entityManager.persist(unit(CAMPAIGN_ID, "/Users/b___", CleanupScanUnitState.RUNNING, MAX_ATTEMPTS + 2));
      // Failed with ONE attempt left: a transient failure must keep healing
      entityManager.persist(unit(CAMPAIGN_ID, "/Users/c___", CleanupScanUnitState.FAILED, MAX_ATTEMPTS - 1));
      // Out of the work list
      entityManager.persist(unit(CAMPAIGN_ID, "/Users/d___", CleanupScanUnitState.FAILED, MAX_ATTEMPTS));
      entityManager.persist(unit(CAMPAIGN_ID, "/Users/e___", CleanupScanUnitState.FAILED, MAX_ATTEMPTS + 4));
      entityManager.persist(unit(CAMPAIGN_ID, "/Groups/spaces/marketing", CleanupScanUnitState.DONE, 1));
      entityManager.persist(unit(CAMPAIGN_ID, "/Groups/spaces/sales", CleanupScanUnitState.DONE, MAX_ATTEMPTS));
      // Another campaign's unit: the work list is per campaign
      entityManager.persist(unit(OTHER_CAMPAIGN_ID, "/Trash", CleanupScanUnitState.PENDING, 0));
      entityManager.getTransaction().commit();
    }
  }

  @Test
  void theWorkListHoldsEverythingButDoneAndTheSettledFailures() {
    List<CleanupScanUnitEntity> units = unitsToProcess(MAX_ATTEMPTS);

    // A settled-failed subtree is GONE from the work list: the coordinator claims
    // every unit this query returns, so leaving it here is what made ATTEMPT_COUNT
    // climb to 4, 5, ... and re-walked a subtree already proved unreadable on
    // every single watchdog tick
    assertEquals(List.of("/Trash", "/Users/a___", "/Users/b___", "/Users/c___"),
                 units.stream().map(CleanupScanUnitEntity::getUnitPath).toList(),
                 "The work list must hold the PENDING, the RUNNING (whatever their attempts) and the still-retryable FAILED"
                     + " units of THIS campaign, oldest id first");
  }

  @Test
  void aRunningUnitPastTheBoundIsStillWalkedSoItCannotStrandTheCampaign() {
    List<CleanupScanUnitEntity> units = unitsToProcess(MAX_ATTEMPTS);

    // THE trap of this filter: a bare 'attemptCount < maxAttemptCount' drops a
    // thrice-interrupted RUNNING unit, which is neither DONE nor settled-FAILED.
    // Nothing would walk it and nothing would settle it, so completeCampaign
    // would hold the dry-run open forever over a unit no run can finish
    List<String> runningPaths = units.stream()
                                     .filter(unit -> CleanupScanUnitState.RUNNING.name().equals(unit.getState()))
                                     .map(CleanupScanUnitEntity::getUnitPath)
                                     .toList();
    assertEquals(List.of("/Users/a___", "/Users/b___"),
                 runningPaths,
                 "A RUNNING unit at or past the bound must stay in the work list: the exclusion is state-AWARE");
    assertTrue(units.stream().anyMatch(unit -> unit.getAttemptCount() > MAX_ATTEMPTS),
               "The dataset must really hold a unit past the bound, or the assertion above is vacuous");
  }

  @Test
  void theBoundIsInclusiveOnBothQueries() {
    // Exactly AT the bound is SETTLED: 'attemptCount > max' instead of '>=' would
    // grant a fourth walk to every unreadable subtree, and the promise is THREE
    assertTrue(unitsToProcess(MAX_ATTEMPTS).stream().noneMatch(unit -> "/Users/d___".equals(unit.getUnitPath())),
               "A FAILED unit that spent exactly the allowed attempts is settled: it must not be walked again");
    assertEquals(2, settledFailures(MAX_ATTEMPTS), "The units at and past the bound are the settled ones");
    // One attempt LEFT is not settled, on either side of the contract: still
    // walked here, still counted as unsettled there — that is what heals a
    // transient JCR failure
    assertTrue(unitsToProcess(MAX_ATTEMPTS).stream().anyMatch(unit -> "/Users/c___".equals(unit.getUnitPath())));
    assertEquals(1,
                 settledFailures(MAX_ATTEMPTS + 1),
                 "Raising the bound un-settles the unit that had only just settled: the count follows the SAME >= comparison"
                     + " as the work list, so the two can never disagree on which unit is settled");
    assertEquals(6,
                 unitsToProcess(MAX_ATTEMPTS + 5).size(),
                 "A bound nothing reaches settles nothing: every non-DONE unit of the campaign is back in the work list");
  }

  @Test
  void theWorkListAndTheSettledCountPartitionTheUnitsOfACampaign() {
    List<CleanupScanUnitEntity> all = allUnits(CAMPAIGN_ID);
    List<CleanupScanUnitEntity> units = unitsToProcess(MAX_ATTEMPTS);
    long doneCount = all.stream().filter(unit -> CleanupScanUnitState.DONE.name().equals(unit.getState())).count();

    // The completion rule (every unit DONE or settled-failed) and the work-list
    // rule (everything but DONE and the settled failures) must be exact
    // complements. A unit falling through BOTH is a dry-run nothing can ever
    // finish; a unit in both is a settled subtree walked forever
    assertEquals(all.size(),
                 units.size() + doneCount + settledFailures(MAX_ATTEMPTS),
                 "Every unit must be either in the work list, DONE, or counted as a settled failure — exactly once");
    assertTrue(units.stream().noneMatch(unit -> CleanupScanUnitState.DONE.name().equals(unit.getState())),
               "A DONE unit is finished: it must never be handed back");
    assertTrue(units.stream().allMatch(unit -> unit.getCampaignId() == CAMPAIGN_ID),
               "The work list of a campaign must never leak another campaign's unit");
  }

  @Test
  void theStateBreakdownCountsEveryStateOfTHISCampaignInOneGroupedQuery() {
    Map<String, Long> counts = new HashMap<>();
    for (Object[] row : groupedStateCounts()) {
      counts.put((String) row[0], ((Number) row[1]).longValue());
    }

    // The dataset above, folded: the grouped query is what the console reads
    // instead of one count query per state, and its SUM is the unit total — so a
    // state the query forgot would silently shrink the denominator too
    assertEquals(Map.of(CleanupScanUnitState.PENDING.name(), 1L,
                        CleanupScanUnitState.RUNNING.name(), 2L,
                        CleanupScanUnitState.FAILED.name(), 3L,
                        CleanupScanUnitState.DONE.name(), 2L),
                 counts,
                 "Every state of this campaign must come back grouped, and no other campaign's unit with it");
    assertEquals(8L, counts.values().stream().mapToLong(Long::longValue).sum());
  }

  @Test
  void theDeepestAttemptIsTheONEFigureThatShowsAScanGoingRoundInCircles() {
    // /Users/e___ was walked 7 times. A checkpoint standing still while THIS
    // climbs is the difference between a scan progressing and one re-walking, and
    // it is the only number on the console that can say so
    assertEquals(7L, maxAttemptCount(CAMPAIGN_ID));
    // COALESCE, not a null: a campaign whose units were never planned must read 0
    // rather than blow up the panel that asks
    assertEquals(0L, maxAttemptCount(4242L), "MAX over no row must answer 0, not null");
  }

  @Test
  void theInFlightFetchReturnsTheRunningUnitsOldestFirst() {
    List<CleanupScanUnitEntity> running = unitsInState(CleanupScanUnitState.RUNNING);

    // RUNNING does NOT mean a reader is alive: both of these were left behind by
    // an interrupted run, which is precisely what the console must be able to show
    assertEquals(List.of("/Users/a___", "/Users/b___"),
                 running.stream().map(CleanupScanUnitEntity::getUnitPath).toList());
    assertTrue(running.stream().allMatch(unit -> unit.getCampaignId() == CAMPAIGN_ID));
  }

  /** Executes the repository's own grouped state-count JPQL, likewise. */
  private List<Object[]> groupedStateCounts() {
    try (EntityManager entityManager = sessionFactory.createEntityManager()) {
      return entityManager.createQuery(jpqlOf("countByState"), Object[].class)
                          .setParameter("campaignId", CAMPAIGN_ID)
                          .getResultList();
    }
  }

  /** Executes the repository's own deepest-attempt JPQL, likewise. */
  private long maxAttemptCount(long campaignId) {
    try (EntityManager entityManager = sessionFactory.createEntityManager()) {
      return entityManager.createQuery(jpqlOf("maxAttemptCount"), Long.class)
                          .setParameter("campaignId", campaignId)
                          .getSingleResult();
    }
  }

  /** Executes the repository's own by-state JPQL, likewise. */
  private List<CleanupScanUnitEntity> unitsInState(CleanupScanUnitState state) {
    try (EntityManager entityManager = sessionFactory.createEntityManager()) {
      return entityManager.createQuery(jpqlOf("findByState"), CleanupScanUnitEntity.class)
                          .setParameter("campaignId", CAMPAIGN_ID)
                          .setParameter("state", state.name())
                          .getResultList();
    }
  }

  /** Executes the repository's own work-list JPQL, read from its annotation. */
  private List<CleanupScanUnitEntity> unitsToProcess(long maxAttemptCount) {
    try (EntityManager entityManager = sessionFactory.createEntityManager()) {
      return entityManager.createQuery(jpqlOf("findUnitsToProcess"), CleanupScanUnitEntity.class)
                          .setParameter("campaignId", CAMPAIGN_ID)
                          .setParameter("doneState", CleanupScanUnitState.DONE.name())
                          .setParameter("failedState", CleanupScanUnitState.FAILED.name())
                          .setParameter("maxAttemptCount", maxAttemptCount)
                          .getResultList();
    }
  }

  /** Executes the repository's own settled-failure count JPQL, likewise. */
  private long settledFailures(long maxAttemptCount) {
    try (EntityManager entityManager = sessionFactory.createEntityManager()) {
      return entityManager.createQuery(jpqlOf("countSettledFailures"), Long.class)
                          .setParameter("campaignId", CAMPAIGN_ID)
                          .setParameter("state", CleanupScanUnitState.FAILED.name())
                          .setParameter("maxAttemptCount", maxAttemptCount)
                          .getSingleResult();
    }
  }

  private List<CleanupScanUnitEntity> allUnits(long campaignId) {
    try (EntityManager entityManager = sessionFactory.createEntityManager()) {
      return entityManager.createQuery("SELECT u FROM CleanupScanUnit u WHERE u.campaignId = :campaignId ORDER BY u.id ASC",
                                       CleanupScanUnitEntity.class)
                          .setParameter("campaignId", campaignId)
                          .getResultList();
    }
  }

  /**
   * The JPQL of a repository method, from its {@code @Query} annotation: the
   * queries under test are the production ones, never a copy.
   */
  private String jpqlOf(String methodName) {
    List<Query> queries = new ArrayList<>();
    for (var method : CleanupScanUnitDAO.class.getMethods()) {
      if (method.getName().equals(methodName) && method.getAnnotation(Query.class) != null) {
        queries.add(method.getAnnotation(Query.class));
      }
    }
    assertEquals(1, queries.size(), methodName + " must stay a single @Query-annotated method of CleanupScanUnitDAO");
    String jpql = queries.get(0).value();
    assertNotNull(jpql);
    return jpql;
  }

  @Test
  void shouldDropEveryUnitRowOfACampaignInOneStatement() throws NoSuchMethodException {
    long deletedCampaignId = 9301L;
    try (EntityManager entityManager = sessionFactory.createEntityManager()) {
      entityManager.getTransaction().begin();
      entityManager.persist(unit(deletedCampaignId, "/Users/z___", CleanupScanUnitState.DONE, 1));
      entityManager.persist(unit(deletedCampaignId, "/Trash", CleanupScanUnitState.PENDING, 0));
      entityManager.getTransaction().commit();

      entityManager.getTransaction().begin();
      int deleted = entityManager.createQuery(queryOf("deleteByCampaignId", long.class))
                                 .setParameter("campaignId", deletedCampaignId)
                                 .executeUpdate();
      entityManager.getTransaction().commit();

      assertEquals(2, deleted, "The bulk statement must drop every unit row of the campaign, in one go");
      // The fixture campaigns are untouched: a delete scoped to one campaign that
      // reached another would drop the checkpoints of a scan still walking
      assertEquals(9L,
                   entityManager.createQuery("SELECT COUNT(u) FROM CleanupScanUnit u WHERE u.campaignId IN (:a, :b)", Long.class)
                                .setParameter("a", CAMPAIGN_ID)
                                .setParameter("b", OTHER_CAMPAIGN_ID)
                                .getSingleResult(),
                   "Another campaign's unit rows must be untouched");
    }
  }

  @Test
  void shouldReportTheUnitRowsWhoseCampaignRowIsGone() throws NoSuchMethodException {
    // The scan-unit half of the orphan sweep: rows a delete interrupted by a JVM
    // death left behind, reachable by no query and holding space forever
    try (EntityManager entityManager = sessionFactory.createEntityManager()) {
      entityManager.getTransaction().begin();
      CleanupCampaignEntity liveCampaign = new CleanupCampaignEntity();
      liveCampaign.setName("Still there");
      liveCampaign.setState("SIMULATED");
      entityManager.persist(liveCampaign);
      entityManager.persist(unit(liveCampaign.getId(), "/Users/kept", CleanupScanUnitState.RUNNING, 1));
      long orphanedCampaignId = 9401L;
      entityManager.persist(unit(orphanedCampaignId, "/Users/orphaned", CleanupScanUnitState.DONE, 1));
      entityManager.getTransaction().commit();

      List<Long> orphans = entityManager.createQuery(queryOf("findOrphanCampaignIds"), Long.class).getResultList();

      assertTrue(orphans.contains(orphanedCampaignId),
                 "A unit row whose campaign row is gone must be reported as sweepable: " + orphans);
      assertFalse(orphans.contains(liveCampaign.getId()),
                  "A LIVE campaign's units must never be swept — they are the checkpoints its scan resumes from: " + orphans);
    }
  }

  private String queryOf(String methodName, Class<?>... parameterTypes) throws NoSuchMethodException {
    Query query = CleanupScanUnitDAO.class.getMethod(methodName, parameterTypes).getAnnotation(Query.class);
    assertNotNull(query, methodName + " must stay annotated @Query");
    return query.value();
  }

  private static CleanupScanUnitEntity unit(long campaignId, String unitPath, CleanupScanUnitState state, long attemptCount) {
    CleanupScanUnitEntity entity = new CleanupScanUnitEntity();
    entity.setCampaignId(campaignId);
    entity.setUnitPath(unitPath);
    entity.setUnitPathHash(CleanupScanUnitEntity.hashUnitPath(campaignId + unitPath));
    entity.setState(state.name());
    entity.setAttemptCount(attemptCount);
    if (state == CleanupScanUnitState.FAILED) {
      entity.setFailureReason("cleanup.scanUnitFailed");
    }
    return entity;
  }

}
