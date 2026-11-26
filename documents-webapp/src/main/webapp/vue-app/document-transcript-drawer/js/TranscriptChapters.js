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

/** The shortest recording grouped into chapters, in seconds, as the server decides it. */
const CHAPTERS_MIN_SECONDS = 300;

/**
 * The colours of a transcript's chapters, in turn: the dot of the list, the
 * guide along an unfolded chapter's lines, and anything else that marks a
 * chapter (the timeline) reads its colour from its group. The first is the
 * theme's primary colour, so a chapter follows the site's branding; the others
 * are fixed categorical colours told apart from it, the PO's mockup ones.
 */
export const CHAPTER_COLORS = ['var(--allPagesPrimaryColor, #2f6db5)', '#c4622d', '#7a5a8c', '#2e7d4f'];

/**
 * Groups a transcript's segments under its chapters, each chapter holding the
 * segments from its first one to the next chapter's. Chapters that do not fit
 * the segments (fewer than two, untitled, out of order or out of range), or a
 * recording shorter than five minutes, are ignored as a whole, and the
 * segments make one group with no chapter: the transcript and the timeline
 * both show chapters exactly when this gives some.
 *
 * @param {Array<Object>} segments the transcript's segments, each with start
 *          and text
 * @param {Array<Object>} chapters the chapters, each with start, index, title
 *          and summary, or null
 * @param {number} durationSeconds the recording's stored duration, or null
 * @returns {Array<Object>} the groups in order, each with its key, its chapter
 *          or null, its colour (null without chapter), and its rows; a row holds its segment, the segment's index
 *          in the transcript, and whether it is the first row of its group,
 *          where a speaker's name shows again however the previous group ended
 */
export function groupByChapters(segments, chapters, durationSeconds) {
  const rows = (segments || []).map((segment, index) => ({segment, index, firstOfChapter: index === 0}));
  if (!showsChapters(segments, chapters, durationSeconds)) {
    return [{key: 'all', chapter: null, color: null, rows}];
  }
  return chapters.map((chapter, position) => {
    const first = position === 0 ? 0 : chapter.index;
    const end = position + 1 < chapters.length ? chapters[position + 1].index : rows.length;
    return {
      key: `chapter-${position}`,
      chapter,
      color: CHAPTER_COLORS[position % CHAPTER_COLORS.length],
      rows: rows.slice(first, end).map((row, rank) => ({...row, firstOfChapter: rank === 0})),
    };
  });
}

/**
 * Tells whether a transcript shows chapters: a recording of at least five
 * minutes, with at least two titled chapters whose first segments follow each
 * other within the transcript. The recording's length is its stored duration,
 * the one the server measures before making chapters and lacksChapters reads,
 * so a recording the server made chapters for shows them, trailing silence or
 * speech starting after 0:00 included. Without a stored duration, it is where
 * the last segment ends, rounded to the second as the stored duration is.
 *
 * @param {Array<Object>} segments the transcript's segments, each with start
 *          and end, or null
 * @param {Array<Object>} chapters the chapters, or null
 * @param {number} durationSeconds the recording's stored duration, or null
 * @returns {boolean} true when the chapters are shown
 */
export function showsChapters(segments, chapters, durationSeconds) {
  const count = segments?.length || 0;
  const length = Number.isFinite(durationSeconds)
    ? durationSeconds
    : Math.round(count && (segments[count - 1].end || segments[count - 1].start) || 0);
  return count > 0 && length >= CHAPTERS_MIN_SECONDS && fitChapters(count, chapters);
}

/**
 * Tells whether chapters fit a transcript's segments.
 *
 * @param {number} count how many segments the transcript has
 * @param {Array<Object>} chapters the chapters, or null
 * @returns {boolean} true for at least two titled chapters whose first segments
 *          follow each other within the transcript
 */
function fitChapters(count, chapters) {
  if (!count || !Array.isArray(chapters) || chapters.length < 2) {
    return false;
  }
  return chapters.every((chapter, position) => chapter?.title
    && Number.isInteger(chapter.index)
    && chapter.index < count
    && (position === 0 || chapter.index > chapters[position - 1].index));
}

/** How long after its transcript a recording's chapters may still come, in milliseconds. */
const CHAPTERS_WAIT_MILLIS = 180000;

/**
 * Tells whether a transcribed recording can be grouped into chapters and has
 * none yet.
 *
 * @param {Object} state the transcription state
 * @returns {boolean} true for a transcript done, with timed segments and no
 *          chapters, of a recording long enough
 */
export function lacksChapters(state) {
  return state?.status === 'DONE'
    && !!state.hasSegments
    && !state.hasChapters
    && (state.durationSeconds || 0) >= CHAPTERS_MIN_SECONDS;
}

/**
 * Tells whether a transcribed recording may still get its chapters: the
 * server said they are coming when they were asked for, and they come a
 * little later.
 *
 * @param {Object} state the transcription state
 * @param {number} comingSince when the server said the chapters are coming,
 *          in milliseconds since the epoch, or null when it did not
 * @returns {boolean} true for a transcript with no chapters yet, said to be
 *          coming less than three minutes ago
 */
export function awaitsChapters(state, comingSince) {
  return lacksChapters(state) && !!comingSince && Date.now() - comingSince < CHAPTERS_WAIT_MILLIS;
}
