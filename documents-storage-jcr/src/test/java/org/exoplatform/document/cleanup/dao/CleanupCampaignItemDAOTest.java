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

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

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

}
