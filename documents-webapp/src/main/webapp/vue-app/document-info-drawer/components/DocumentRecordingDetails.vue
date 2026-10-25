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
  <div v-if="media" class="documentRecordingDetails">
    <v-list-item
      v-if="durationLabel"
      dense
      two-line>
      <v-list-item-content class="pt-0 pb-2">
        <v-list-item-subtitle>{{ $t('documents.drawer.details.duration') }}</v-list-item-subtitle>
        <v-list-item-title>{{ durationLabel }}</v-list-item-title>
      </v-list-item-content>
    </v-list-item>
    <v-list-item
      v-if="state && state.available"
      dense
      two-line>
      <v-list-item-content class="pt-0 pb-2">
        <v-list-item-subtitle>{{ $t('documents.drawer.details.transcript') }}</v-list-item-subtitle>
        <v-list-item-title v-if="ready">
          <v-btn
            class="px-0 text-none font-weight-bold"
            color="primary"
            small
            text
            @click="openTranscript">
            {{ $t('documents.drawer.details.transcript.ready') }}
          </v-btn>
        </v-list-item-title>
        <document-transcript-status
          v-else
          :state="state"
          :retrying="retrying"
          class="text-wrap"
          @retry="retryTranscription" />
      </v-list-item-content>
    </v-list-item>
  </div>
</template>
<script>
import {getState, retry} from '../../../js/TranscriptionService.js';
import {formatRecordingLength, mediaKind} from '../js/RecordingLength.js';

const REFRESH_MILLIS = 15000;

const STALE_MILLIS = 3600000;

export default {
  props: {
    documentId: {
      type: String,
      default: null,
    },
    mimetype: {
      type: String,
      default: null,
    },
    measuredDuration: {
      type: Number,
      default: null,
    },
  },
  data: () => ({
    state: null,
    retrying: false,
    refreshTimeout: null,
  }),
  computed: {
    media() {
      return !!mediaKind(this.mimetype);
    },
    ready() {
      return this.state?.status === 'DONE';
    },
    running() {
      const status = this.state?.status;
      return status === 'QUEUED'
        || (status === 'PROCESSING' && !!this.state.updated && Date.now() - this.state.updated < STALE_MILLIS);
    },
    durationLabel() {
      const stored = this.state?.available && this.state.durationSeconds;
      return formatRecordingLength(stored > 0 ? stored : this.measuredDuration, (key, params) => this.$t(key, params));
    },
  },
  watch: {
    documentId: {
      immediate: true,
      handler() {
        this.state = null;
        this.readState();
      },
    },
  },
  beforeDestroy() {
    this.stopRefresh();
  },
  methods: {
    /**
     * Reads the transcription state of the file, and reads it again every
     * 15 seconds while the transcription moves, and tells whether the
     * transcript can be offered. An answer that comes back for another file
     * than the one shown is dropped, and a failed read shows no transcript
     * row.
     * @returns {Promise} resolved once the state is read
     */
    readState() {
      this.stopRefresh();
      const documentId = this.documentId;
      if (!documentId || !this.media) {
        return Promise.resolve();
      }
      return getState(documentId)
        .then(state => {
          if (documentId === this.documentId) {
            this.state = state;
            this.$emit('available', !!state?.available);
          }
        })
        .catch(() => {
          if (documentId === this.documentId) {
            this.state = null;
            this.$emit('available', false);
          }
        })
        .finally(() => {
          if (documentId === this.documentId && this.running) {
            this.refreshTimeout = window.setTimeout(this.readState, REFRESH_MILLIS);
          }
        });
    },
    /**
     * Stops the pending read of the state, if any.
     * @returns {void}
     */
    stopRefresh() {
      if (this.refreshTimeout) {
        window.clearTimeout(this.refreshTimeout);
        this.refreshTimeout = null;
      }
    },
    /**
     * Asks to transcribe the file, again or for the first time, then reads
     * its new state.
     * @returns {Promise} resolved once the new state is read
     */
    retryTranscription() {
      const documentId = this.documentId;
      this.retrying = true;
      return retry(documentId)
        .catch(() => this.$root.$emit('alert-message', this.$t('documents.transcript.retryError'), 'error'))
        .then(() => documentId === this.documentId && this.readState())
        .finally(() => this.retrying = false);
    },
    /**
     * Opens the transcript drawer of the file.
     * @returns {void}
     */
    openTranscript() {
      document.dispatchEvent(new CustomEvent('documents-open-transcript', {
        detail: {documentId: this.documentId},
        cancelable: true,
      }));
    },
  },
};
</script>
