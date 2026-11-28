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
package org.exoplatform.documents.service;

import java.util.List;

import org.exoplatform.documents.model.DocumentNameLookupHit;
import org.exoplatform.documents.model.DocumentNameLookupRequest;

/**
 * A contribution to the assistant's lookup of a file by its name: another
 * add-on that holds named data about files (for instance a form filled in on
 * a file) names the files that a name matches in its own data, when no file
 * carries that name.
 * <p>
 * Only the assistant's name lookup calls it, after the lookup by name and the
 * title search both found nothing. The caller reads each contributed id back
 * with the user's own rights and drops an id that names no file the user may
 * read, a file in the trash, or a file outside the space searched: a
 * contributor never decides what the user sees.
 * <p>
 * A contributor is a Spring {@code @Service} with an explicit bean name,
 * looked up by type in the portal container at each lookup; it carries no
 * business logic and delegates to its add-on's service layer.
 */
public interface DocumentNameLookupPlugin {

  /**
   * Finds the files that a name matches in the contributor's own data, for one
   * user. Called on the request thread: the contributor bounds its own time
   * and answers an empty list, never an exception, when its backend is
   * unavailable.
   *
   * @param request the user, the name, the space searched, the number of
   *          results wanted and the user's locale
   * @return the matching files, best first, each with what matched; never
   *         null
   */
  List<DocumentNameLookupHit> findByName(DocumentNameLookupRequest request);

}
