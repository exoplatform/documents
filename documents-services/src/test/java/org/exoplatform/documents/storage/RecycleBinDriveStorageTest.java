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
package org.exoplatform.documents.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

import org.exoplatform.documents.dao.RecycleBinDriveRepository;
import org.exoplatform.documents.entity.RecycleBinDriveEntity;
import org.exoplatform.documents.model.RecycleBinDrive;

/**
 * Runs the storage of the Recycle Bin drives through the real repository
 * proxy, Hibernate and HSQLDB, on the schema the changelog builds: every
 * statement is one the engine runs, and the entity matches the table. The
 * transactional methods are not called: each test calls the logic they wrap
 * inside a transaction of its own. MySQL and PostgreSQL are not run here.
 */
class RecycleBinDriveStorageTest {

  private Connection             keeper;

  private EntityManagerFactory   factory;

  private EntityManager          entityManager;

  private RecycleBinDriveStorage storage;

  @BeforeEach
  void openDatabase() throws Exception {
    String url = "jdbc:hsqldb:mem:documents-recycle-bin-storage-" + System.nanoTime();
    keeper = DriverManager.getConnection(url, "sa", "");
    RecycleBinDriveChangelogTest.update(keeper);
    factory = Persistence.createEntityManagerFactory("documents-recycle-bin-test", Map.of("jakarta.persistence.jdbc.url", url));
    entityManager = factory.createEntityManager();
    storage = storage(entityManager);
  }

  @AfterEach
  void closeDatabase() throws Exception {
    if (entityManager != null && entityManager.isOpen()) {
      entityManager.close();
    }
    if (factory != null) {
      factory.close();
    }
    try (Statement statement = keeper.createStatement()) {
      statement.execute("SHUTDOWN");
    }
    keeper.close();
  }

  @Test
  void theFirstItemOfADriveFindsNoRowAndCreatesIt() {
    assertFalse(resultInTransaction(() -> storage.doAdd(7, 10)));
    assertEquals(0, storage.countDrives());

    inTransaction(() -> storage.doCreate(7, 10));

    assertEquals(List.of(new RecycleBinDrive(7, 10, 1)), storage.getDrives(0, 10));
    assertNotNull(row(7).getUpdatedDate());
  }

  @Test
  void eachItemAddsItsSizeAndCountsOne() {
    inTransaction(() -> storage.doCreate(7, 10));

    assertTrue(resultInTransaction(() -> storage.doAdd(7, 5)));
    assertTrue(resultInTransaction(() -> storage.doAdd(7, 0)));

    assertEquals(List.of(new RecycleBinDrive(7, 15, 3)), storage.getDrives(0, 10));
  }

  @Test
  void aDriveThatAlreadyHasARowIsNotCreatedAgainNorReplaced() {
    inTransaction(() -> storage.doCreate(7, 10));

    entityManager.getTransaction().begin();
    try {
      assertThrows(RuntimeException.class, () -> storage.doCreate(7, 99));
    } finally {
      entityManager.getTransaction().rollback();
    }

    assertEquals(List.of(new RecycleBinDrive(7, 10, 1)), storage.getDrives(0, 10));
  }

  @Test
  void anAdditionCountsOnTopOfWhatAnotherWriterCommittedMeanwhile() {
    inTransaction(() -> storage.doCreate(7, 10));
    EntityManager other = factory.createEntityManager();
    try {
      // This writer has read the row before the other one changes it.
      assertEquals(10, row(7).getItemsSize());
      other.getTransaction().begin();
      storage(other).doAdd(7, 5);
      other.getTransaction().commit();

      inTransaction(() -> storage.doAdd(7, 1));
    } finally {
      other.close();
    }

    assertEquals(List.of(new RecycleBinDrive(7, 16, 3)), storage.getDrives(0, 10));
  }

  @Test
  void anItemLeavingTheBinIsSubtractedAndTheLastOneDeletesTheRow() {
    inTransaction(() -> storage.doCreate(7, 10));
    inTransaction(() -> storage.doAdd(7, 5));

    inTransaction(() -> storage.doRemove(7, 10));
    assertEquals(List.of(new RecycleBinDrive(7, 5, 1)), storage.getDrives(0, 10));

    inTransaction(() -> storage.doRemove(7, 5));
    assertEquals(0, storage.countDrives());
  }

  @Test
  void anItemLeavingADriveWithNoRowChangesNothing() {
    inTransaction(() -> storage.doCreate(7, 10));

    inTransaction(() -> storage.doRemove(8, 10));

    assertEquals(List.of(new RecycleBinDrive(7, 10, 1)), storage.getDrives(0, 10));
  }

  @Test
  void anItemChangingDriveIsCountedInTheOtherOneWhetherItHasARowOrNot() {
    inTransaction(() -> storage.doCreate(7, 10));
    inTransaction(() -> storage.doAdd(7, 5));
    inTransaction(() -> storage.doCreate(8, 1));

    inTransaction(() -> storage.doMove(7, 8, 5));
    assertEquals(List.of(new RecycleBinDrive(7, 10, 1), new RecycleBinDrive(8, 6, 2)), storage.getDrives(0, 10));

    inTransaction(() -> storage.doMove(7, 9, 10));
    assertEquals(List.of(new RecycleBinDrive(9, 10, 1), new RecycleBinDrive(8, 6, 2)), storage.getDrives(0, 10));
  }

  @Test
  void theDrivesAreListedLargestFirstThenByOwnerFromAnyOffset() {
    inTransaction(() -> storage.doCreate(5, 10));
    inTransaction(() -> storage.doCreate(3, 30));
    inTransaction(() -> storage.doCreate(9, 10));
    inTransaction(() -> storage.doCreate(4, 20));
    inTransaction(() -> storage.doCreate(1, 1));

    assertEquals(5, storage.countDrives());
    assertEquals(List.of(3L, 4L, 5L, 9L, 1L), storage.getDrives(0, 10).stream().map(RecycleBinDrive::getOwnerId).toList());
    assertEquals(List.of(4L, 5L), storage.getDrives(1, 2).stream().map(RecycleBinDrive::getOwnerId).toList());
    assertEquals(List.of(1L), storage.getDrives(4, 3).stream().map(RecycleBinDrive::getOwnerId).toList());
    assertEquals(List.of(), storage.getDrives(5, 3));
  }

  @Test
  void aTotalThatDriftedBelowZeroIsReadAsZero() {
    inTransaction(() -> storage.doCreate(7, 10));
    inTransaction(() -> storage.doAdd(7, 5));

    inTransaction(() -> storage.doRemove(7, 40));

    assertEquals(List.of(new RecycleBinDrive(7, 0, 1)), storage.getDrives(0, 10));
  }

  /**
   * The transactional methods are not run here. What makes each write one
   * transaction is their annotation, read on the method: a write of several
   * statements depends on it.
   */
  @Test
  void eachWriteIsDeclaredAsOneTransaction() throws Exception {
    for (java.lang.reflect.Method method : List.of(RecycleBinDriveStorage.class.getMethod("add", long.class, long.class),
                                                   RecycleBinDriveStorage.class.getMethod("create", long.class, long.class),
                                                   RecycleBinDriveStorage.class.getMethod("remove", long.class, long.class),
                                                   RecycleBinDriveStorage.class.getMethod("move", long.class, long.class, long.class))) {
      io.meeds.common.ContainerTransactional transactional = method.getAnnotation(io.meeds.common.ContainerTransactional.class);
      assertNotNull(transactional, method.getName());
      assertTrue(transactional.atomic(), method.getName());
    }
  }

  @Test
  void aMoveThatFailsOnTheDriveTheItemJoinsLeavesTheOneItLeavesAsItWas() {
    inTransaction(() -> storage.doCreate(7, 10));
    inTransaction(() -> storage.doAdd(7, 5));
    RecycleBinDriveStorage failing = new RecycleBinDriveStorage(new JpaRepositoryFactory(entityManager).getRepository(RecycleBinDriveRepository.class)) {
      @Override
      boolean doAdd(long ownerId, long size) {
        throw new IllegalStateException("The database refuses the statement");
      }
    };

    entityManager.getTransaction().begin();
    try {
      assertThrows(IllegalStateException.class, () -> failing.doMove(7, 8, 5));
    } finally {
      entityManager.getTransaction().rollback();
    }

    assertEquals(List.of(new RecycleBinDrive(7, 15, 2)), storage.getDrives(0, 10));
  }

  private RecycleBinDriveStorage storage(EntityManager manager) {
    return new RecycleBinDriveStorage(new JpaRepositoryFactory(manager).getRepository(RecycleBinDriveRepository.class));
  }

  private RecycleBinDriveEntity row(long ownerId) {
    return entityManager.find(RecycleBinDriveEntity.class, ownerId);
  }

  private void inTransaction(Runnable work) {
    resultInTransaction(() -> {
      work.run();
      return null;
    });
  }

  private <T> T resultInTransaction(java.util.function.Supplier<T> work) {
    entityManager.getTransaction().begin();
    T result = work.get();
    entityManager.getTransaction().commit();
    return result;
  }

}
