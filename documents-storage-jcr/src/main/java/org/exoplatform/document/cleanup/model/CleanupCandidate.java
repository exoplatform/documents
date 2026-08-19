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

import org.exoplatform.document.cleanup.constant.CleanupAction;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A file (JCR nt:file node) qualifying for a cleanup action, as computed by a
 * dry-run scan or a revalidation. A previously-exempted file (carrying the
 * exo:cleanupExemption mixin) still qualifying by the criteria is emitted
 * flagged {@code exempted}, with the mixin's decision metadata when readable,
 * so it stays visible as 'Kept' in every campaign instead of being skipped.
 */
@Data
@NoArgsConstructor
public class CleanupCandidate {

  private String        nodeUuid;

  private String        path;

  private long          ownerIdentityId;

  private long          fileSize;

  private long          versionsSize;

  private CleanupAction action;

  private long          createdTime;

  private long          lastModifiedTime;

  /** Whether the node carries the exo:cleanupExemption mixin. */
  private boolean       exempted;

  /** exo:cleanupExemptedBy mixin property, null when unreadable. */
  private String        exemptedBy;

  /**
   * exo:cleanupExemptedDate mixin property (epoch millis), 0 when unreadable.
   */
  private long          exemptedDate;

  public CleanupCandidate(String nodeUuid, // NOSONAR
                          String path,
                          long ownerIdentityId,
                          long fileSize,
                          long versionsSize,
                          CleanupAction action,
                          long createdTime,
                          long lastModifiedTime) {
    this.nodeUuid = nodeUuid;
    this.path = path;
    this.ownerIdentityId = ownerIdentityId;
    this.fileSize = fileSize;
    this.versionsSize = versionsSize;
    this.action = action;
    this.createdTime = createdTime;
    this.lastModifiedTime = lastModifiedTime;
  }

}
