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

/**
 * A sentence of a transcript quoted as evidence of who a speaker is
 * (EXO-92126).
 *
 * @param start when it is said, in seconds from the beginning of the recording
 * @param speaker the speaker's number in the transcript, possibly null
 * @param text what is said
 */
public record TranscriptionQuote(double start, String speaker, String text) {
}
