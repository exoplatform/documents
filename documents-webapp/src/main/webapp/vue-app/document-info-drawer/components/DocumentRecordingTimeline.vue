<!--
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
-->
<template>
  <section
    v-if="shown"
    :aria-label="$t('documents.timeline.label')"
    class="documentRecordingTimeline white border-radius pa-4">
    <div class="d-flex align-baseline flex-wrap mb-2">
      <h3 class="text-subtitle-1 font-weight-bold me-3">{{ $t('documents.timeline.title') }}</h3>
      <span class="text-caption text-sub-title">{{ $t('documents.timeline.hint') }}</span>
    </div>
    <div :style="{gridTemplateRows: `repeat(${rows}, auto)`}" class="documentTimelineGrid">
      <span></span>
      <div class="documentTimelineScale position-relative text-caption text-sub-title" aria-hidden="true">
        <span
          v-for="tick in ticks"
          :key="tick"
          :style="{insetInlineStart: `${tick * 100 / duration}%`}"
          class="documentTimelineTick">
          {{ formatMoment(tick) }}
        </span>
      </div>
      <template v-if="chapters.length">
        <span class="text-caption font-weight-bold text-truncate">{{ $t('documents.timeline.chapters') }}</span>
        <document-timeline-track
          :items="chapterItems"
          :duration="duration"
          :label="$t('documents.timeline.chapters')"
          :active-key="currentChapter && `chapter-${currentChapter.position}`"
          blocks
          @seek="$emit('seek', $event)" />
      </template>
      <template v-for="lane in lanes">
        <document-transcript-speaker
          v-if="lane.speaker"
          :key="`${lane.key}-name`"
          :speaker="lane.speaker"
          :person="personOf(lane)"
          :can-edit="canNameSpeakers"
          :busy="!!savingSpeaker && savingSpeaker !== lane.speaker"
          :saving="savingSpeaker === lane.speaker"
          class="text-caption font-weight-bold overflow-hidden"
          @name="nameSpeaker"
          @unname="unnameSpeaker" />
        <document-timeline-speaker
          v-else
          :key="`${lane.key}-name`"
          :name="laneName(lane)" />
        <document-timeline-track
          :key="lane.key"
          :items="laneItems[lane.key]"
          :duration="duration"
          :label="laneName(lane)"
          :active-key="currentTurn && lane.speakers.includes(currentTurn.speaker) && turnKey(currentTurn) || null"
          @seek="$emit('seek', $event)" />
      </template>
      <div
        v-if="playhead !== null"
        class="documentTimelineNow"
        aria-hidden="true">
        <div :style="{insetInlineStart: `${playhead * 100}%`}" class="documentTimelinePlayhead"></div>
      </div>
    </div>
    <div class="d-flex flex-wrap text-caption text-sub-title mt-2">
      <span v-if="currentChapter || currentTurn" class="me-5">
        {{ $t('documents.timeline.now') }}
        <strong v-if="currentChapter">{{ currentChapter.title }}</strong>
        <template v-if="currentChapter && currentTurn"> · </template>
        <template v-if="currentTurn">{{ $t('documents.timeline.speaking', {0: speakerName(currentTurn.speaker)}) }}</template>
      </span>
      <span v-if="lanes.length">
        {{ $t('documents.timeline.talkTime') }}
        {{ lanes.map(lane => $t('documents.timeline.share', {0: laneName(lane), 1: shares[lane.key]})).join(' · ') }}
      </span>
    </div>
  </section>
</template>
<script>
import {chapterItems, chapterSpans, itemAt, speakerLanes, speakerTurns, talkShares, timelineDuration, timelineTicks, turnItems, turnKey} from '../js/RecordingTimeline.js';
import {speakerColor} from '../../document-transcript-drawer/js/TranscriptSpeakers.js';
import RecordingTimelineReader from '../js/RecordingTimelineReader.js';
import {formatMoment} from '../../document-transcript-drawer/js/TranscriptSearch.js';

export default {
  props: {
    documentId: {
      type: String,
      default: null,
    },
    playingSeconds: {
      type: Number,
      default: null,
    },
    mediaDuration: {
      type: Number,
      default: null,
    },
  },
  mixins: [RecordingTimelineReader],
  computed: {
    /** @returns {number} how long the recording lasts on the timeline, in seconds */
    duration() {
      return timelineDuration(this.segments, this.mediaDuration);
    },
    /** @returns {Array<Object>} the chapters as spans of the timeline, none when the transcript shows none */
    chapters() {
      return this.duration && chapterSpans(this.segments, this.rawChapters, this.duration) || [];
    },
    /** @returns {Array<Object>} the turns of speech, consecutive segments of one speaker merged */
    turns() {
      return speakerTurns(this.segments);
    },
    /**
     * The speakers' lanes, one per person: a person named for two speakers
     * holds both on one lane, as in the transcript; the least active are
     * grouped beyond five.
     * @returns {Array<Object>} the lanes
     */
    lanes() {
      return speakerLanes(this.turns, speaker => {
        const identityId = this.speakersByNumber[speaker]?.identityId;
        return identityId ? `person-${identityId}` : `speaker-${speaker}`;
      });
    },
    /** @returns {Object} each lane's share of the speaking time, in percents, by its key */
    shares() {
      return talkShares(this.lanes);
    },
    /** @returns {Array<number>} the moments the time scale marks */
    ticks() {
      return timelineTicks(this.duration);
    },
    /**
     * How many rows the timeline has: the time scale, the chapters when
     * there are some, and the lanes; the line marking the current moment
     * spans all but the scale.
     * @returns {number} the count of rows
     */
    rows() {
      return 1 + (this.chapters.length ? 1 : 0) + this.lanes.length;
    },
    /** @returns {boolean} true when the recording has chapters or speakers to show */
    shown() {
      return this.duration > 0 && (this.chapters.length > 0 || this.lanes.length > 0);
    },
    /** @returns {Array<Object>} the chapters' blocks, in the transcript's colours */
    chapterItems() {
      return chapterItems(this.chapters, chapter => this.describe(chapter.title, this.$t('documents.timeline.chapter', {
        0: chapter.title,
        1: formatMoment(chapter.from),
        2: formatMoment(chapter.to),
      }), chapter.from, chapter.to));
    },
    /**
     * The bars of each lane, computed once per change of the transcript or of
     * the names rather than on each time update of the player; the shared
     * lane's bars are grey, each named in its label.
     * @returns {Object} the items of each lane, by its key
     */
    laneItems() {
      return Object.fromEntries(this.lanes.map(lane => [lane.key, turnItems(lane, lane.speaker && speakerColor(lane.speaker) || 'grey', turn => {
        const name = this.speakerName(turn.speaker);
        return this.describe(name, this.$t('documents.timeline.turn', {0: name, 1: formatMoment(turn.start), 2: formatMoment(turn.end)}), turn.start, turn.end);
      })]));
    },
    /** @returns {Object} the chapter at the current moment, or null */
    currentChapter() {
      return itemAt(this.chapters, this.playingSeconds);
    },
    /** @returns {Object} the turn of speech at the current moment, or null */
    currentTurn() {
      return itemAt(this.turns, this.playingSeconds);
    },
    /**
     * Where the line marking the current moment stands, as a share of the
     * recording; it follows the player's time updates.
     * @returns {number} a number from 0 to 1, or null before playback reports one
     */
    playhead() {
      return Number.isFinite(this.playingSeconds) && this.duration ? Math.min(Math.max(this.playingSeconds / this.duration, 0), 1) : null;
    },
  },
  watch: {
    shown: {
      immediate: true,
      /**
       * Tells the full view whether the timeline shows, for its layout.
       * @param {boolean} shown whether the timeline shows
       * @returns {void}
       */
      handler(shown) {
        this.$emit('shown', shown);
      },
    },
  },
  methods: {
    formatMoment,
    turnKey,
    /**
     * The tooltip and the accessible label of a block or a bar.
     * @param {string} name what it shows: a chapter's title or a speaker's name
     * @param {string} label its accessible label
     * @param {number} from where it begins, in seconds
     * @param {number} to where it ends, in seconds
     * @returns {{title: string, label: string}} its tooltip and its label
     */
    describe(name, label, from, to) {
      return {title: `${name} · ${formatMoment(from)}–${formatMoment(to)}`, label};
    },
    /**
     * Shows a message to the user, as the Transcript tab does.
     * @param {string} message the message
     * @param {string} type its kind: success or error
     * @returns {void}
     */
    alert(message, type) {
      document.dispatchEvent(new CustomEvent('alert-message', {detail: {alertMessage: message, alertType: type}}));
    },
  },
};
</script>
