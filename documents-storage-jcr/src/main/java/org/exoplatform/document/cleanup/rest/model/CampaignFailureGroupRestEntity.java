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
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <gnu.org/licenses>.
 */
package org.exoplatform.document.cleanup.rest.model;

import lombok.Data;

/**
 * REST representation of one grouped failure of a campaign: the failure message
 * code the console localizes, how many items carry it, and whether a retry would
 * re-attempt them — the SERVER's answer to that last question, so the console
 * never holds its own copy of the retryable rule.
 */
@Data
public class CampaignFailureGroupRestEntity {

  private String  reason;

  private long    count;

  private boolean retryable;

}
