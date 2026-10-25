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

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import org.exoplatform.commons.exception.ObjectNotFoundException;

/**
 * Where what makes transcripts' chapters registers itself (EXO-92122).
 * Documents is community code and the transcriber an enterprise one, booting
 * after it: the transcriber registers here when it starts, and a transcript
 * opened with no chapters asks it for them, through this registry, the answer
 * telling the reader whether to wait for them.
 */
@Service
public class TranscriptChaptersRequesterRegistry {

  private final Set<TranscriptChaptersRequester> requesters = ConcurrentHashMap.newKeySet();

  /**
   * Records what makes chapters.
   *
   * @param requester what makes chapters
   */
  public void register(TranscriptChaptersRequester requester) {
    requesters.add(requester);
  }

  /**
   * Forgets what made chapters.
   *
   * @param requester the one registered
   */
  public void unregister(TranscriptChaptersRequester requester) {
    requesters.remove(requester);
  }

  /**
   * Asks every registered requester for the chapters of a file's transcript;
   * each is asked, whatever the others answer.
   *
   * @param documentId the file's UUID
   * @return true when one of them says the chapters are coming
   * @throws ObjectNotFoundException when the file does not exist
   */
  public boolean requestChapters(String documentId) throws ObjectNotFoundException {
    boolean coming = false;
    for (TranscriptChaptersRequester requester : requesters) {
      coming |= requester.requestChapters(documentId);
    }
    return coming;
  }

}
