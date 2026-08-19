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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

import org.exoplatform.document.cleanup.constant.CleanupAction;

class CleanupCampaignItemDAOTest {

  @Test
  void shouldKeepReclaimableBytesFragmentAlignedWithDeleteActionName() {
    // The JPQL fragment must embed the enum name as a string literal (the
    // entity stores the action as a plain string): a CleanupAction.DELETE
    // rename must break this test, never silently break the queries
    assertTrue(CleanupCampaignItemDAO.RECLAIMABLE_BYTES.contains("i.action = '" + CleanupAction.DELETE.name() + "'"),
               "RECLAIMABLE_BYTES JPQL fragment no longer matches CleanupAction.DELETE.name()");
  }

  /**
   * The path-search contract is SPLIT across two layers: the Storage builds the
   * pattern (trimmed, lower-cased, '%'/'_'/'|' escaped with '|' — pinned by
   * {@code CleanupCampaignStorageTest}) and the query here supplies the matching
   * halves. Nothing else parses these queries, so each half is asserted:
   * <ul>
   * <li>{@code LOWER(i.path)} — without it the search becomes case SENSITIVE,
   * while the pattern arrives lower-cased, i.e. 'Invoice' stops matching</li>
   * <li>{@code ESCAPE '|'} — without it the escaping becomes double-escaping: a
   * term holding '_' or '%' matches nothing at all</li>
   * <li>{@code :searchPattern IS NULL} — the null-tolerance that makes a blank
   * term mean NO filter instead of matching nothing</li>
   * </ul>
   * A {@code @DataJpaTest} executing the queries against an in-memory database
   * would be the stronger guard (it would also cover the collation), but it is
   * out of scope for this module's plain-JUnit suite.
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

  private String queryOf(String methodName, Class<?>... parameterTypes) throws NoSuchMethodException {
    Query query = CleanupCampaignItemDAO.class.getMethod(methodName, parameterTypes).getAnnotation(Query.class);
    assertNotNull(query, methodName + " must stay annotated @Query");
    return query.value();
  }

}
