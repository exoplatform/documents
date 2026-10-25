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

import org.exoplatform.commons.exception.ObjectNotFoundException;

/**
 * What makes the chapters of transcripts (EXO-92122), as Documents asks it for
 * those of a transcript opened with none. The transcriber implements it as a
 * bean of the documents context, which {@link DocumentTranscriptionService}
 * collects: Documents cannot see the transcriber's classes, only this
 * interface.
 */
public interface TranscriptChaptersRequester {

  /**
   * Asks for the chapters of a file's transcript, and tells whether they are
   * coming.
   *
   * @param documentId the file's UUID
   * @return true when the chapters are queued now, or were queued and are not
   *         made yet; false when none are to be made (switched off, a recording
   *         too short, or already made or refused for this file)
   * @throws ObjectNotFoundException when the file does not exist
   */
  boolean requestChapters(String documentId) throws ObjectNotFoundException;

}
