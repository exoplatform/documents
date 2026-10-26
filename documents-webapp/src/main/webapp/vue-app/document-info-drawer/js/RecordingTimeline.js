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
import {formatMoment} from '../../document-transcript-drawer/js/TranscriptSearch.js';

/** The most speakers shown each on a lane of their own; one more and the least active share « Others ». */
export const MAX_SPEAKER_LANES = 5;

/** How many speakers keep a lane of their own once the others are grouped. */
const TOP_SPEAKER_LANES = MAX_SPEAKER_LANES - 1;

/** The steps between two marks of the time scale, in seconds. */
const TICK_STEPS = [10, 15, 30, 60, 120, 300, 600, 900, 1200, 1800, 3600];

/** The most marks the time scale shows, its end aside. */
const MAX_TICKS = 6;

/** The least contrast of a caption with its background, as WCAG AA asks of normal text. */
const MIN_TEXT_CONTRAST = 4.5;

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
 * Lays the turns out on lanes, the most active first: one lane per key, a key
 * being a speaker or the person several speakers are, up to
 * MAX_SPEAKER_LANES; beyond, the TOP_SPEAKER_LANES most active keep theirs and
 * the others share one lane, so that « Others » never holds a single one.
 *
 * @param {Array<Object>} turns the turns, as speakerTurns gives them
 * @param {Function} keyOf gives the key of a speaker's number; by default, one
 *          lane per speaker
 * @returns {Array<Object>} the lanes, each with its key, the number of its
 *          first speaker (null for the shared lane), the speakers it holds, how
 *          many lanes it groups, its turns and the seconds they last
 */
export function speakerLanes(turns, keyOf = speaker => `speaker-${speaker}`) {
  const byKey = new Map();
  (turns || []).forEach(turn => {
    const key = keyOf(turn.speaker);
    const lane = byKey.get(key) || {key, speaker: turn.speaker, speakers: [], count: 1, turns: [], seconds: 0};
    if (!lane.speakers.includes(turn.speaker)) {
      lane.speakers.push(turn.speaker);
    }
    lane.turns.push(turn);
    lane.seconds += turn.end - turn.start;
    byKey.set(key, lane);
  });
  const lanes = [...byKey.values()].sort((a, b) => b.seconds - a.seconds
    || Number.parseInt(a.speaker, 10) - Number.parseInt(b.speaker, 10));
  if (lanes.length <= MAX_SPEAKER_LANES) {
    return lanes;
  }
  const others = lanes.slice(TOP_SPEAKER_LANES);
  return [...lanes.slice(0, TOP_SPEAKER_LANES), {
    key: 'others',
    speaker: null,
    speakers: others.flatMap(lane => lane.speakers),
    count: others.length,
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
 * The tooltip and the accessible label of a block or a bar.
 *
 * @param {string} name what it shows: a chapter's title or a speaker's name
 * @param {string} label its accessible label
 * @param {number} from where it begins, in seconds
 * @param {number} to where it ends, in seconds
 * @returns {{title: string, label: string}} its tooltip and its label
 */
export function itemDescription(name, label, from, to) {
  return {title: `${name} · ${formatMoment(from)}–${formatMoment(to)}`, label};
}

/**
 * The blocks of the chapters on their track, in the colours the transcript
 * gives them: every other block is a light tint of its colour, so two
 * chapters side by side differ by lightness and not by hue alone. A block's
 * caption is white where white reads on its colour, dark otherwise.
 *
 * @param {Array<Object>} chapters the spans, as chapterSpans gives them
 * @param {Function} describe gives a chapter's tooltip and accessible label,
 *          as {title, label}
 * @returns {Array<Object>} the track's items
 */
export function chapterItems(chapters, describe) {
  return (chapters || []).map(chapter => {
    const solid = chapter.position % 2 === 0;
    return {
      ...chapter,
      ...describe(chapter),
      key: `chapter-${chapter.position}`,
      caption: chapter.title,
      text: solid && readsWhite(chapter.color) && 'white--text' || null,
      style: {backgroundColor: solid ? chapter.color : `${chapter.color}33`},
    };
  });
}

/**
 * The bars of a lane on its track, one per turn of speech.
 *
 * @param {Object} lane the lane, as speakerLanes gives it
 * @param {string} background the theme colour of its bars
 * @param {Function} describe gives a turn's tooltip and accessible label,
 *          as {title, label}
 * @returns {Array<Object>} the track's items
 */
export function turnItems(lane, background, describe) {
  return lane.turns.map(turn => ({
    ...describe(turn),
    key: turnKey(turn),
    start: turn.start,
    from: turn.start,
    to: turn.end,
    background,
  }));
}

/**
 * The key of a turn, unique on the timeline.
 *
 * @param {Object} turn the turn
 * @returns {string} the key
 */
export function turnKey(turn) {
  return `${turn.speaker}-${turn.start}`;
}

/**
 * Tells whether white text reads on a colour, by the contrast of their
 * relative luminances.
 *
 * @param {string} color the colour, as #rrggbb
 * @returns {boolean} true when white text on it has a contrast of 4.5 or more
 */
export function readsWhite(color) {
  const channels = /^#([0-9a-f]{2})([0-9a-f]{2})([0-9a-f]{2})$/i.exec(color || '');
  if (!channels) {
    return false;
  }
  const [red, green, blue] = channels.slice(1).map(channel => {
    const value = Number.parseInt(channel, 16) / 255;
    return value <= 0.03928 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4;
  });
  const luminance = 0.2126 * red + 0.7152 * green + 0.0722 * blue;
  return 1.05 / (luminance + 0.05) >= MIN_TEXT_CONTRAST;
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
