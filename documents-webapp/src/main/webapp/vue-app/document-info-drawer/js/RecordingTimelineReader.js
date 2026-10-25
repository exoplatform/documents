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

/**
 * The reading of what a recording's timeline shows (EXO-92127): its timed
 * segments, its chapters and the speakers someone named. The component using
 * it has a documentId prop; it follows the names given in the transcript.
 */
export default {
  data: () => ({
    segments: null,
    rawChapters: null,
    speakers: null,
    lastRead: 0,
  }),
  watch: {
    documentId: {
      immediate: true,
      handler() {
        this.segments = null;
        this.rawChapters = null;
        this.speakers = null;
        this.read();
      },
    },
  },
  created() {
    document.addEventListener('documents-transcript-speakers-changed', this.readSpeakersAgain);
  },
  beforeDestroy() {
    this.lastRead++;
    document.removeEventListener('documents-transcript-speakers-changed', this.readSpeakersAgain);
  },
  methods: {
    /**
     * Reads what the timeline shows of a transcribed recording: its timed
     * segments, its chapters and the speakers someone named. A recording not
     * transcribed yet, or a read that fails, shows no timeline; only the
     * latest read changes it.
     * @returns {Promise} resolved once read
     */
    read() {
      const documentId = this.documentId;
      const read = ++this.lastRead;
      if (!documentId) {
        return Promise.resolve();
      }
      const service = this.$transcriptionService;
      return service.getState(documentId)
        .then(state => state?.status === 'DONE' && state.hasSegments && Promise.all([
          service.getSegments(documentId),
          state.hasChapters && service.getChapters(documentId).catch(() => null) || null,
        ]))
        .then(answer => {
          if (!answer || read !== this.lastRead) {
            return;
          }
          const [segments, chapters] = answer;
          this.segments = Array.isArray(segments) && segments || null;
          this.rawChapters = Array.isArray(chapters) && chapters || null;
          return this.segments?.some(segment => segment.speaker) && this.readSpeakers(documentId, read);
        })
        .catch(() => null);
    },
    /**
     * Reads the speakers someone named, kept only when no later read began.
     * @param {string} documentId the file's id
     * @param {number} read the read it belongs to
     * @returns {Promise} resolved once read, never rejected
     */
    readSpeakers(documentId, read) {
      return this.$transcriptionService.getSpeakers(documentId)
        .then(answer => read === this.lastRead && (this.speakers = answer?.speakers || []))
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
        this.readSpeakers(this.documentId, this.lastRead);
      }
    },
  },
};
