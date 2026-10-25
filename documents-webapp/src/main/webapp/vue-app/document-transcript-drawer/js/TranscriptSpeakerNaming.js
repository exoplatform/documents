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
 * The naming of a transcript's speakers, shared by the transcript drawer and
 * the details' Transcript tab (EXO-92123): the component using it has a
 * documentId and an alert(message, type) method. A view saves one speaker at
 * a time, the server running the saves of a file one after the other, and
 * shows only its latest read of the names.
 */
export default {
  data: () => ({
    speakers: null,
    canNameSpeakers: false,
    savingSpeaker: null,
    speakersRead: 0,
  }),
  methods: {
    /**
     * Reads the named speakers of a transcript whose sentences tell their
     * speakers apart; forgets them otherwise.
     *
     * @param {string} documentId the file's UUID
     * @param {Array<object>} segments the transcript's sentences, possibly null
     * @returns {Promise<void>} resolved once read
     */
    loadSpeakers(documentId, segments) {
      if (!segments?.some(segment => segment.speaker)) {
        this.speakersRead++;
        this.speakers = null;
        this.canNameSpeakers = false;
        return Promise.resolve();
      }
      return this.reloadSpeakers(documentId);
    },
    /**
     * Names a speaker after the user chosen in the suggester, then reads the
     * named speakers again.
     *
     * @param {{speaker: string, user: object}} choice the speaker and the user
     * @returns {Promise<void>} resolved once done
     */
    nameSpeaker({speaker, user}) {
      if (this.savingSpeaker) {
        return Promise.resolve();
      }
      const documentId = this.documentId;
      this.savingSpeaker = speaker;
      return this.identityIdOf(user)
        .then(identityId => this.$transcriptionService.setSpeaker(documentId, speaker, identityId))
        .then(() => this.reloadSpeakers(documentId), e => this.alertSpeakerError(e))
        .finally(() => this.savingSpeaker = null);
    },
    /**
     * Unnames a speaker, then reads the named speakers again.
     *
     * @param {{speaker: string}} choice the speaker
     * @returns {Promise<void>} resolved once done
     */
    unnameSpeaker({speaker}) {
      if (this.savingSpeaker) {
        return Promise.resolve();
      }
      const documentId = this.documentId;
      this.savingSpeaker = speaker;
      return this.$transcriptionService.unsetSpeaker(documentId, speaker)
        .then(() => this.reloadSpeakers(documentId), e => this.alertSpeakerError(e))
        .finally(() => this.savingSpeaker = null);
    },
    /**
     * Reads the named speakers again, whatever the sentences say. Only the
     * latest read of the file shown changes the names, failed or not: a failed
     * one shows no names and no naming. Never rejected.
     *
     * @param {string} documentId the file's UUID
     * @returns {Promise<void>} resolved once read
     */
    reloadSpeakers(documentId) {
      const read = ++this.speakersRead;
      const isLatest = () => read === this.speakersRead && documentId === this.documentId;
      return this.$transcriptionService.getSpeakers(documentId).then(answer => {
        if (isLatest()) {
          this.speakers = answer?.speakers || [];
          this.canNameSpeakers = !!answer?.canEdit;
        }
      }, () => {
        if (isLatest()) {
          this.speakers = [];
          this.canNameSpeakers = false;
        }
      });
    },
    /**
     * The identity id of a user the suggester gives, which some of its sources
     * leave out: it is then looked up by the user name.
     *
     * @param {object} user the suggester's item
     * @returns {Promise<string>} the identity id
     */
    identityIdOf(user) {
      if (user?.identityId) {
        return Promise.resolve(user.identityId);
      }
      return this.$identityService.getIdentityByProviderIdAndRemoteId('organization', user?.remoteId).then(identity => identity.id);
    },
    /**
     * Tells why a speaker could not be named, with the server's reason when it
     * has a label.
     *
     * @param {Error} e the failure
     * @returns {void}
     */
    alertSpeakerError(e) {
      const code = e?.message;
      this.alert(code && this.$te(code) && this.$t(code) || this.$t('documents.transcript.speaker.error'), 'error');
    },
  },
};
