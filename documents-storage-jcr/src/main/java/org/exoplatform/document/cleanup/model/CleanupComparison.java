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
package org.exoplatform.document.cleanup.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Delta between two campaigns' candidate sets, matched by node uuid.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CleanupComparison {

  private long baseCampaignId;

  private long otherCampaignId;

  private long newCount;

  private long goneCount;

  private long persistingCount;

  private long newBytes;

  private long goneBytes;

  private long persistingBytes;

}
