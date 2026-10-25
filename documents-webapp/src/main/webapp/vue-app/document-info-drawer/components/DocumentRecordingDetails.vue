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
    <documents-info-row
      v-if="durationLabel"
      :label="$t('documents.drawer.details.duration')"
      :value="durationLabel" />
    <documents-info-row
      v-if="transcribed"
      :label="$t('documents.drawer.details.transcript')">
      <button
        v-if="ready"
        type="button"
        class="font-weight-bold primary--text"
        @click="openTranscript">
        {{ $t('documents.drawer.details.transcript.read') }}
      </button>
      <document-transcript-status
        v-else
        :state="state"
        :retrying="retrying"
        compact
        @retry="retryTranscription" />
    </documents-info-row>
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
    lastRead: 0,
    destroyed: false,
  }),
  computed: {
    media() {
      return !!mediaKind(this.mimetype);
    },
    /**
     * Shows the transcript row only for a recording whose transcription has
     * started, whatever became of it: a recording never transcribed gets
     * none, its transcription being offered in the transcript drawer.
     * @returns {boolean} true when the row shows
     */
    transcribed() {
      return !!this.state?.available && !!this.state.status;
    },
    ready() {
      return this.state?.status === 'DONE';
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
  created() {
    document.addEventListener('documents-transcript-drawer-closed', this.readStateOnDrawerClosed);
  },
  beforeDestroy() {
    document.removeEventListener('documents-transcript-drawer-closed', this.readStateOnDrawerClosed);
    this.destroyed = true;
    this.stopRefresh();
  },
  methods: {
    /**
     * Reads the transcription state of the file, and reads it again every
     * 15 seconds while the transcription moves, and tells whether the
     * transcript can be offered. An answer that comes back for another file
     * than the one shown, or after a later read was sent, is dropped, so a
     * single chain of reads runs; a failed read shows no transcript row.
     * @returns {Promise} resolved once the state is read
     */
    readState() {
      this.stopRefresh();
      const documentId = this.documentId;
      const read = ++this.lastRead;
      if (!documentId || !this.media) {
        return Promise.resolve();
      }
      return getState(documentId)
        .then(state => {
          if (this.isCurrent(documentId) && read === this.lastRead) {
            this.state = state;
            this.$emit('available', !!state?.available);
            if (this.isRunning(state)) {
              this.refreshTimeout = window.setTimeout(this.readState, REFRESH_MILLIS);
            }
          }
        })
        .catch(() => {
          if (this.isCurrent(documentId) && read === this.lastRead) {
            this.state = null;
            this.$emit('available', false);
          }
        });
    },
    /**
     * Reads the state again once the transcript drawer of the same file
     * closes, since a transcription may have been asked from there.
     * @param {CustomEvent} event its detail holds documentId
     * @returns {void}
     */
    readStateOnDrawerClosed(event) {
      if (!this.destroyed && event?.detail?.documentId === this.documentId) {
        this.readState();
      }
    },
    /**
     * Tells whether an answer read for a file still concerns the rows shown:
     * the rows are not destroyed, as when the drawer closes while a read is
     * in flight, and still show that file.
     * @param {string} documentId the file the answer was read for
     * @returns {boolean} true when the answer is to be shown
     */
    isCurrent(documentId) {
      return !this.destroyed && documentId === this.documentId;
    },
    /**
     * Tells whether a transcription still moves: queued, or being transcribed
     * with an update less than an hour ago, as the transcript drawer counts it.
     * @param {Object} state the transcription state
     * @returns {boolean} true while the state is worth reading again
     */
    isRunning(state) {
      const status = state?.status;
      return status === 'QUEUED'
        || (status === 'PROCESSING' && !!state.updated && Date.now() - state.updated < STALE_MILLIS);
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
        .then(() => this.isCurrent(documentId) && this.readState())
        .finally(() => this.retrying = false);
    },
    /**
     * Asks the drawer showing these rows to show the transcript: in its tab
     * when expanded, else in the transcript drawer.
     * @returns {void}
     */
    openTranscript() {
      this.$emit('transcript');
    },
  },
};
</script>
