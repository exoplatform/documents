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
    <div class="documentTimelineGrid position-relative">
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
        <document-timeline-speaker
          :key="`${lane.key}-name`"
          :speaker="lane.speaker"
          :person="personOf(lane)"
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
        :style="{'--documentTimelinePlayhead': playhead}"
        class="documentTimelinePlayhead"
        aria-hidden="true"></div>
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
import {chapterSpans, itemAt, speakerLanes, speakerTurns, talkShares, timelineDuration, timelineTicks} from '../js/RecordingTimeline.js';
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
    duration() {
      return timelineDuration(this.segments, this.mediaDuration);
    },
    chapters() {
      return this.duration && chapterSpans(this.segments, this.rawChapters, this.duration) || [];
    },
    turns() {
      return speakerTurns(this.segments);
    },
    lanes() {
      return speakerLanes(this.turns);
    },
    shares() {
      return talkShares(this.lanes);
    },
    ticks() {
      return timelineTicks(this.duration);
    },
    shown() {
      return this.duration > 0 && (this.chapters.length > 0 || this.lanes.length > 0);
    },
    speakersByNumber() {
      return Object.fromEntries((this.speakers || []).map(person => [person.speaker, person]));
    },
    /**
     * The blocks of the chapters, in the colours the transcript gives them:
     * every other block is a light tint of its colour with dark text, so two
     * chapters side by side differ by lightness and not by hue alone.
     * @returns {Array<Object>} the chapters' items
     */
    chapterItems() {
      return this.chapters.map(chapter => ({
        ...chapter,
        text: chapter.position % 2 === 0 && 'white--text' || null,
        style: {backgroundColor: chapter.position % 2 === 0 ? chapter.color : `${chapter.color}33`},
        key: `chapter-${chapter.position}`,
        caption: chapter.title,
        title: `${chapter.title} · ${formatMoment(chapter.from)}–${formatMoment(chapter.to)}`,
        label: this.$t('documents.timeline.chapter', {0: chapter.title, 1: formatMoment(chapter.from), 2: formatMoment(chapter.to)}),
      }));
    },
    /**
     * The bars of each lane, computed once per change of the transcript or of
     * the names rather than on each time update of the player.
     * @returns {Object} the items of each lane, by its key
     */
    laneItems() {
      return Object.fromEntries(this.lanes.map(lane => [lane.key, this.turnItems(lane)]));
    },
    currentChapter() {
      return itemAt(this.chapters, this.playingSeconds);
    },
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
      handler(shown) {
        this.$emit('shown', shown);
      },
    },
  },
  methods: {
    formatMoment,
    /**
     * The person a lane's speaker is, once someone named them.
     * @param {Object} lane the lane
     * @returns {Object} the person, or null for an unnamed speaker or « Others »
     */
    personOf(lane) {
      return lane.speaker && this.speakersByNumber[lane.speaker] || null;
    },
    /**
     * The name of a speaker: the person someone named, else « Speaker N ».
     * @param {string} speaker the speaker's number
     * @returns {string} the name
     */
    speakerName(speaker) {
      return this.speakersByNumber[speaker]?.fullName || this.$t('documents.transcript.speaker.label', {0: speaker});
    },
    /**
     * The name of a lane: its speaker's, or « Others (N) » for the shared one.
     * @param {Object} lane the lane
     * @returns {string} the name
     */
    laneName(lane) {
      return lane.speaker && this.speakerName(lane.speaker) || this.$t('documents.timeline.others', {0: lane.speakers.length});
    },
    /**
     * The key of a turn, unique on the timeline.
     * @param {Object} turn the turn
     * @returns {string} the key
     */
    turnKey(turn) {
      return `${turn.speaker}-${turn.start}`;
    },
    /**
     * The bars of a lane, one per turn, coloured as their speaker in the
     * transcript; the shared lane's bars are grey, each named in its label.
     * @param {Object} lane the lane
     * @returns {Array<Object>} the lane's items
     */
    turnItems(lane) {
      const background = lane.speaker && speakerColor(lane.speaker) || 'grey';
      return lane.turns.map(turn => {
        const name = this.speakerName(turn.speaker);
        const span = `${formatMoment(turn.start)}–${formatMoment(turn.end)}`;
        return {
          key: this.turnKey(turn),
          start: turn.start,
          from: turn.start,
          to: turn.end,
          background,
          title: `${name} · ${span}`,
          label: this.$t('documents.timeline.turn', {0: name, 1: formatMoment(turn.start), 2: formatMoment(turn.end)}),
        };
      });
    },
  },
};
</script>
