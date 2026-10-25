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
  <div class="d-flex align-start pt-3 pb-1">
    <v-btn
      :aria-label="open && $t('documents.transcript.chapters.fold') || $t('documents.transcript.chapters.unfold')"
      :aria-expanded="String(open)"
      class="me-1"
      icon
      small
      @click="$emit('toggle')">
      <v-icon size="12" class="icon-default-color">{{ open && 'fa-chevron-down' || 'fa-chevron-right' }}</v-icon>
    </v-btn>
    <div
      :title="$t('documents.transcript.playFrom', {0: moment})"
      class="d-flex flex-column flex-grow-1 clickable border-radius px-1"
      role="button"
      tabindex="0"
      @click="$emit('seek', chapter.start)"
      @keydown.enter="$emit('seek', chapter.start)">
      <div class="d-flex align-baseline">
        <span class="text-caption primary--text text-no-wrap me-3">{{ moment }}</span>
        <span class="text-subtitle-1 font-weight-bold">{{ chapter.title }}</span>
      </div>
      <div v-if="chapter.summary" class="text-caption text-sub-title text-truncate-2">{{ chapter.summary }}</div>
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
    open: {
      type: Boolean,
      default: true,
    },
  },
  computed: {
    moment() {
      return formatMoment(this.chapter.start);
    },
  },
};
</script>
