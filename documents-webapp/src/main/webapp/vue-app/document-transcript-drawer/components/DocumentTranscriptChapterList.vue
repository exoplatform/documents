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
  <nav :aria-label="$t('documents.transcript.chapters.title')" class="d-flex flex-column">
    <div class="text-subtitle-2 font-weight-bold mb-1">{{ $t('documents.transcript.chapters.title') }}</div>
    <div
      v-for="group in groups"
      :key="group.key"
      :title="$t('documents.transcript.playFrom', {0: formatMoment(group.chapter.start)})"
      class="documentTranscriptChapterListItem clickable border-radius"
      role="button"
      tabindex="0"
      @click="$emit('select', group)"
      @keydown.enter="$emit('select', group)">
      <span :style="group.color && {backgroundColor: group.color}" class="documentTranscriptChapterDot"></span>
      <span class="documentTranscriptChapterTime text-caption primary--text text-no-wrap">{{ formatMoment(group.chapter.start) }}</span>
      <span class="text-body-2 text-truncate">{{ group.chapter.title }}</span>
    </div>
  </nav>
</template>
<script>
import {formatMoment} from '../js/TranscriptSearch.js';

export default {
  props: {
    groups: {
      type: Array,
      required: true,
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
