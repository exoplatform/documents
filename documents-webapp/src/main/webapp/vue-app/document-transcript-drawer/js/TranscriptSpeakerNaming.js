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
 * The naming of a transcript's speakers, shared by the transcript drawer, the
 * details' Transcript tab (EXO-92123) and the full view's timeline
 * (EXO-92127): the component using it has a documentId and an alert(message,
 * type) method. A view saves one speaker at a time, the server running the
 * saves of a file one after the other, shows only its latest read of the
 * names, and reads them again when another view names a speaker. A user who
 * can name the speakers also reads the proposals of who they are (EXO-92126).
 */
export default {
  data: () => ({
    speakers: null,
    speakerProposals: [],
    canNameSpeakers: false,
    savingSpeaker: null,
    speakersRead: 0,
  }),
  /**
   * Follows the names given in the other views of the transcript.
   * @returns {void}
   */
  created() {
    document.addEventListener('documents-transcript-speakers-changed', this.followSpeakers);
  },
  /**
   * Stops following the names given elsewhere.
   * @returns {void}
   */
  beforeDestroy() {
    document.removeEventListener('documents-transcript-speakers-changed', this.followSpeakers);
  },
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
        this.speakerProposals = [];
        this.canNameSpeakers = false;
        return Promise.resolve();
      }
      return this.reloadSpeakers(documentId);
    },
    /**
     * Names a speaker after the user chosen in the suggester, or after a
     * guest's plain name, then reads the named speakers again.
     *
     * @param {{speaker: string, user: object, guestName: string}} choice the
     *          speaker, and the user or the guest's name
     * @returns {Promise<void>} resolved once done
     */
    nameSpeaker({speaker, user, guestName}) {
      if (this.savingSpeaker) {
        return Promise.resolve();
      }
      const documentId = this.documentId;
      this.savingSpeaker = speaker;
      const saved = guestName
        ? this.$transcriptionService.setGuestSpeaker(documentId, speaker, guestName)
        : this.identityIdOf(user).then(identityId => this.$transcriptionService.setSpeaker(documentId, speaker, identityId));
      return saved
        .then(() => this.reloadSpeakers(documentId).then(() => this.announceSpeakers(documentId)), e => this.alertSpeakerError(e))
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
        .then(() => this.reloadSpeakers(documentId).then(() => this.announceSpeakers(documentId)), e => this.alertSpeakerError(e))
        .finally(() => this.savingSpeaker = null);
    },
    /**
     * Names a speaker after the person proposed for it, then reads the named
     * speakers again and tells the other views of the file.
     *
     * @param {{speaker: string, identityId: string}} proposal the proposal
     * @returns {Promise<void>} resolved once done
     */
    confirmSpeakerProposal({speaker, identityId}) {
      if (this.savingSpeaker) {
        return Promise.resolve();
      }
      const documentId = this.documentId;
      this.savingSpeaker = speaker;
      return this.$transcriptionService.setSpeaker(documentId, speaker, identityId)
        .then(() => this.reloadSpeakers(documentId).then(() => this.announceSpeakers(documentId)), e => this.alertSpeakerError(e))
        .finally(() => this.savingSpeaker = null);
    },
    /**
     * Dismisses the person proposed for a speaker, for good, then reads the
     * named speakers and the proposals again.
     *
     * @param {{speaker: string}} proposal the proposal
     * @returns {Promise<void>} resolved once done
     */
    dismissSpeakerProposal({speaker}) {
      if (this.savingSpeaker) {
        return Promise.resolve();
      }
      const documentId = this.documentId;
      this.savingSpeaker = speaker;
      return this.$transcriptionService.dismissSpeakerProposal(documentId, speaker)
        .then(() => this.reloadSpeakers(documentId), e => this.alertSpeakerError(e))
        .finally(() => this.savingSpeaker = null);
    },
    /**
     * Tells the page that the speakers of a file were named again, so that the
     * other views of its transcript, as the full view's timeline, read the
     * names again (EXO-92127). The view that named tells it is the source,
     * having read the names already.
     *
     * @param {string} documentId the file's UUID
     * @returns {void}
     */
    announceSpeakers(documentId) {
      document.dispatchEvent(new CustomEvent('documents-transcript-speakers-changed', {detail: {documentId, source: this._uid}}));
    },
    /**
     * Reads the names again once another view of the same file's transcript
     * named a speaker, once this view has read or reset the names of the
     * file it shows (speakersRead only grows): a read started before that
     * naming may answer the former names, and an extra read costs one request.
     *
     * @param {CustomEvent} event its detail holds the file's documentId and the view that named
     * @returns {void}
     */
    followSpeakers(event) {
      const detail = event?.detail;
      if ((this.speakers || this.speakersRead) && detail?.documentId === this.documentId && detail.source !== this._uid) {
        this.reloadSpeakers(this.documentId);
      }
    },
    /**
     * Reads the named speakers again, whatever the sentences say, and for a
     * user who can name them the proposals. Only the latest read of the file
     * shown changes them, failed or not: a failed one shows no names, no
     * naming and no proposal; failed proposals alone show none. Never
     * rejected.
     *
     * @param {string} documentId the file's UUID
     * @returns {Promise<void>} resolved once read
     */
    reloadSpeakers(documentId) {
      const read = ++this.speakersRead;
      const isLatest = () => read === this.speakersRead && documentId === this.documentId;
      return this.$transcriptionService.getSpeakers(documentId).then(answer => {
        const canEdit = !!answer?.canEdit;
        const proposals = canEdit && this.$transcriptionService.getSpeakerProposals(documentId).catch(() => []) || Promise.resolve([]);
        return proposals.then(speakerProposals => {
          if (isLatest()) {
            this.speakers = answer?.speakers || [];
            this.speakerProposals = speakerProposals || [];
            this.canNameSpeakers = canEdit;
          }
        });
      }, () => {
        if (isLatest()) {
          this.speakers = [];
          this.speakerProposals = [];
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
