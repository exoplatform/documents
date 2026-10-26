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
  <div
    :class="current && 'documentTranscriptChapterCurrent'"
    :style="color && {'--documentTranscriptChapterColor': color}"
    class="documentTranscriptChapter">
    <div class="documentTranscriptChapterHeading">
      <v-btn
        :aria-label="open && $t('documents.transcript.chapters.fold') || $t('documents.transcript.chapters.unfold')"
        :aria-expanded="String(open)"
        x-small
        icon
        @click="$emit('toggle')">
        <v-icon size="12" class="icon-default-color">{{ open && 'fa-chevron-down' || 'fa-chevron-right' }}</v-icon>
      </v-btn>
      <div
        :title="$t('documents.transcript.playFrom', {0: formatMoment(chapter.start)})"
        class="documentTranscriptChapterHeadingText clickable border-radius"
        role="button"
        tabindex="0"
        @click="$emit('seek', chapter.start)"
        @keydown.enter="$emit('seek', chapter.start)">
        <span class="documentTranscriptChapterTime text-caption primary--text text-no-wrap">{{ formatMoment(chapter.start) }}</span>
        <span class="text-subtitle-1 font-weight-bold">{{ chapter.title }}</span>
        <span v-if="chapter.summary" class="documentTranscriptChapterSummary text-caption text-sub-title text-truncate-2">{{ chapter.summary }}</span>
      </div>
    </div>
    <div v-if="open" class="documentTranscriptChapterLines">
      <div
        v-for="row in rows"
        :key="row.index"
        :data-segment-index="row.index"
        :title="$t('documents.transcript.playFrom', {0: formatMoment(row.segment.start)})"
        :class="row.index === activeIndex && 'light-grey-background-color'"
        class="documentTranscriptChapterLine clickable border-radius"
        role="button"
        tabindex="0"
        @click="$emit('seek', row.segment.start)"
        @keydown.enter="$emit('seek', row.segment.start)">
        <template v-if="speakerHeads[row.index]">
          <span class="documentTranscriptLineTime text-caption primary--text text-no-wrap align-self-center mb-1">{{ formatMoment(row.segment.start) }}</span>
          <document-transcript-speaker
            :speaker="row.segment.speaker"
            :person="speakersByNumber[row.segment.speaker]"
            :can-edit="canNameSpeakers"
            :busy="!!savingSpeaker && savingSpeaker !== row.segment.speaker"
            :saving="savingSpeaker === row.segment.speaker"
            class="text-truncate mb-1"
            @name="$emit('name-speaker', $event)"
            @unname="$emit('unname-speaker', $event)" />
          <span aria-hidden="true"></span>
        </template>
        <span v-else class="documentTranscriptLineTime text-caption primary--text text-no-wrap">{{ formatMoment(row.segment.start) }}</span>
        <document-transcript-highlight
          :text="row.segment.text"
          :term="term"
          class="text-body-2" />
      </div>
    </div>
  </div>
</template>
<script>
import {formatMoment} from '../js/TranscriptSearch.js';

export default {
  props: {
    chapter: {
      type: Object,
      required: true,
    },
    rows: {
      type: Array,
      required: true,
    },
    term: {
      type: String,
      default: '',
    },
    activeIndex: {
      type: Number,
      default: -1,
    },
    open: {
      type: Boolean,
      default: false,
    },
    current: {
      type: Boolean,
      default: false,
    },
    /** The chapter's colour, for the guide along its lines; the primary colour when none. */
    color: {
      type: String,
      default: null,
    },
    /** The speakers' people, by speaker number. */
    speakersByNumber: {
      type: Object,
      default: () => ({}),
    },
    /** Whether each line opens a run of its speaker, which then shows beside its moment, by the sentence's index. */
    speakerHeads: {
      type: Array,
      default: () => [],
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
  methods: {
    /**
     * Writes a moment of the recording as m:ss or h:mm:ss.
     *
     * @param {number} seconds the moment, in seconds
     * @returns {string} the written moment
     */
    formatMoment,
  },
};
</script>
