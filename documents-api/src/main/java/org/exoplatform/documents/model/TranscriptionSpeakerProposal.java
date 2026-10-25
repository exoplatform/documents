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
 * A proposal of who a speaker of a file's transcript is, as shown to a person
 * who can edit the file (EXO-92126): the proposal and the sentences that show
 * it.
 *
 * @param speaker the speaker's number in the transcript
 * @param identityId the identity id of the person proposed
 * @param strong whether the evidence was strong
 * @param autoApplied whether the speaker was named after it without waiting for
 *          a person, and still is
 * @param quotes the sentences that show it, in order
 * @param premiseSpeaker for a proposal made by elimination, the other voice it
 *          follows from; null otherwise
 */
public record TranscriptionSpeakerProposal(String speaker,
                                           long identityId,
                                           boolean strong,
                                           boolean autoApplied,
                                           List<TranscriptionQuote> quotes,
                                           String premiseSpeaker) {
}
