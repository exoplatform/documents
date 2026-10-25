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
    v-if="compact"
    :title="tooltip"
    class="text-truncate">
    {{ compactMessage }}<template v-if="canRetry">
      ·
      <button
        :disabled="retrying"
        type="button"
        class="font-weight-bold primary--text pa-0"
        @click="$emit('retry')">
        {{ $t('documents.transcript.retry') }}
      </button>
    </template>
  </div>
  <div v-else class="d-flex flex-column">
    <div class="text-body-1">{{ message }}</div>
    <div v-if="reason" class="text-subtitle mt-1">{{ reason }}</div>
    <div v-if="longQueued" class="text-subtitle mt-1">{{ $t('documents.transcript.status.longQueued') }}</div>
    <v-progress-linear
      v-if="running"
      :value="progressValue"
      :indeterminate="!progressValue"
      color="primary"
      class="mt-3"
      rounded />
    <div v-if="canRetry" class="mt-4">
      <v-btn
        :loading="retrying"
        class="btn btn-primary"
        @click="$emit('retry')">
        {{ status && $t('documents.transcript.retry') || $t('documents.transcript.transcribe') }}
      </v-btn>
    </div>
  </div>
</template>
<script>
const STALE_MILLIS = 3600000;

export default {
  props: {
    state: {
      type: Object,
      default: null,
    },
    unreadable: {
      type: Boolean,
      default: false,
    },
    retrying: {
      type: Boolean,
      default: false,
    },
    compact: {
      type: Boolean,
      default: false,
    },
  },
  computed: {
    status() {
      return this.state?.status || null;
    },
    stale() {
      return this.status === 'PROCESSING'
        && (!this.state.updated || Date.now() - this.state.updated >= STALE_MILLIS);
    },
    longQueued() {
      return this.status === 'QUEUED' && !this.state.error
        && (!this.state.updated || Date.now() - this.state.updated >= STALE_MILLIS);
    },
    rescheduled() {
      return this.status === 'QUEUED' && !!this.state.error;
    },
    running() {
      return this.status === 'QUEUED' || (this.status === 'PROCESSING' && !this.stale);
    },
    canRetry() {
      return !this.unreadable && (!this.status || this.status === 'FAILED' || this.stale || this.longQueued);
    },
    progressParts() {
      const parts = (this.state?.progress || '').split('/').map(Number);
      return parts.length === 2 && parts.every(Number.isFinite) && parts[1] > 0 && parts || null;
    },
    progressValue() {
      return this.status === 'PROCESSING' && this.progressParts && Math.round(100 * this.progressParts[0] / this.progressParts[1]) || 0;
    },
    message() {
      if (this.unreadable) {
        return this.$t('documents.transcript.status.unreadable');
      } else if (!this.status) {
        return this.$t('documents.transcript.status.none');
      } else if (this.status === 'FAILED') {
        return this.$t('documents.transcript.status.failed');
      } else if (this.stale) {
        return this.$t('documents.transcript.status.stopped');
      } else if (this.rescheduled) {
        return this.$t('documents.transcript.status.rescheduled');
      } else if (this.status === 'PROCESSING') {
        return this.progressParts
          && this.$t('documents.transcript.status.progress', {0: this.progressParts[0], 1: this.progressParts[1]})
          || this.$t('documents.transcript.status.processing');
      } else {
        return this.$t('documents.transcript.status.queued');
      }
    },
    /**
     * Says the state in a few words, for a one-line row: the progress as
     * parts transcribed, or how the transcription ended.
     * @returns {string} the short message
     */
    compactMessage() {
      if (this.unreadable) {
        return this.$t('documents.transcript.status.unreadable');
      } else if (this.status === 'FAILED') {
        return this.$t('documents.transcript.compact.failed');
      } else if (this.stale) {
        return this.$t('documents.transcript.compact.stopped');
      } else if (this.rescheduled) {
        return this.$t('documents.transcript.compact.rescheduled');
      } else if (this.status === 'PROCESSING') {
        return this.progressParts
          && this.$t('documents.transcript.compact.progress', {0: this.progressParts[0], 1: this.progressParts[1]})
          || this.$t('documents.transcript.compact.processing');
      } else {
        return this.$t('documents.transcript.compact.queued');
      }
    },
    /**
     * Gives the one-line row's hover the full message, and the reason of a
     * failure.
     * @returns {string} the message, then the reason when there is one
     */
    tooltip() {
      return [this.message, this.reason, this.longQueued && this.$t('documents.transcript.status.longQueued')].filter(Boolean).join(' ');
    },
    reason() {
      if (this.unreadable || !(this.status === 'FAILED' || this.rescheduled)) {
        return null;
      }
      const key = `documents.transcript.error.${this.state.error}`;
      return this.$t('documents.transcript.reason', {0: this.state.error && this.$te(key) && this.$t(key) || this.$t('documents.transcript.error.unknown')});
    },
  },
};
</script>
