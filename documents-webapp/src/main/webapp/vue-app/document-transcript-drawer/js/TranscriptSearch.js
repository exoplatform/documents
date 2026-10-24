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
 * Cuts a text around the occurrences of a searched term, case ignored.
 *
 * @param {string} text the text to search in
 * @param {string} term the searched term
 * @returns {Array<Object>} the pieces of the text in order, each with its
 *          value and whether it is an occurrence of the term
 */
export function splitOccurrences(text, term) {
  const value = text || '';
  const searched = term?.trim();
  if (!searched) {
    return [{value, match: false}];
  }
  const pattern = new RegExp(`(${searched.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')})`, 'gi');
  return value.split(pattern)
    .map((piece, index) => ({value: piece, match: index % 2 === 1}))
    .filter(piece => piece.value.length);
}

/**
 * Counts the occurrences of a searched term in a text, case ignored.
 *
 * @param {string} text the text to search in
 * @param {string} term the searched term
 * @returns {number} the number of occurrences
 */
export function countOccurrences(text, term) {
  return splitOccurrences(text, term).filter(piece => piece.match).length;
}

/**
 * Writes a moment of a recording as minutes and seconds, with hours when it
 * has some.
 *
 * @param {number} seconds the moment, in seconds from the start
 * @returns {string} the moment, as m:ss or h:mm:ss
 */
export function formatMoment(seconds) {
  const total = Math.max(0, Math.floor(Number(seconds) || 0));
  const hours = Math.floor(total / 3600);
  const minutes = Math.floor((total % 3600) / 60);
  const secs = String(total % 60).padStart(2, '0');
  return hours ? `${hours}:${String(minutes).padStart(2, '0')}:${secs}` : `${minutes}:${secs}`;
}
