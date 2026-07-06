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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A single version entry of a versioned document.
 */
@JsonInclude(Include.NON_EMPTY)
public record DocumentVersionModel(@JsonProperty("version_id") String id,
                                   @JsonProperty("version_number") int versionNumber,
                                   String title,
                                   String summary,
                                   String author,
                                   @JsonProperty("author_full_name") String authorFullName,
                                   @JsonProperty("created_date") String createdDate,
                                   @JsonProperty("is_current") boolean current,
                                   long size) {
}
