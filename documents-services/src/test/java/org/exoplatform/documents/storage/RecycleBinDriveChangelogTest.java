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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringWriter;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.OfflineConnection;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;

/**
 * Runs the documents changelog through Liquibase on HSQLDB: the table of the
 * Recycle Bin drives is created with its key and its index, rolled back and
 * created again. MySQL and PostgreSQL are not run here: only the statements
 * Liquibase writes for them are read.
 */
class RecycleBinDriveChangelogTest {

  static final String  CHANGELOG = "db/changelog/documents-rdbms.db.changelog-master.xml";

  private static final String TABLE = "DOCUMENTS_RECYCLE_BIN_DRIVE";

  private Connection   connection;

  @TempDir
  Path                 workDirectory;

  @BeforeEach
  void openDatabase() throws Exception {
    connection = DriverManager.getConnection("jdbc:hsqldb:mem:documents-recycle-bin-changelog-" + System.nanoTime(), "sa", "");
    update(connection);
  }

  @AfterEach
  void closeDatabase() throws Exception {
    try (Statement statement = connection.createStatement()) {
      statement.execute("SHUTDOWN");
    }
    connection.close();
  }

  @Test
  void theTableHoldsOneRowPerDriveOwnerWithItsTotals() throws Exception {
    Map<String, String> columns = new LinkedHashMap<>();
    try (Statement statement = connection.createStatement();
        ResultSet rows = statement.executeQuery("SELECT COLUMN_NAME, IS_NULLABLE FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = '"
            + TABLE + "' ORDER BY ORDINAL_POSITION")) {
      while (rows.next()) {
        columns.put(rows.getString(1), rows.getString(2));
      }
    }
    assertEquals(Map.of("OWNER_ID", "NO", "ITEMS_SIZE", "NO", "ITEMS_COUNT", "NO", "UPDATED_DATE", "YES"), columns);

    execute("INSERT INTO " + TABLE + " (OWNER_ID, ITEMS_SIZE, ITEMS_COUNT) VALUES (7, 10, 1)");
    assertThrows(SQLException.class, () -> execute("INSERT INTO " + TABLE + " (OWNER_ID, ITEMS_SIZE, ITEMS_COUNT) VALUES (7, 20, 1)"));
  }

  /**
   * HSQLDB accepts a descending index column and reports it ascending, so the
   * directions are read in the statements Liquibase writes for the two other
   * engines, without a database.
   */
  @ParameterizedTest
  @ValueSource(strings = { "mysql", "postgresql" })
  void theIndexCarriesTheDirectionsOfTheLargestFirstListing(String engine) throws Exception {
    String sql = statementsFor(engine);

    Matcher index = Pattern.compile("CREATE INDEX \\S*IDX_DOCUMENTS_RECYCLE_BIN_DRIVE_01\\S* ON \\S+\\s*\\(([^)]*)\\)").matcher(sql);
    assertTrue(index.find(), sql);
    assertEquals("ITEMS_SIZE DESC, OWNER_ID", index.group(1).replaceAll("[`\"]", "").trim());
    assertTrue(Pattern.compile("CREATE TABLE \\S*" + TABLE).matcher(sql).find(), sql);
  }

  @Test
  void theChangesetRollsBackAndAppliesAgain() throws Exception {
    assertTrue(tableExists());

    liquibase(connection).rollback(1, "");
    assertFalse(tableExists());
    // The table of the public links, created by an earlier changeset, stays.
    execute("SELECT COUNT(*) FROM DOCUMENTS_PUBLIC_ACCESS");

    update(connection);
    assertTrue(tableExists());
  }

  /**
   * Applies the documents changelog to a database. Liquibase leaves the
   * connection out of auto-commit: a caller that writes rows with it afterwards
   * commits them itself.
   */
  static void update(Connection connection) throws Exception {
    liquibase(connection).update(new Contexts(), new LabelExpression());
  }

  private static Liquibase liquibase(Connection connection) throws Exception {
    Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
    return new Liquibase(CHANGELOG, new ClassLoaderResourceAccessor(), database);
  }

  /** The statements the changelog gives for an engine, with no database. */
  private String statementsFor(String engine) throws Exception {
    ClassLoaderResourceAccessor resourceAccessor = new ClassLoaderResourceAccessor();
    // Liquibase keeps what it has written in this file: a new one each run.
    String url = "offline:" + engine + "?changeLogFile=" + workDirectory.resolve(engine + "-changelog.csv").toUri().getPath();
    Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new OfflineConnection(url, resourceAccessor));
    StringWriter sql = new StringWriter();
    new Liquibase(CHANGELOG, resourceAccessor, database).update(new Contexts(), new LabelExpression(), sql);
    return sql.toString();
  }

  private boolean tableExists() throws SQLException {
    try (Statement statement = connection.createStatement();
        ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = '" + TABLE + "'")) {
      rows.next();
      return rows.getInt(1) == 1;
    }
  }

  private void execute(String sql) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.execute(sql);
    }
  }

}
