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
 * Partial-update request body: {"name": "..."} and/or {"graceDays": N}.
 * <p>
 * A dedicated body rather than the whole {@link CampaignRestEntity}: the PATCH
 * updates the EDITABLE attributes only, and reusing the creation DTO would
 * suggest every parameter field is patchable too — the candidacy criteria
 * (period, minimum file size, excluded paths) and the per-file version cap are
 * snapshotted at creation and never change afterwards, because editing them
 * would invalidate the dry-run the administrator is reviewing.
 * <p>
 * Both fields are OPTIONAL and independent: a null field means 'leave that
 * attribute unchanged', so the console can send either one alone or both at
 * once. Both null is refused by the Service ("cleanup.nothingToUpdate") rather
 * than silently accepted as a no-op.
 */
@Data
public class UpdateCampaignRestEntity {

  /**
   * New campaign name, null to leave it unchanged. Trimmed and validated by the
   * Service, never here.
   */
  private String  name;

  /**
   * New grace period in days, null to leave it unchanged. BOXED on purpose: 0 is
   * a MEANINGFUL value — a zero grace period is explicitly valid and elapses at
   * publication — so a primitive int could not tell 'no grace period' from 'no
   * change requested'.
   */
  private Integer graceDays;

}
