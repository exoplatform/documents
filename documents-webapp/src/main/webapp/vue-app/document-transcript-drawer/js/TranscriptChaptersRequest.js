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

import {awaitsChapters, lacksChapters} from './TranscriptChapters.js';

/**
 * The request for a transcript's chapters, for the views that show a
 * transcript or its chapters (the details' Transcript tab, the transcript
 * drawer, the full view's timeline). A transcript opened with none, just made
 * or done before chapters were switched on, asks for them once. When the
 * server answers they are coming, the view reads its state again through its
 * own pollForChapters().
 */
export default {
  data: () => ({
    chaptersAsked: false,
    chaptersComingSince: null,
  }),
  methods: {
    /**
     * Forgets the request, for another file.
     *
     * @returns {void}
     */
    resetChaptersRequest() {
      this.chaptersAsked = false;
      this.chaptersComingSince = null;
    },
    /**
     * Asks once for the chapters of a transcript opened with none.
     *
     * @param {string} documentId the file's id
     * @param {Object} state the transcription state
     * @returns {void}
     */
    askChapters(documentId, state) {
      if (this.chaptersAsked || !lacksChapters(state)) {
        return;
      }
      this.chaptersAsked = true;
      this.$transcriptionService.requestChapters(documentId)
        .then(coming => {
          if (coming && documentId === this.documentId) {
            this.chaptersComingSince = Date.now();
            this.pollForChapters();
          }
        })
        .catch(() => null);
    },
    /**
     * Tells whether the chapters the server said are coming may still come.
     *
     * @param {Object} state the transcription state
     * @returns {boolean} true while they are awaited
     */
    awaitingChapters(state) {
      return awaitsChapters(state, this.chaptersComingSince);
    },
  },
};
