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
 * The people who may be the speakers of a recording (EXO-92126).
 *
 * @param attendees true when they are the attendees of the meeting the
 *          recording belongs to; false when no meeting is known and they are
 *          people who can open the file
 * @param identityIds their identity ids, enabled users only
 */
public record TranscriptionSpeakerCandidates(boolean attendees, List<Long> identityIds) {
}
