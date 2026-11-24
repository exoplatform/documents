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
package org.exoplatform.documents.mcp.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One row of a document's « Manage access » list, as
 * {@code get_document_access} returns it: a name, never an id, a username or
 * an email.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DocumentCollaboratorModel {

  /** The user's full name, the space's display name or the group's label. */
  private String name;

  /** {@code user}, {@code space} or {@code group}. */
  private String type;

  /** {@code edit} or {@code view}. */
  private String access;

}
