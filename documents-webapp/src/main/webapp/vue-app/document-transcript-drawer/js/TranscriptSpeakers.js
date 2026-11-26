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
 * The colours speakers take in turn, by their number (EXO-92123). No
 * 'primary': the platform skin gives an element of that class white text,
 * which empties an outlined chip.
 */
const SPEAKER_COLORS = ['blue darken-2', 'teal', 'orange darken-2', 'purple', 'red darken-1', 'blue-grey', 'green darken-1', 'pink'];

/**
 * The colour of a speaker: the same for a speaker everywhere, taken in turn
 * from the theme colours by its number.
 *
 * @param {string} speaker the speaker's number
 * @returns {string} a theme colour
 */
export function speakerColor(speaker) {
  const number = Number.parseInt(speaker, 10);
  return SPEAKER_COLORS[(Number.isNaN(number) ? 0 : number - 1 + SPEAKER_COLORS.length) % SPEAKER_COLORS.length];
}

/**
 * Tells who a speaker number is, so that two numbers named after one person,
 * or after one guest, are one speaker wherever speakers are told apart: the
 * transcript's runs, the recording's lanes.
 *
 * @param {string} speaker the speaker's number
 * @param {object} speakersByNumber the named speakers, by speaker number
 * @returns {string} a key that a person, a guest and a number never share
 */
export function speakerIdentity(speaker, speakersByNumber) {
  const person = speakersByNumber?.[speaker];
  if (person?.guest) {
    return `guest:${person.fullName}`;
  }
  return person?.identityId ? `person:${person.identityId}` : `speaker:${speaker}`;
}

/**
 * Tells whether a sentence opens a run of sentences of its speaker: the first
 * one, the first under a heading, or one whose speaker is not the previous
 * sentence's. Two numbers named after one person are one speaker.
 *
 * @param {Array<object>} segments the sentences
 * @param {number} index the sentence's index
 * @param {boolean} firstOfGroup whether the sentence is the first under a heading
 * @param {Function} keyOf who a speaker number is, the number itself by default
 * @returns {boolean} true when the sentence shows its speaker
 */
export function opensSpeakerRun(segments, index, firstOfGroup, keyOf = speaker => speaker) {
  const speaker = segments[index]?.speaker;
  const previous = index > 0 && segments[index - 1]?.speaker;
  return !!speaker && (firstOfGroup || index === 0 || !previous || keyOf(previous) !== keyOf(speaker));
}
