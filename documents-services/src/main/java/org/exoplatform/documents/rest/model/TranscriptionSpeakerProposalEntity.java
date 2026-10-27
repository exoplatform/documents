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
package org.exoplatform.documents.rest.model;

import java.util.List;

import org.exoplatform.documents.model.TranscriptionQuote;

/**
 * A proposal of who a speaker of a transcript is, as the REST answers it to a
 * person who can edit the file (EXO-92126).
 *
 * @param speaker the speaker's number in the transcript
 * @param identityId the person's identity id
 * @param username the person's user name
 * @param fullName the person's full name
 * @param avatarUrl the person's avatar
 * @param strong whether the evidence was strong
 * @param autoApplied whether the speaker was named after it without waiting for
 *          a person
 * @param quotes the sentences that show it, each with its moment
 * @param premiseSpeaker for a proposal made by elimination, the other voice it
 *          follows from; null otherwise
 * @param premiseFullName for a proposal made by elimination, the full name of
 *          the person that other voice is taken to be; null otherwise
 */
public record TranscriptionSpeakerProposalEntity(String speaker,
                                                 String identityId,
                                                 String username,
                                                 String fullName,
                                                 String avatarUrl,
                                                 boolean strong,
                                                 boolean autoApplied,
                                                 List<TranscriptionQuote> quotes,
                                                 String premiseSpeaker,
                                                 String premiseFullName) {
}
