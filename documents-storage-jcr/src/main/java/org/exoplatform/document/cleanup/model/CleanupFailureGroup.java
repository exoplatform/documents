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
 * One group of a campaign's failed items: the failure message code and how many
 * SKIPPED items carry it, computed by a single grouped aggregate query (the item
 * rows are never loaded).
 * <p>
 * {@link #retryable} is filled by the SERVICE, from its retryable allowlist —
 * never by the query. What is worth re-attempting is a business rule, and the
 * database has no business holding a copy of it.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CleanupFailureGroup {

  private String  reason;

  private long    count;

  private boolean retryable;

}
