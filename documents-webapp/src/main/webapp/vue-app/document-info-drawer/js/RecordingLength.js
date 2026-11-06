/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <gnu.org/licenses>.
 */

/**
 * Writes how long a recording lasts the way the meeting block of the agenda
 * writes it: seconds under a minute (« 17 s »), minutes under an hour
 * (« 58 min »), then hours and two-digit minutes (« 1 h 05 »). Minutes are
 * rounded to the nearest one.
 *
 * @param {Number} seconds the duration
 * @param {Function} t the i18n lookup, called with a key and its parameters
 * @returns {String} the duration as shown, or null when it is unknown
 */
export function formatRecordingLength(seconds, t) {
  if (typeof seconds !== 'number' || !Number.isFinite(seconds) || seconds <= 0) {
    return null;
  }
  if (seconds < 59.5) {
    return t('documents.drawer.details.duration.seconds', {0: Math.max(1, Math.round(seconds))});
  }
  const totalMinutes = Math.round(seconds / 60);
  if (totalMinutes < 60) {
    return t('documents.drawer.details.duration.minutes', {0: totalMinutes});
  }
  return t('documents.drawer.details.duration.hours', {
    0: Math.floor(totalMinutes / 60),
    1: String(totalMinutes % 60).padStart(2, '0'),
  });
}

/**
 * Tells whether a file is audio or video, by its mime type.
 *
 * @param {String} mimetype the file's mime type
 * @returns {String} 'video', 'audio', or null for any other file
 */
export function mediaKind(mimetype) {
  const match = /^(audio|video)\//.exec(mimetype || '');
  return match && match[1] || null;
}
