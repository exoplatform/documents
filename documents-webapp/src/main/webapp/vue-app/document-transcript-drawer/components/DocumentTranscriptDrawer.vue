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
  <exo-drawer
    ref="drawer"
    v-model="drawer"
    :loading="loading"
    :filter-placeholder="$t('documents.transcript.search')"
    :class="aboveDialog && 'z-index-snackbar'"
    class="documentTranscriptDrawer"
    allow-expand
    use-filter
    right
    @filter-updated="term = $event"
    @closed="stopPolling">
    <template #title>
      {{ $t('documents.transcript.title') }}
    </template>
    <template v-if="transcript" #titleIcons>
      <v-tooltip bottom>
        <template #activator="{on, bind}">
          <v-btn
            icon
            v-on="on"
            v-bind="bind"
            @click="copy">
            <v-icon size="18">fas fa-copy</v-icon>
          </v-btn>
        </template>
        <span>{{ $t('documents.transcript.copy') }}</span>
      </v-tooltip>
    </template>
    <template v-if="drawer" #content>
      <div class="pa-4">
        <div
          v-if="file"
          :title="file.name"
          class="text-subtitle text-truncate mb-4">
          {{ file.name }}
        </div>
        <document-transcript-content
          v-if="done"
          :segments="segments"
          :text="text"
          :term="term"
          :seconds="seconds"
          @seek="seek" />
        <document-transcript-status
          v-else-if="state || unreadable"
          :state="state"
          :unreadable="unreadable"
          :retrying="retrying"
          @retry="retry" />
      </div>
    </template>
  </exo-drawer>
</template>
<script>
const POLLING_MILLIS = 15000;

const STALE_MILLIS = 3600000;

export default {
  data: () => ({
    drawer: false,
    loading: false,
    retrying: false,
    unreadable: false,
    documentId: null,
    seconds: null,
    file: null,
    state: null,
    text: null,
    segments: null,
    term: '',
    pollingTimer: null,
    aboveDialog: false,
  }),
  computed: {
    done() {
      return this.state?.status === 'DONE' && (this.text !== null || this.segments !== null);
    },
    transcript() {
      if (!this.done) {
        return '';
      }
      return (this.segments?.length && this.segments.map(segment => segment.text).join('\n') || this.text || '').trim();
    },
  },
  created() {
    document.addEventListener('documents-transcript-drawer-open', this.open);
    document.addEventListener('modalOpened', this.raiseAboveDialog);
    document.addEventListener('modalClosed', this.followDialogs);
  },
  beforeDestroy() {
    document.removeEventListener('documents-transcript-drawer-open', this.open);
    document.removeEventListener('modalOpened', this.raiseAboveDialog);
    document.removeEventListener('modalClosed', this.followDialogs);
    this.stopPolling();
  },
  methods: {
    /**
     * Opens the drawer on the file the event names, at the moment it names if
     * any.
     *
     * @param {CustomEvent} event its detail holds documentId and optionally
     *          seconds
     * @returns {void}
     */
    open(event) {
      const detail = event?.detail || {};
      if (this.documentId !== detail.documentId) {
        this.documentId = detail.documentId;
        this.file = null;
        this.state = null;
        this.text = null;
        this.segments = null;
        this.loadFile();
      }
      const seconds = ['number', 'string'].includes(typeof detail.seconds) && detail.seconds !== '' ? Number(detail.seconds) : NaN;
      this.seconds = Number.isFinite(seconds) && seconds >= 0 ? seconds : null;
      this.followDialogs();
      this.$refs.drawer.open();
      this.refresh();
    },
    /**
     * Reads the name and type of the file, to title the drawer and to open its
     * preview.
     * @returns {void}
     */
    loadFile() {
      const documentId = this.documentId;
      this.$attachmentService.getDocumentDetails(documentId)
        .then(file => {
          if (documentId === this.documentId) {
            this.file = file;
          }
        })
        .catch(() => this.file = null);
    },
    /**
     * Reads the transcription state of the file and, once transcribed, its
     * transcript; while it is queued or running, reads it again later.
     *
     * @returns {Promise} resolved once read
     */
    refresh() {
      this.stopPolling();
      const documentId = this.documentId;
      this.loading = true;
      return this.$transcriptionService.getState(documentId)
        .then(state => {
          if (documentId !== this.documentId) {
            return;
          }
          if (!state?.available) {
            this.$refs.drawer.close();
            this.openPreview(null);
            return;
          }
          this.unreadable = false;
          this.state = state;
          if (state.status === 'DONE') {
            return this.loadTranscript(documentId, state.hasSegments);
          } else if (this.drawer && this.isRunning(state)) {
            this.pollingTimer = window.setTimeout(this.refresh, POLLING_MILLIS);
          }
        })
        .catch(() => {
          this.state = null;
          this.unreadable = true;
        })
        .finally(() => this.loading = false);
    },
    /**
     * Reads the transcript, as timed segments when it has some, else as text.
     *
     * @param {string} documentId the file's id
     * @param {boolean} hasSegments whether the transcript has timed segments
     * @returns {Promise} resolved once read
     */
    loadTranscript(documentId, hasSegments) {
      if (this.text !== null || this.segments !== null) {
        return Promise.resolve();
      }
      const segments = hasSegments && this.$transcriptionService.getSegments(documentId).catch(() => null) || Promise.resolve(null);
      return segments.then(timedSegments => {
        if (documentId !== this.documentId) {
          return;
        }
        if (Array.isArray(timedSegments) && timedSegments.length) {
          this.segments = timedSegments;
          return;
        }
        return this.$transcriptionService.getText(documentId).then(text => {
          if (documentId === this.documentId) {
            this.text = text || '';
          }
        });
      });
    },
    /**
     * Tells whether a transcription is waiting in the queue, or running and
     * still moving; a queued one may wait long behind other runs.
     *
     * @param {Object} state the transcription state
     * @returns {boolean} true when queued, or running and changed less than an
     *          hour ago
     */
    isRunning(state) {
      return state.status === 'QUEUED'
        || (state.status === 'PROCESSING' && !!state.updated && Date.now() - state.updated < STALE_MILLIS);
    },
    /**
     * Keeps the drawer above a preview dialog opened while it is open, such as
     * the one a sentence click opens: the drawer computes its own layer only
     * when it opens.
     *
     * @returns {void}
     */
    raiseAboveDialog() {
      this.aboveDialog = true;
    },
    /**
     * Keeps the drawer above the dialogs still open, and back at its own
     * layer once none is.
     *
     * @returns {void}
     */
    followDialogs() {
      // a dialog announces its closing before it renders it: read the dialogs once rendered
      this.$nextTick(() => this.aboveDialog = !!document.querySelector('.v-dialog--active'));
    },
    /**
     * Stops reading the state again.
     * @returns {void}
     */
    stopPolling() {
      if (this.pollingTimer) {
        window.clearTimeout(this.pollingTimer);
        this.pollingTimer = null;
      }
    },
    /**
     * Asks to transcribe the file again, then reads its new state.
     * @returns {void}
     */
    retry() {
      this.retrying = true;
      this.$transcriptionService.retry(this.documentId)
        .then(() => this.refresh())
        .catch(() => this.alert(this.$t('documents.transcript.retryError'), 'error'))
        .finally(() => this.retrying = false);
    },
    /**
     * Moves the playing recording to a moment; when no preview of the file is
     * open, opens it there.
     *
     * @param {number} seconds the moment, in seconds from the start
     * @returns {void}
     */
    seek(seconds) {
      const handled = !document.dispatchEvent(new CustomEvent('document-preview-seek', {
        detail: {
          documentId: this.documentId,
          seconds,
        },
        cancelable: true,
      }));
      if (!handled) {
        this.openPreview(seconds);
      }
    },
    /**
     * Opens the preview of the file, its recording starting at a moment.
     *
     * @param {number} seconds the moment, in seconds from the start, or null to
     *          start at the beginning
     * @returns {void}
     */
    openPreview(seconds) {
      const documentId = this.documentId;
      const file = this.file && Promise.resolve(this.file) || this.$attachmentService.getDocumentDetails(documentId);
      // the Documents preview item carries the seek and the start time; a page
      // that does not load it otherwise would preview the recording without them
      const previewExtension = new Promise(resolve => window.require(['SHARED/documentsPreviewExtension'], resolve, resolve));
      Promise.all([file, previewExtension]).then(([details]) => {
        document.dispatchEvent(new CustomEvent('open-attachments-preview', {detail: {
          attachments: [{
            id: documentId,
            name: details.name,
            filename: details.name,
            mimetype: details.mimeType,
            downloadUrl: this.$documentsUtils.getDownloadUrl(documentId, details.modifiedDate),
            source: 'documents',
            startSeconds: seconds,
          }],
          id: documentId,
        }}));
      }).catch(() => this.alert(this.$t('documents.transcript.status.unreadable'), 'error'));
    },
    /**
     * Copies the whole transcript to the clipboard.
     * @returns {void}
     */
    copy() {
      navigator.clipboard.writeText(this.transcript)
        .then(() => this.alert(this.$t('documents.transcript.copied'), 'success'))
        .catch(() => this.alert(this.$t('documents.transcript.copyError'), 'error'));
    },
    /**
     * Shows a message in the page's alert area.
     *
     * @param {string} message the message
     * @param {string} type success or error
     * @returns {void}
     */
    alert(message, type) {
      this.$root.$emit('alert-message', message, type);
    },
  },
};
</script>
