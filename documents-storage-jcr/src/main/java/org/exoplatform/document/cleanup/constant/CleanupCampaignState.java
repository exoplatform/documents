/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <gnu.org/licenses>.
 */
package org.exoplatform.document.cleanup.constant;

/**
 * Lifecycle states of a cleanup campaign.
 */
public enum CleanupCampaignState {

  DRAFT,

  DRY_RUN_RUNNING,

  SIMULATED,

  PUBLISHED,

  LOCKED,

  EXECUTING,

  COMPLETED,

  CANCELLED;

  /**
   * @return true when the campaign reached a final state
   */
  public boolean isTerminal() {
    return this == COMPLETED || this == CANCELLED;
  }

}
