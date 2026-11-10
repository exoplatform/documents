/*
 * Copyright (C) 2026 eXo Platform SAS
 *
 *  This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <gnu.org/licenses>.
 */
package org.exoplatform.documents.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;

import org.junit.jupiter.api.Test;

import jakarta.persistence.Column;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;

/**
 * The documents changelog run by Liquibase itself on HSQLDB: every changeset
 * applies to an empty database, and the public link's creator column
 * (EXO-91530) applies, rolls back and applies again. HSQLDB only: the MySQL and
 * PostgreSQL behaviour of the {@code addColumn} is not checked here.
 */
class DocumentsChangelogTest {

  private static final String CHANGELOG      = "db/changelog/documents-rdbms.db.changelog-master.xml";

  private static final String TABLE          = "DOCUMENTS_PUBLIC_ACCESS";

  private static final String CREATOR_COLUMN = "CREATOR_ID";

  @Test
  void theCreatorColumnAppliesRollsBackAndReapplies() throws Exception {
    try (Connection connection = DriverManager.getConnection("jdbc:hsqldb:mem:documents" + System.nanoTime(), "sa", "")) {
      Liquibase liquibase = new Liquibase(CHANGELOG,
                                          new ClassLoaderResourceAccessor(),
                                          DatabaseFactory.getInstance()
                                                         .findCorrectDatabaseImplementation(new JdbcConnection(connection)));
      liquibase.update(2, new Contexts(), new LabelExpression());
      liquibase.tag("before-creator");
      assertFalse(columnExists(connection, CREATOR_COLUMN), "the column is not there before its changeset");
      try (Statement statement = connection.createStatement()) {
        statement.executeUpdate("INSERT INTO " + TABLE + " (ID, NODE_ID) VALUES (1, 'legacy-link')");
      }

      liquibase.update("");
      assertTrue(columnExists(connection, CREATOR_COLUMN), "1.0.0-exo-91530-01 adds " + CREATOR_COLUMN);
      assertColumnIsANullableBigint(connection);
      try (Statement statement = connection.createStatement();
           ResultSet legacyLink = statement.executeQuery("SELECT " + CREATOR_COLUMN + " FROM " + TABLE + " WHERE ID = 1")) {
        assertTrue(legacyLink.next(), "an existing link survives the changeset");
        legacyLink.getLong(1);
        assertTrue(legacyLink.wasNull(), "an existing link records no creator");
      }

      liquibase.rollback("before-creator", "");
      assertFalse(columnExists(connection, CREATOR_COLUMN), "the rollback drops the column");
      assertTrue(columnExists(connection, "NODE_ID"), "and nothing else");

      liquibase.update("");
      assertTrue(columnExists(connection, CREATOR_COLUMN), "the changeset applies again after its rollback");
    }
  }

  @Test
  void theEntityMapsTheColumnTheChangesetCreates() throws Exception {
    Column column = PublicDocumentAccessEntity.class.getDeclaredField("creatorId").getAnnotation(Column.class);
    assertEquals(CREATOR_COLUMN, column.name());
    assertTrue(column.nullable(), "a legacy link has no creator");
    assertNull(new PublicDocumentAccessEntity().getCreatorId());
  }

  private static void assertColumnIsANullableBigint(Connection connection) throws SQLException {
    try (ResultSet columns = connection.getMetaData().getColumns(null, null, TABLE, CREATOR_COLUMN)) {
      assertTrue(columns.next());
      assertEquals(Types.BIGINT, columns.getInt("DATA_TYPE"));
      assertEquals(DatabaseMetaData.columnNullable, columns.getInt("NULLABLE"));
    }
  }

  private static boolean columnExists(Connection connection, String column) throws SQLException {
    try (ResultSet columns = connection.getMetaData().getColumns(null, null, TABLE, column)) {
      return columns.next();
    }
  }
}
