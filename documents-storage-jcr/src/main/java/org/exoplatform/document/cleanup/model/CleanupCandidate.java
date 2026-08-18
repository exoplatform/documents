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

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A file (JCR nt:file node) qualifying for a cleanup action, as computed by a
 * dry-run scan or a revalidation.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CleanupCandidate {

  private String        nodeUuid;

  private String        path;

  private long          ownerIdentityId;

  private long          fileSize;

  private long          versionsSize;

  private CleanupAction action;

  private long          createdTime;

  private long          lastModifiedTime;

}
