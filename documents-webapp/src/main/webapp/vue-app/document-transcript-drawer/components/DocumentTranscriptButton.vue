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
  <v-btn
    v-if="available && previewAction"
    id="preview-attachment-transcript"
    :class="!isMobile && 'icon-large-size' || 'icon-medium-size'"
    :title="$t('documents.transcript.open')"
    icon
    class="white--text ml-1"
    @click="openTranscript">
    <i class="fas fa-closed-captioning"></i>
  </v-btn>
  <v-btn
    v-else-if="available && card"
    :title="$t('attachments.transcript.open')"
    small
    icon
    class="white--text my-auto mx-0"
    @click.stop="openTranscript">
    <v-icon size="20">fa-closed-captioning</v-icon>
  </v-btn>
  <v-tooltip v-else-if="available" bottom>
    <template #activator="{on, bind}">
      <v-btn
        icon
        v-on="on"
        v-bind="bind"
        @click="openTranscript">
        <v-icon size="18">fas fa-closed-captioning</v-icon>
      </v-btn>
    </template>
    <span>{{ $t('documents.transcript.open') }}</span>
  </v-tooltip>
</template>
<script>
import {getState} from '../../../js/TranscriptionService.js';

const lang = eXo?.env?.portal?.language || 'en';

const availability = new Map();

/**
 * Tells whether a file can be transcribed, reading its state once per page:
 * the answer depends on the edition and on the viewer's access to the file,
 * neither of which changes while the page is open. A failed read is not kept,
 * so the next button reads it again.
 *
 * @param {string} documentId the file's id
 * @returns {Promise<boolean>} true when the transcript can be offered
 */
function readAvailability(documentId) {
  if (!availability.has(documentId)) {
    availability.set(documentId, getState(documentId)
      .then(state => !!state?.available)
      .catch(() => {
        availability.delete(documentId);
        return false;
      }));
  }
  return availability.get(documentId);
}

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
    previewAction: {
      type: Boolean,
      default: false,
    },
    isMobile: {
      type: Boolean,
      default: false,
    },
    card: {
      type: Boolean,
      default: false,
    },
  },
  data: () => ({
    available: false,
  }),
  computed: {
    media() {
      return /^(audio|video)\//.test(this.mimetype || '');
    },
  },
  watch: {
    documentId: {
      immediate: true,
      handler() {
        this.checkAvailability();
      },
    },
  },
  methods: {
    /**
     * Shows the button only for an audio or video file of a server that can
     * transcribe it, once the label it shows is loaded. An attachment card
     * reads its label from the attachments bundle its application loads, and
     * never merges the Documents bundle, whose keys would override the
     * attachments' ones on the page.
     * @returns {void}
     */
    checkAvailability() {
      const documentId = this.documentId;
      this.available = false;
      if (!documentId || !this.media) {
        return;
      }
      readAvailability(documentId)
        .then(available => {
          if (!available || this.card) {
            return available;
          }
          return exoi18n.loadLanguageAsync(lang, `/documents-portlet/i18n/locale.portlet.Documents?lang=${lang}`)
            .then(() => true, () => true);
        })
        .then(available => this.available = documentId === this.documentId && !!available)
        .catch(() => this.available = false);
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
