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
    <template v-if="hasSegments">
      <document-transcript-chapter-list
        v-if="hasChapters"
        :groups="groups"
        class="mb-2"
        @select="selectChapter" />
      <div
        v-for="group in groups"
        :key="group.key">
        <document-transcript-chapter
          v-if="group.chapter"
          :ref="group.key"
          :chapter="group.chapter"
          :open="!closed[group.key]"
          @toggle="toggleChapter(group.key)"
          @seek="$emit('seek', $event)" />
        <template v-if="!closed[group.key]">
          <div
            v-for="row in group.rows"
            :key="row.index"
            :ref="`segment-${row.index}`"
            :title="$t('documents.transcript.playFrom', {0: formatMoment(row.segment.start)})"
            :class="row.index === activeIndex && 'light-grey-background-color'"
            class="d-flex align-start py-1 px-2 clickable border-radius"
            role="button"
            tabindex="0"
            @click="$emit('seek', row.segment.start)"
            @keydown.enter="$emit('seek', row.segment.start)">
            <span class="text-caption primary--text text-no-wrap pt-1 me-3">{{ formatMoment(row.segment.start) }}</span>
            <document-transcript-highlight
              :text="row.segment.text"
              :term="term"
              class="text-body-1" />
          </div>
        </template>
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

export default {
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
  },
  data: () => ({
    closed: {},
  }),
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
      return groupByChapters(this.segments, this.chapters);
    },
    hasChapters() {
      return this.groups.some(group => group.chapter);
    },
    activeGroupKey() {
      return this.groupKeyOf(this.activeIndex);
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
    activeGroupKey() {
      this.openChapter(this.activeGroupKey);
    },
    chapters() {
      this.closed = {};
    },
  },
  mounted() {
    this.showActiveSegment();
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
     * Scrolls the sentence of the moment asked for into view, once rendered;
     * the playing sentence is only highlighted, so reading or searching
     * elsewhere is never pulled back.
     * @returns {void}
     */
    showActiveSegment() {
      if (this.shownIndex >= 0) {
        this.openChapter(this.groupKeyOf(this.shownIndex));
        // the sentence exists in the DOM only once the segments are rendered
        this.$nextTick(() => this.$refs[`segment-${this.shownIndex}`]?.[0]?.scrollIntoView({block: 'center'}));
      }
    },
    /**
     * Finds the group a sentence is shown in.
     *
     * @param {number} index the sentence's index, or -1
     * @returns {string} the group's key, or null
     */
    groupKeyOf(index) {
      return index < 0 ? null : this.groups.find(group => group.rows.some(row => row.index === index))?.key || null;
    },
    /**
     * Unfolds a chapter.
     *
     * @param {string} key the chapter's group key, or null
     * @returns {void}
     */
    openChapter(key) {
      if (key && this.closed[key]) {
        this.$set(this.closed, key, false);
      }
    },
    /**
     * Folds or unfolds a chapter.
     *
     * @param {string} key the chapter's group key
     * @returns {void}
     */
    toggleChapter(key) {
      this.$set(this.closed, key, !this.closed[key]);
    },
    /**
     * Unfolds every chapter holding an occurrence of the searched term; the
     * others keep the fold the user chose.
     *
     * @returns {void}
     */
    openMatchingChapters() {
      this.groups
        .filter(group => group.rows.some(row => countOccurrences(row.segment.text, this.term)))
        .forEach(group => this.openChapter(group.key));
    },
    /**
     * Goes to a chapter picked in the list: unfolded, shown, and played from
     * its start.
     *
     * @param {Object} group the chapter's group
     * @returns {void}
     */
    selectChapter(group) {
      this.openChapter(group.key);
      // the heading is in the DOM only once the list of chapters is rendered
      this.$nextTick(() => this.$refs[group.key]?.[0]?.$el?.scrollIntoView({block: 'start'}));
      this.$emit('seek', group.chapter.start);
    },
  },
};
</script>
