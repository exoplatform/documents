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
 * The transcription state of a media file as the REST answers it (EXO-92097).
 *
 * @param available whether a transcription engine is installed; when false,
 *          the other fields are empty and no transcript affordance is shown
 * @param status QUEUED, PROCESSING, DONE or FAILED, or null when the file was
 *          never sent to transcription
 * @param progress the pieces transcribed out of the pieces to transcribe ("3/9")
 * @param error the message code of the last failure
 * @param language the ISO-639-1 language of the transcript
 * @param durationSeconds the duration of the media
 * @param updated when the state last changed, in milliseconds since the epoch
 * @param hasSegments whether the transcript has timed segments
 */
public record TranscriptionStateEntity(boolean available,
                                       String status,
                                       String progress,
                                       String error,
                                       String language,
                                       Long durationSeconds,
                                       Long updated,
                                       boolean hasSegments) {
}
