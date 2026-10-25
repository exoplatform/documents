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
    class="documentTranscriptDrawer"
    allow-expand
    use-filter
    right
    @filter-updated="term = $event"
    @expand-updated="expandIntoDetails"
    @closed="closePlayer">
    <template #title>
      {{ $t('documents.transcript.title') }}
    </template>
    <template v-if="transcript || detailsShown" #titleIcons>
      <v-tooltip v-if="detailsShown" bottom>
        <template #activator="{on, bind}">
          <v-btn
            :aria-label="detailsLabel"
            icon
            v-on="on"
            v-bind="bind"
            @click.stop="showDetails">
            <v-icon size="18">fas fa-info-circle</v-icon>
          </v-btn>
        </template>
        <span>{{ detailsLabel }}</span>
      </v-tooltip>
      <v-tooltip v-if="transcript" bottom>
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
          v-if="playerUrl && (file || fileUnknown)"
          :style="{position: 'sticky', top: 0, zIndex: 1}"
          class="white mb-4">
          <document-transcript-player
            ref="player"
            :url="playerUrl"
            :start="playerStart"
            :autoplay="playerAutoplay"
            :audio="audioFile"
            compact
            @time="playerSeconds = $event" />
        </div>
        <div>
          <document-transcript-content
            v-if="done"
            :segments="segments"
            :text="text"
            :term="term"
            :seconds="seconds"
            :playing-seconds="playerUrl && playerSeconds"
            @seek="seek" />
          <document-transcript-status
            v-else-if="state || unreadable"
            :state="state"
            :unreadable="unreadable"
            :retrying="retrying"
            @retry="retry" />
        </div>
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
    lastRead: 0,
    fileUnknown: false,
    playerAutoplay: false,
    playerUrl: null,
    playerStart: 0,
    playerSeconds: null,
    inDocuments: false,
  }),
  computed: {
    /**
     * Names the file whose details the header's button opens, for its hover
     * and for screen readers, since the drawer does not show the name.
     * @returns {string} the label
     */
    detailsLabel() {
      return this.file?.name && this.$t('documents.transcript.detailsOf', {0: this.file.name}) || this.$t('documents.transcript.details');
    },
    audioFile() {
      return /^audio\//.test(this.file?.mimeType || '');
    },
    done() {
      return this.state?.status === 'DONE' && (this.text !== null || this.segments !== null);
    },
    transcript() {
      if (!this.done) {
        return '';
      }
      return (this.segments?.length && this.segments.map(segment => segment.text).join('\n') || this.text || '').trim();
    },
    /**
     * Offers the file's details, from which Documents opens, to a drawer
     * opened outside Documents, once the file is known to be readable; never
     * beside a preview or in the Documents application, which show them.
     *
     * @returns {boolean} true when the details button shows
     */
    detailsShown() {
      return !this.inDocuments && !!this.file;
    },
  },
  created() {
    document.addEventListener('documents-transcript-drawer-open', this.open);
    document.addEventListener('documents-transcript-sync', this.syncFromDetails);
    document.addEventListener('documents-transcript-close', this.closeFromDetails);
  },
  beforeDestroy() {
    document.removeEventListener('documents-transcript-drawer-open', this.open);
    document.removeEventListener('documents-transcript-sync', this.syncFromDetails);
    document.removeEventListener('documents-transcript-close', this.closeFromDetails);
    window.removeEventListener('click', this.closeOnPreviewMask, true);
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
      const sameFile = this.documentId === detail.documentId;
      if (!sameFile) {
        this.documentId = detail.documentId;
        this.file = null;
        this.state = null;
        this.text = null;
        this.segments = null;
        this.playerUrl = null;
        this.fileUnknown = false;
        this.loadFile();
      }
      const seconds = ['number', 'string'].includes(typeof detail.seconds) && detail.seconds !== '' ? Number(detail.seconds) : NaN;
      this.seconds = Number.isFinite(seconds) && seconds >= 0 ? seconds : null;
      const besidePlayer = !document.dispatchEvent(new CustomEvent('document-preview-showing', {
        detail: {documentId: this.documentId},
        cancelable: true,
      }));
      this.inDocuments = besidePlayer || !!document.querySelector('.documents-application');
      if (besidePlayer) {
        this.playerUrl = null;
        window.addEventListener('click', this.closeOnPreviewMask, true);
      } else {
        window.removeEventListener('click', this.closeOnPreviewMask, true);
        if (!this.playerUrl) {
          this.openPlayer(this.seconds || 0, false);
        }
      }
      if (this.drawer) {
        this.bringToFront(sameFile);
      } else {
        this.$refs.drawer.open();
      }
      if (typeof detail.term === 'string' && detail.term) {
        // the drawer's search keeps its text once its field closes: set after
        // the opening, which empties it, the search shows as applied
        this.$refs.drawer.filterText = detail.term;
      }
      this.refresh();
    },
    /**
     * Shows the drawer, already open, above the drawers opened over it, such
     * as the file's details opened from its header, by closing those: opening
     * it again would leave it under them. The search is kept on the same file.
     *
     * @param {boolean} sameFile whether the drawer already shows the file
     * @returns {void}
     */
    bringToFront(sameFile) {
      const openedDrawers = eXo.openedDrawers || [];
      const index = openedDrawers.indexOf(this.$refs.drawer);
      if (index >= 0) {
        openedDrawers.slice(index + 1).reverse().forEach(drawer => drawer.close());
      }
      if (!sameFile) {
        this.$refs.drawer.resetFilter();
      }
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
        .catch(() => {
          if (documentId === this.documentId) {
            this.file = null;
            this.fileUnknown = true;
          }
        });
    },
    /**
     * Reads the transcription state of the file and, once transcribed, its
     * transcript; while it is queued or running, reads it again later. Only
     * the latest read changes what the drawer shows.
     *
     * @returns {Promise} resolved once read
     */
    refresh() {
      this.stopPolling();
      const documentId = this.documentId;
      const read = ++this.lastRead;
      this.loading = true;
      return this.$transcriptionService.getState(documentId)
        .then(state => {
          if (read !== this.lastRead) {
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
     * Opens the details drawer of the file, as the details button of a file
     * card does. When the details drawer is already open under this one, as
     * when the transcript was opened from it, it is closed first: opening it
     * again would leave it under this one, while closed then opened it comes
     * above it, and closing it returns to the transcript. The click is
     * stopped: the details drawer closes on any click outside it that reaches
     * the page.
     *
     * @returns {void}
     */
    showDetails() {
      this.closeDetailsUnder();
      document.dispatchEvent(new CustomEvent('open-document-info-drawer', {detail: this.documentId}));
    },
    /**
     * Closes the details drawer when it is open under this one, so that it
     * opens again above this one, drawn and closed as the top drawer.
     *
     * @returns {void}
     */
    closeDetailsUnder() {
      const openedDrawers = eXo.openedDrawers || [];
      const detailsIndex = openedDrawers.findIndex(drawer => drawer.$el?.classList?.contains('documentInfoDrawer'));
      if (detailsIndex >= 0 && detailsIndex < openedDrawers.indexOf(this.$refs.drawer)) {
        openedDrawers[detailsIndex].close();
      }
    },
    /**
     * Closes the drawer opened beside a preview when the preview's mask is
     * clicked, as any drawer closes on its mask, and keeps the preview open:
     * the click is stopped before the dialog sees it.
     *
     * @param {MouseEvent} event a click anywhere in the page
     * @returns {void}
     */
    closeOnPreviewMask(event) {
      const target = event?.target;
      if (target?.classList?.contains('v-dialog__content') || target?.closest?.('.v-overlay')) {
        event.stopPropagation();
        this.$refs.drawer.close();
      }
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
     * Moves the recording to a moment: the preview's when one shows the file,
     * else the drawer's own player, opened by this click when the preview the
     * drawer was opened beside has closed since.
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
      if (handled) {
        return;
      } else if (this.$refs.player) {
        this.$refs.player.seek(seconds);
      } else {
        this.openPlayer(seconds, true);
      }
    },
    /**
     * Shows the drawer's own player above the transcript.
     *
     * @param {number} seconds the moment to start at, in seconds
     * @param {boolean} autoplay whether to play at once
     * @returns {void}
     */
    openPlayer(seconds, autoplay) {
      this.playerStart = seconds;
      this.playerAutoplay = autoplay;
      this.playerUrl = this.$documentsUtils.getDownloadUrl(this.documentId, this.file?.modifiedDate);
    },
    /**
     * Expanding the drawer opens the file's full view above it, on its
     * Transcript tab, at the moment the recording reached and with the same
     * search: one full-screen view serves both the details and the
     * transcript, playing on when this drawer's recording was playing.
     * This drawer stays open underneath, narrow, its player paused, so
     * collapsing the full view reveals it in place.
     *
     * @param {boolean} expanded whether the drawer was expanded
     * @returns {void}
     */
    expandIntoDetails(expanded) {
      if (!expanded) {
        return;
      }
      this.$refs.drawer.toogleExpand();
      const media = this.playerMedia();
      const moment = this.playerSeconds ?? this.seconds;
      const playing = !!media && !media.paused;
      media?.pause?.();
      this.closeDetailsUnder();
      document.dispatchEvent(new CustomEvent('open-document-info-drawer', {detail: {
        documentId: this.documentId,
        expanded: true,
        tab: 'transcript',
        seconds: Number.isFinite(moment) ? Math.floor(moment) : null,
        term: this.term,
        origin: 'transcript',
        playing,
      }}));
    },
    /**
     * Takes back the moment and the search the full view reached when it
     * collapses onto this drawer, playing on only when the full view played.
     *
     * @param {CustomEvent} event its detail holds documentId, seconds, term
     *          and playing
     * @returns {void}
     */
    syncFromDetails(event) {
      const detail = event?.detail || {};
      if (!this.drawer || detail.documentId !== this.documentId) {
        return;
      }
      this.$refs.drawer.filterText = detail.term || '';
      const media = this.playerMedia();
      if (media && Number.isFinite(detail.seconds)) {
        media.currentTime = detail.seconds;
        this.playerSeconds = detail.seconds;
        this.seconds = detail.seconds;
        if (detail.playing) {
          const play = () => media.play()?.catch(() => {
            // a browser that refuses to play without a gesture still shows the moment
          });
          // a play started while the seek runs may be interrupted by it
          if (media.seeking) {
            media.addEventListener('seeked', play, {once: true});
          } else {
            play();
          }
        }
      }
    },
    /**
     * Finds the drawer's own audio or video element, whichever the player
     * renders.
     * @returns {HTMLMediaElement} the element, or null when no player shows
     */
    playerMedia() {
      const element = this.$refs.player?.$el;
      return element?.matches?.('video, audio') && element || element?.querySelector?.('video, audio') || null;
    },
    /**
     * Closes the drawer when the full view opened from it is closed, so one
     * click leaves both.
     *
     * @param {CustomEvent} event its detail holds documentId
     * @returns {void}
     */
    closeFromDetails(event) {
      if (this.drawer && event?.detail?.documentId === this.documentId) {
        this.$refs.drawer.close();
      }
    },
    /**
     * Stops the drawer's own player and the polling once the drawer closes.
     *
     * @returns {void}
     */
    closePlayer() {
      window.removeEventListener('click', this.closeOnPreviewMask, true);
      this.stopPolling();
      this.playerUrl = null;
      this.playerSeconds = null;
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
