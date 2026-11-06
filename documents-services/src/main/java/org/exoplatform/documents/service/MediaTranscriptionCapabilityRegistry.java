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

/**
 * Where a transcription engine says it is installed (EXO-92097). Documents is
 * community code and the transcriber an enterprise one, booting after it: the
 * transcriber registers itself here when it starts, and the transcript
 * affordances (the state, the transcript tab, the retry) show only while one is
 * registered.
 */
@Service
public class MediaTranscriptionCapabilityRegistry {

  private final Set<String> transcribers = ConcurrentHashMap.newKeySet();

  /**
   * Records that a transcription engine is installed.
   *
   * @param transcriberId a name for the engine
   */
  public void register(String transcriberId) {
    transcribers.add(transcriberId);
  }

  /**
   * Records that a transcription engine is no longer installed.
   *
   * @param transcriberId the name it registered with
   */
  public void unregister(String transcriberId) {
    transcribers.remove(transcriberId);
  }

  /**
   * Tells whether media files can be transcribed on this server.
   *
   * @return true when a transcription engine is registered
   */
  public boolean isAvailable() {
    return !transcribers.isEmpty();
  }

}
