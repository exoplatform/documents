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

import java.util.List;

/**
 * Who a speaker of a file's transcript probably is, as the platform proposed it
 * after the transcription (EXO-92126). A proposal is not a name: it is shown to
 * the people who can edit the file until one of them confirms or dismisses it,
 * or, when it was applied without waiting, as the reason the speaker is named.
 *
 * @param speaker the speaker's number in the transcript
 * @param identityId the identity id of the person proposed
 * @param strong whether the evidence was strong enough to name the speaker
 *          without waiting for a person
 * @param autoApplied whether the speaker was named after the proposal without
 *          waiting for a person
 * @param evidence the indexes of the transcript's segments that show it, in
 *          order
 */
public record TranscriptionSpeakerSuggestion(String speaker,
                                             long identityId,
                                             boolean strong,
                                             boolean autoApplied,
                                             List<Integer> evidence) {

  /**
   * Same proposal, applied or not.
   *
   * @param applied whether the speaker was named after it without waiting for a
   *          person
   * @return the proposal with that flag
   */
  public TranscriptionSpeakerSuggestion withAutoApplied(boolean applied) {
    return new TranscriptionSpeakerSuggestion(speaker, identityId, strong, applied, evidence);
  }

}
