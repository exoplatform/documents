/*
 * Copyright (C) 2026 eXo Platform SAS
 *
 *  This program is free software: you can redistribute it and/or modify
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
package org.exoplatform.documents.model;

import java.util.Locale;

import org.exoplatform.services.security.Identity;

/**
 * What a {@link org.exoplatform.documents.service.DocumentNameLookupPlugin}
 * is asked.
 *
 * @param viewer the user who looks the name up, with their memberships
 * @param name the name as the user gave it
 * @param spaceId the id of the one space searched, null for every place
 * @param limit the most files wanted
 * @param locale the user's language, for the labels of what matched
 */
public record DocumentNameLookupRequest(Identity viewer, String name, Long spaceId, int limit, Locale locale) {
}
