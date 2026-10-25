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
import {groupByChapters} from '../../document-transcript-drawer/js/TranscriptChapters.js';

/** The most speakers shown each on a lane of their own; one more and the least active share « Others ». */
export const MAX_SPEAKER_LANES = 5;

/** How many speakers keep a lane of their own once the others are grouped. */
const TOP_SPEAKER_LANES = MAX_SPEAKER_LANES - 1;

/** The steps between two marks of the time scale, in seconds. */
const TICK_STEPS = [10, 15, 30, 60, 120, 300, 600, 900, 1200, 1800, 3600];

/** The most marks the time scale shows, its end aside. */
const MAX_TICKS = 6;

/**
 * How long a recording lasts on its timeline: the length the player read, or
 * else the end of the transcript's last segment.
 *
 * @param {Array<Object>} segments the transcript's segments, each with start
 *          and end
 * @param {number} mediaDuration the length the player read, in seconds, or null
 * @returns {number} the length in seconds, 0 when unknown
 */
export function timelineDuration(segments, mediaDuration) {
  const last = segments?.length && segments[segments.length - 1];
  const transcriptEnd = last && Number(last.end || last.start) || 0;
  return Math.max(Number.isFinite(mediaDuration) ? mediaDuration : 0, transcriptEnd);
}

/**
 * The chapters as spans of the timeline: each from its start to the next
 * one's, the first from the beginning and the last to the end. Chapters that
 * do not fit the transcript give none, as in the transcript.
 *
 * @param {Array<Object>} segments the transcript's segments
 * @param {Array<Object>} chapters the chapters, each with start, index and title
 * @param {number} duration the timeline's length, in seconds
 * @returns {Array<Object>} the spans in order, each with its position, title,
 *          colour (the transcript's), start (where a click plays from), from
 *          and to (where it is drawn)
 */
export function chapterSpans(segments, chapters, duration) {
  const groups = groupByChapters(segments, chapters).filter(group => group.chapter);
  return groups.map((group, position) => ({
    position,
    title: group.chapter.title,
    color: group.color,
    start: group.chapter.start,
    from: position === 0 ? 0 : group.chapter.start,
    to: position + 1 < groups.length ? groups[position + 1].chapter.start : duration,
  }));
}

/**
 * Merges the consecutive segments of one speaker into turns of speech, so that
 * a long call draws a few hundred bars rather than a bar per sentence. A
 * segment with no speaker ends the turn before it and opens none.
 *
 * @param {Array<Object>} segments the transcript's segments, each with start,
 *          end and, when told apart, its speaker's number
 * @returns {Array<Object>} the turns in order, each with speaker, start and end
 */
export function speakerTurns(segments) {
  const turns = [];
  let turn = null;
  (segments || []).forEach(segment => {
    const speaker = segment?.speaker;
    if (!speaker) {
      turn = null;
    } else if (turn?.speaker === speaker) {
      turn.end = Math.max(turn.end, Number(segment.end) || turn.end);
    } else {
      const start = Number(segment.start) || 0;
      turn = {speaker, start, end: Math.max(start, Number(segment.end) || start)};
      turns.push(turn);
    }
  });
  return turns;
}

/**
 * Lays the turns out on lanes, the most active speaker first: one lane per
 * speaker up to MAX_SPEAKER_LANES; beyond, the TOP_SPEAKER_LANES most active
 * keep theirs and the others share one lane, so that « Others » never holds a
 * single person.
 *
 * @param {Array<Object>} turns the turns, as speakerTurns gives them
 * @returns {Array<Object>} the lanes, each with its key, its speaker's number
 *          (null for the shared lane), the speakers it holds, its turns and the
 *          seconds they last
 */
export function speakerLanes(turns) {
  const bySpeaker = new Map();
  (turns || []).forEach(turn => {
    const lane = bySpeaker.get(turn.speaker) || {key: `speaker-${turn.speaker}`, speaker: turn.speaker, speakers: [turn.speaker], turns: [], seconds: 0};
    lane.turns.push(turn);
    lane.seconds += turn.end - turn.start;
    bySpeaker.set(turn.speaker, lane);
  });
  const lanes = [...bySpeaker.values()].sort((a, b) => b.seconds - a.seconds
    || Number.parseInt(a.speaker, 10) - Number.parseInt(b.speaker, 10));
  if (lanes.length <= MAX_SPEAKER_LANES) {
    return lanes;
  }
  const others = lanes.slice(TOP_SPEAKER_LANES);
  return [...lanes.slice(0, TOP_SPEAKER_LANES), {
    key: 'others',
    speaker: null,
    speakers: others.map(lane => lane.speaker),
    turns: others.flatMap(lane => lane.turns).sort((a, b) => a.start - b.start),
    seconds: others.reduce((sum, lane) => sum + lane.seconds, 0),
  }];
}

/**
 * The share of the speaking time of each lane, in whole percents.
 *
 * @param {Array<Object>} lanes the lanes, as speakerLanes gives them
 * @returns {Object} the percent of each lane, by its key
 */
export function talkShares(lanes) {
  const total = (lanes || []).reduce((sum, lane) => sum + lane.seconds, 0);
  return Object.fromEntries((lanes || []).map(lane => [lane.key, total ? Math.round(lane.seconds * 100 / total) : 0]));
}

/**
 * The marks of the time scale: evenly spaced at a round step, and its end. A
 * mark too close to the end to be read beside it is left out.
 *
 * @param {number} duration the timeline's length, in seconds
 * @returns {Array<number>} the moments marked, in seconds, the end included
 */
export function timelineTicks(duration) {
  if (!(duration > 0)) {
    return [];
  }
  const step = TICK_STEPS.find(candidate => duration / candidate <= MAX_TICKS) || Math.ceil(duration / MAX_TICKS / 3600) * 3600;
  const ticks = [];
  for (let moment = 0; moment <= duration - step / 2; moment += step) {
    ticks.push(moment);
  }
  ticks.push(duration);
  return ticks;
}

/**
 * The item of a track playing at a moment: the last one started by then and
 * not yet over.
 *
 * @param {Array<Object>} items the track's items in order, each with start and
 *          end, or from and to
 * @param {number} seconds the moment, in seconds
 * @returns {Object} the item, or null when none plays then
 */
export function itemAt(items, seconds) {
  if (!Number.isFinite(seconds) || !items?.length) {
    return null;
  }
  let low = 0;
  let high = items.length - 1;
  let found = -1;
  while (low <= high) {
    const middle = (low + high) >> 1;
    if (itemFrom(items[middle]) <= seconds) {
      found = middle;
      low = middle + 1;
    } else {
      high = middle - 1;
    }
  }
  const item = found >= 0 && items[found];
  return item && seconds < itemTo(item) ? item : null;
}

/**
 * Where an item begins on its track.
 *
 * @param {Object} item a turn or a chapter span
 * @returns {number} its beginning, in seconds
 */
function itemFrom(item) {
  return item.from ?? item.start;
}

/**
 * Where an item ends on its track.
 *
 * @param {Object} item a turn or a chapter span
 * @returns {number} its end, in seconds
 */
function itemTo(item) {
  return item.to ?? item.end;
}
