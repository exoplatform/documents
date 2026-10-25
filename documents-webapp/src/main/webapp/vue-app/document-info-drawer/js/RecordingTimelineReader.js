/*
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
 */

import {awaitsChapters} from '../../document-transcript-drawer/js/TranscriptChapters.js';

/** How often the timeline reads the state again while the transcript or its chapters may still come, in milliseconds. */
const POLLING_MILLIS = 15000;

/** How long a transcription may run without a change before it is no longer waited for, in milliseconds. */
const STALE_MILLIS = 3600000;

/**
 * The reading of what a recording's timeline shows (EXO-92127): its timed
 * segments, its chapters and the speakers someone named, with the names they
 * go by. The component using it has a documentId prop; it follows the names
 * given in the transcript, and reads the state again while the transcript is
 * made or its chapters may still come, as the Transcript tab does.
 */
export default {
  data: () => ({
    segments: null,
    rawChapters: null,
    speakers: null,
    lastRead: 0,
    speakersRead: 0,
    chaptersAwaitedSince: null,
    pollingTimer: null,
  }),
  computed: {
    /** @returns {Object} the persons someone named, by their speaker's number */
    speakersByNumber() {
      return Object.fromEntries((this.speakers || []).map(person => [person.speaker, person]));
    },
  },
  watch: {
    documentId: {
      immediate: true,
      /**
       * Forgets the file shown before and reads the new one.
       * @returns {void}
       */
      handler() {
        this.segments = null;
        this.rawChapters = null;
        this.speakers = null;
        this.speakersRead++;
        this.chaptersAwaitedSince = Date.now();
        this.read();
      },
    },
  },
  /**
   * Follows the names given in the transcript.
   * @returns {void}
   */
  created() {
    document.addEventListener('documents-transcript-speakers-changed', this.readSpeakersAgain);
    document.addEventListener('documents-transcript-chapters-requested', this.awaitChapters);
  },
  /**
   * Stops reading: no read under way changes the timeline any more.
   * @returns {void}
   */
  beforeDestroy() {
    this.lastRead++;
    this.speakersRead++;
    this.stopPolling();
    document.removeEventListener('documents-transcript-speakers-changed', this.readSpeakersAgain);
    document.removeEventListener('documents-transcript-chapters-requested', this.awaitChapters);
  },
  methods: {
    /**
     * Reads what the timeline shows of a transcribed recording: its timed
     * segments, its chapters and, on the first read of its segments, the
     * speakers someone named. A recording not transcribed yet, or a read that
     * fails, shows no timeline; only the latest read changes it. The state is
     * read again while the transcription runs or the chapters may still come.
     * @returns {Promise} resolved once read
     */
    read() {
      const documentId = this.documentId;
      const read = ++this.lastRead;
      this.stopPolling();
      if (!documentId) {
        return Promise.resolve();
      }
      const service = this.$transcriptionService;
      return service.getState(documentId)
        .then(state => {
          if (read !== this.lastRead) {
            return null;
          }
          if (this.mayChange(state)) {
            this.pollingTimer = window.setTimeout(this.read, POLLING_MILLIS);
          }
          if (state?.status !== 'DONE' || !state.hasSegments) {
            return null;
          }
          // a transcript cannot change once done: what was already read stays
          return Promise.all([
            this.segments || service.getSegments(documentId),
            this.rawChapters || state.hasChapters && service.getChapters(documentId).catch(() => null) || null,
          ]);
        })
        .then(answer => {
          if (!answer || read !== this.lastRead) {
            return;
          }
          const firstRead = !this.segments;
          const [segments, chapters] = answer;
          this.segments = Array.isArray(segments) && segments || null;
          this.rawChapters = Array.isArray(chapters) && chapters || null;
          return firstRead && this.segments?.some(segment => segment.speaker) && this.readSpeakers(documentId);
        })
        .catch(() => null);
    },
    /**
     * Waits for the chapters the Transcript tab just asked for, reading the
     * state again until they come or the wait for them ends.
     * @param {CustomEvent} event its detail holds the file's documentId
     * @returns {void}
     */
    awaitChapters(event) {
      if (event?.detail?.documentId === this.documentId) {
        this.chaptersAwaitedSince = Date.now();
        this.read();
      }
    },
    /**
     * Tells whether what the timeline shows may still change: a transcription
     * waiting in the queue or running and still moving, or a transcript whose
     * chapters may still come, made with it or asked for by the Transcript tab
     * since the timeline opened.
     * @param {Object} state the transcription state
     * @returns {boolean} true when the state is worth reading again
     */
    mayChange(state) {
      return state?.status === 'QUEUED'
        || (state?.status === 'PROCESSING' && !!state.updated && Date.now() - state.updated < STALE_MILLIS)
        || awaitsChapters(state, this.chaptersAwaitedSince);
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
     * Reads the speakers someone named. Only the latest read of the speakers
     * of the file shown changes the names: an earlier answer arriving last
     * holds older names.
     * @param {string} documentId the file's id
     * @returns {Promise} resolved once read, never rejected
     */
    readSpeakers(documentId) {
      const read = ++this.speakersRead;
      return this.$transcriptionService.getSpeakers(documentId)
        .then(answer => {
          if (read === this.speakersRead && documentId === this.documentId) {
            this.speakers = answer?.speakers || [];
          }
        })
        .catch(() => null);
    },
    /**
     * Reads the named speakers again once someone named one in the
     * transcript, so that the lanes show the new names.
     * @param {CustomEvent} event its detail holds the file's documentId
     * @returns {void}
     */
    readSpeakersAgain(event) {
      if (this.segments && event?.detail?.documentId === this.documentId) {
        this.readSpeakers(this.documentId);
      }
    },
    /**
     * The person a lane's speaker is, once someone named them.
     * @param {Object} lane the lane
     * @returns {Object} the person, or null for an unnamed speaker or « Others »
     */
    personOf(lane) {
      return lane.speaker && this.speakersByNumber[lane.speaker] || null;
    },
    /**
     * The name of a speaker: the person someone named, else « Speaker N ».
     * @param {string} speaker the speaker's number
     * @returns {string} the name
     */
    speakerName(speaker) {
      return this.speakersByNumber[speaker]?.fullName || this.$t('documents.transcript.speaker.label', {0: speaker});
    },
    /**
     * The name of a lane: its speaker's, or « Others (N) » for the shared one.
     * @param {Object} lane the lane
     * @returns {string} the name
     */
    laneName(lane) {
      return lane.speaker && this.speakerName(lane.speaker) || this.$t('documents.timeline.others', {0: lane.count});
    },
  },
};
