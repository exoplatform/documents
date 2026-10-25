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
import java.util.Optional;

/**
 * Tells who attended the meeting a recording belongs to (EXO-92126), for the
 * add-on that knows the meeting. The documents add-on collects every bean that
 * implements it: such a bean is a {@code @Service}, so that the documents
 * context sees it whichever add-on boots first.
 */
public interface TranscriptionAttendeesPlugin {

  /**
   * The attendees of the meeting a recording belongs to.
   *
   * @param documentId the recording file's UUID
   * @return the attendees' identity ids, users only; empty when this plugin
   *         knows no meeting for the file, or knows it but not its attendees
   */
  Optional<List<Long>> getAttendees(String documentId);

}
