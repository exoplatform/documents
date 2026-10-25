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
  <div v-if="empty" class="text-body-1">
    {{ $t('documents.transcript.noSpeech') }}
  </div>
  <div v-else class="d-flex flex-column">
    <div v-if="term" class="text-subtitle mb-3">
      {{ occurrences && $t('documents.transcript.occurrences', {0: occurrences}) || $t('documents.transcript.noOccurrence') }}
    </div>
    <template v-if="hasChapters">
      <document-transcript-chapter-list
        :groups="groups"
        class="mb-2"
        @select="selectChapter" />
      <document-transcript-chapter
        v-for="group in groups"
        :key="group.key"
        :ref="group.key"
        :chapter="group.chapter"
        :rows="group.rows"
        :term="term"
        :active-index="activeIndex"
        :open="!!opened[group.key]"
        :current="group.key === activeGroupKey"
        :color="group.color"
        @toggle="toggleChapter(group.key)"
        @seek="$emit('seek', $event)" />
    </template>
    <template v-else-if="hasSegments">
      <div
        v-for="(segment, index) in segments"
        :key="index"
        :data-segment-index="index"
        :title="$t('documents.transcript.playFrom', {0: formatMoment(segment.start)})"
        :class="index === activeIndex && 'light-grey-background-color'"
        class="d-flex flex-column py-1 px-2 clickable border-radius"
        role="button"
        tabindex="0"
        @click="$emit('seek', segment.start)"
        @keydown.enter="$emit('seek', segment.start)">
        <div v-if="speakerHeads[index]" class="d-flex align-center mb-1">
          <span class="text-caption primary--text text-no-wrap me-3">{{ formatMoment(segment.start) }}</span>
          <document-transcript-speaker
            :speaker="segment.speaker"
            :person="speakersByNumber[segment.speaker]"
            :can-edit="canNameSpeakers"
            :busy="!!savingSpeaker && savingSpeaker !== segment.speaker"
            :saving="savingSpeaker === segment.speaker"
            class="text-truncate"
            @name="$emit('name-speaker', $event)"
            @unname="$emit('unname-speaker', $event)" />
        </div>
        <div class="d-flex align-start">
          <span
            :class="speakerHeads[index] && 'invisible'"
            :aria-hidden="speakerHeads[index] && 'true' || null"
            class="text-caption primary--text text-no-wrap pt-1 me-3">{{ formatMoment(segment.start) }}</span>
          <document-transcript-highlight
            :text="segment.text"
            :term="term"
            class="text-body-1 flex-grow-1" />
        </div>
      </div>
    </template>
    <template v-else>
      <document-transcript-highlight
        v-for="(paragraph, index) in paragraphs"
        :key="index"
        :text="paragraph"
        :term="term"
        class="text-body-1 mb-3 d-block" />
    </template>
  </div>
</template>
<script>
import {countOccurrences, formatMoment} from '../js/TranscriptSearch.js';
import {groupByChapters} from '../js/TranscriptChapters.js';
import TranscriptChapterFolds from '../js/TranscriptChapterFolds.js';
import {opensSpeakerRun} from '../js/TranscriptSpeakers.js';

export default {
  mixins: [TranscriptChapterFolds],
  props: {
    segments: {
      type: Array,
      default: null,
    },
    text: {
      type: String,
      default: null,
    },
    term: {
      type: String,
      default: '',
    },
    seconds: {
      type: Number,
      default: null,
    },
    playingSeconds: {
      type: Number,
      default: null,
    },
    chapters: {
      type: Array,
      default: null,
    },
    durationSeconds: {
      type: Number,
      default: null,
    },
    speakers: {
      type: Array,
      default: null,
    },
    canNameSpeakers: {
      type: Boolean,
      default: false,
    },
    savingSpeaker: {
      type: String,
      default: null,
    },
  },
  computed: {
    hasSegments() {
      return !!this.segments?.length;
    },
    paragraphs() {
      return (this.text || '').split(/\n+/).map(paragraph => paragraph.trim()).filter(paragraph => paragraph.length);
    },
    empty() {
      return !this.hasSegments && !this.paragraphs.length;
    },
    occurrences() {
      const texts = this.hasSegments && this.segments.map(segment => segment.text) || this.paragraphs;
      return texts.reduce((count, text) => count + countOccurrences(text, this.term), 0);
    },
    activeIndex() {
      return this.indexAt(this.playingSeconds === null ? this.seconds : this.playingSeconds);
    },
    shownIndex() {
      return this.indexAt(this.seconds);
    },
    groups() {
      return groupByChapters(this.segments, this.chapters, this.durationSeconds);
    },
    hasChapters() {
      return this.groups.some(group => group.chapter);
    },
    speakersByNumber() {
      return Object.fromEntries((this.speakers || []).map(person => [person.speaker, person]));
    },
    speakerKey() {
      // one person named for two numbers is one run; a person and a number never compare equal
      return speaker => {
        const identityId = this.speakersByNumber[speaker]?.identityId;
        return identityId ? `person:${identityId}` : `speaker:${speaker}`;
      };
    },
    /**
     * Tells, sentence by sentence, whether it opens a run of its speaker, the
     * speaker then showing on its first line, beside its moment.
     *
     * @returns {Array<boolean>} true for each sentence that shows its speaker
     */
    speakerHeads() {
      return (this.segments || []).map((segment, index) => opensSpeakerRun(this.segments, index, false, this.speakerKey));
    },
  },
  watch: {
    term() {
      this.openMatchingChapters();
      // the highlighted occurrences exist in the DOM only once the new term is rendered
      this.$nextTick(() => this.$el?.querySelector?.('mark')?.scrollIntoView({block: 'center'}));
    },
    shownIndex() {
      this.showActiveSegment();
    },
  },
  mounted() {
    this.applyFolds();
    this.showActiveSegment();
    // the occurrences of a term already set exist in the DOM only once its chapters are unfolded
    this.$nextTick(this.revealCurrent);
  },
  methods: {
    /**
     * Writes a moment of the recording as m:ss or h:mm:ss.
     *
     * @param {number} seconds the moment, in seconds
     * @returns {string} the written moment
     */
    formatMoment,
    /**
     * Finds the sentence a moment of the recording falls in.
     *
     * @param {number} seconds the moment, in seconds, or null
     * @returns {number} the sentence's index, or -1
     */
    indexAt(seconds) {
      if (!this.hasSegments || seconds === null) {
        return -1;
      }
      let index = -1;
      this.segments.forEach((segment, position) => {
        if (segment.start <= seconds) {
          index = position;
        }
      });
      return index;
    },
    /**
     * Scrolls the sentence of the moment asked for into view, once rendered,
     * unfolding its chapter; the playing sentence is only highlighted, so
     * reading or searching elsewhere is never pulled back. The start of the
     * recording unfolds nothing: the chapters are folded on open.
     * @returns {void}
     */
    showActiveSegment() {
      if (this.shownIndex >= 0) {
        if (this.seconds > 0) {
          this.openChapter(this.groupKeyOf(this.shownIndex));
        }
        // the sentence exists in the DOM only once its chapter is unfolded and rendered
        this.$nextTick(() => this.$el?.querySelector?.(`[data-segment-index="${this.shownIndex}"]`)?.scrollIntoView({block: 'center'}));
      }
    },
  },
};
</script>
