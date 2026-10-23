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

import org.exoplatform.documents.constant.TranscriptionStatus;

import lombok.Builder;

/**
 * The transcription state of an audio or video file, as stored on the file
 * (EXO-92097).
 *
 * @param status where the transcription stands, null when the file was never
 *          sent to transcription
 * @param progress the pieces transcribed out of the pieces to transcribe ("3/9"),
 *          or null
 * @param error the message code of the last failure, or null
 * @param language the ISO-639-1 language of the transcript, or null
 * @param durationSeconds the duration of the media, or null when not measured
 * @param updated when the state last changed, in milliseconds since the epoch,
 *          or null
 * @param hasSegments whether the transcript has timed segments
 * @param media whether the file is an audio or a video, the only files that
 *          can be transcribed
 */
@Builder(toBuilder = true)
public record TranscriptionState(TranscriptionStatus status,
                                 String progress,
                                 String error,
                                 String language,
                                 Long durationSeconds,
                                 Long updated,
                                 boolean hasSegments,
                                 boolean media) {
}
