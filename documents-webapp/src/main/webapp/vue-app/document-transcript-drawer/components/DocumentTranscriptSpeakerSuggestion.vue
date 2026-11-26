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
    class="documentTranscriptSpeakerSuggestion d-flex flex-column text-caption"
    @click.stop
    @keydown.enter.stop>
    <div class="d-flex align-center flex-wrap">
      <template v-if="applied">
        <span class="text-sub-title me-2">{{ $t('documents.transcript.speaker.suggestion.auto') }}</span>
      </template>
      <template v-else-if="proposal.strong">
        <span class="text-sub-title me-1">{{ $t('documents.transcript.speaker.suggestion.suggested') }}</span>
        <span class="font-weight-bold me-2">{{ proposal.fullName }}</span>
      </template>
      <template v-else-if="premiseSpeaker">
        <span class="text-sub-title me-2">{{ $t('documents.transcript.speaker.suggestion.maybeFrom', {0: proposal.fullName, 1: premiseSpeaker, 2: proposal.premiseFullName}) }}</span>
      </template>
      <template v-else>
        <span class="text-sub-title me-2">{{ $t('documents.transcript.speaker.suggestion.maybe', {0: proposal.fullName}) }}</span>
      </template>
      <template v-if="applied">
        <v-btn
          :disabled="busy || saving"
          :loading="saving"
          class="px-1 me-1"
          color="primary"
          text
          x-small
          @click="$emit('undo', proposal)">
          {{ $t('documents.transcript.speaker.suggestion.undo') }}
        </v-btn>
      </template>
      <template v-else>
        <v-btn
          :disabled="busy || saving"
          :loading="saving"
          class="btn btn-primary me-1"
          x-small
          @click="$emit('confirm', proposal)">
          {{ proposal.strong && $t('documents.transcript.speaker.suggestion.confirm') || $t('documents.transcript.speaker.suggestion.yes') }}
        </v-btn>
        <v-btn
          :disabled="busy || saving"
          class="btn me-1"
          x-small
          @click="$emit('dismiss', proposal)">
          {{ $t('documents.transcript.speaker.suggestion.no') }}
        </v-btn>
      </template>
      <v-btn
        v-if="hasQuotes"
        :aria-expanded="`${why}`"
        class="px-1"
        color="primary"
        text
        x-small
        @click="why = !why">
        {{ $t('documents.transcript.speaker.suggestion.why') }}
        <v-icon size="10" class="ms-1">{{ why && 'fas fa-chevron-up' || 'fas fa-chevron-down' }}</v-icon>
      </v-btn>
    </div>
    <div v-if="why" class="d-flex flex-column mt-1">
      <div
        v-for="(quote, index) in proposal.quotes"
        :key="index"
        :title="$t('documents.transcript.playFrom', {0: formatMoment(quote.start)})"
        class="d-flex align-start clickable border-radius py-1"
        role="button"
        tabindex="0"
        @click="$emit('seek', quote.start)"
        @keydown.enter="$emit('seek', quote.start)">
        <span class="primary--text text-no-wrap me-2">{{ formatMoment(quote.start) }}</span>
        <span>{{ $t('documents.transcript.speaker.suggestion.quote', {0: quote.text}) }}</span>
      </div>
    </div>
  </div>
</template>
<script>
import {formatMoment} from '../js/TranscriptSearch.js';

export default {
  props: {
    proposal: {
      type: Object,
      required: true,
    },
    saving: {
      type: Boolean,
      default: false,
    },
    busy: {
      type: Boolean,
      default: false,
    },
  },
  data: () => ({
    why: false,
  }),
  computed: {
    applied() {
      return !!this.proposal.autoApplied;
    },
    hasQuotes() {
      return !!this.proposal.quotes?.length;
    },
    /**
     * Names the other voice a proposal made by elimination follows from, as
     * the transcript labels it.
     *
     * @returns {string} the other voice's label, or null for a proposal that
     *          follows from no other voice
     */
    premiseSpeaker() {
      return this.proposal.premiseSpeaker && this.proposal.premiseFullName
        && this.$t('documents.transcript.speaker.label', {0: this.proposal.premiseSpeaker})
        || null;
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
