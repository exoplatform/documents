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
  <div class="documentInfoTranscript">
    <div v-if="done" class="d-flex align-center py-3">
      <v-text-field
        v-model="term"
        :placeholder="$t('documents.transcript.search')"
        :aria-label="$t('documents.transcript.search')"
        prepend-inner-icon="fa-search"
        class="pt-0 mt-0"
        hide-details
        clearable
        outlined
        dense />
      <v-btn
        :title="$t('documents.transcript.copy')"
        :aria-label="$t('documents.transcript.copy')"
        class="ms-2"
        icon
        @click="copy">
        <v-icon size="18">fas fa-copy</v-icon>
      </v-btn>
    </div>
    <v-progress-linear v-if="loading" indeterminate />
    <document-transcript-content
      v-if="done"
      :segments="segments"
      :text="text"
      :term="term"
      :playing-seconds="playingSeconds"
      @seek="$emit('seek', $event)" />
    <document-transcript-status
      v-else-if="state || unreadable"
      :state="state"
      :unreadable="unreadable"
      :retrying="retrying"
      class="py-3"
      @retry="retry" />
  </div>
</template>
<script>
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
  },
  data: () => ({
    state: null,
    text: null,
    segments: null,
    term: '',
    loading: false,
    retrying: false,
    unreadable: false,
    lastRead: 0,
  }),
  computed: {
    done() {
      return this.state?.status === 'DONE' && (this.text !== null || this.segments !== null);
    },
    transcript() {
      return this.done && (this.segments?.length && this.segments.map(segment => segment.text).join('\n') || this.text || '').trim() || '';
    },
  },
  watch: {
    documentId: {
      immediate: true,
      handler() {
        this.state = null;
        this.text = null;
        this.segments = null;
        this.term = '';
        this.refresh();
      },
    },
  },
  methods: {
    /**
     * Reads the transcription state of the file and, once transcribed, its
     * transcript, as timed segments when it has some, else as text. Only the
     * latest read changes what the tab shows.
     * @returns {Promise} resolved once read
     */
    refresh() {
      const documentId = this.documentId;
      if (!documentId) {
        return Promise.resolve();
      }
      const read = ++this.lastRead;
      this.loading = true;
      return this.$transcriptionService.getState(documentId)
        .then(state => {
          if (read !== this.lastRead) {
            return;
          }
          this.unreadable = false;
          this.state = state;
          if (state?.status !== 'DONE') {
            return;
          }
          const segments = state.hasSegments && this.$transcriptionService.getSegments(documentId).catch(() => null) || Promise.resolve(null);
          return segments.then(timedSegments => {
            if (read !== this.lastRead) {
              return;
            }
            if (Array.isArray(timedSegments) && timedSegments.length) {
              this.segments = timedSegments;
              return;
            }
            return this.$transcriptionService.getText(documentId).then(text => {
              if (read === this.lastRead) {
                this.text = text || '';
              }
            });
          });
        })
        .catch(() => {
          if (read === this.lastRead) {
            this.state = null;
            this.unreadable = true;
          }
        })
        .finally(() => {
          if (read === this.lastRead) {
            this.loading = false;
          }
        });
    },
    /**
     * Asks to transcribe the file again, then reads its new state.
     * @returns {void}
     */
    retry() {
      this.retrying = true;
      this.$transcriptionService.retry(this.documentId)
        .then(() => this.refresh())
        .catch(() => this.$root.$emit('alert-message', this.$t('documents.transcript.retryError'), 'error'))
        .finally(() => this.retrying = false);
    },
    /**
     * Copies the whole transcript to the clipboard.
     * @returns {void}
     */
    copy() {
      navigator.clipboard.writeText(this.transcript)
        .then(() => this.$root.$emit('alert-message', this.$t('documents.transcript.copied'), 'success'))
        .catch(() => this.$root.$emit('alert-message', this.$t('documents.transcript.copyError'), 'error'));
    },
  },
};
</script>
