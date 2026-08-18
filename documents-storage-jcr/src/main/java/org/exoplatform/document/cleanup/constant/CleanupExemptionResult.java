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
 * Outcome of adding the exemption mixin on a JCR node, letting the Service
 * layer distinguish a node that no longer exists (item GONE) from a transient
 * write failure (item state untouched, retryable).
 */
public enum CleanupExemptionResult {

  /** The node exists and now carries the exemption mixin. */
  ADDED,

  /** The node doesn't exist anymore. */
  NOT_FOUND,

  /** The node exists but the exemption could not be persisted. */
  FAILED;

}
