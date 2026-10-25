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

/**
 * A speaker of a transcript whom someone named, as the REST answers it
 * (EXO-92123): the person, for every reader of the file to see.
 *
 * @param speaker the speaker's number in the transcript
 * @param identityId the person's identity id
 * @param username the person's user name
 * @param fullName the person's full name
 * @param avatarUrl the person's avatar
 */
public record TranscriptionSpeakerEntity(String speaker, String identityId, String username, String fullName, String avatarUrl) {
}
