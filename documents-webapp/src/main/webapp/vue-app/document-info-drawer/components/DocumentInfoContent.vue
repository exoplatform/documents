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
  <div class="documentInfoContent d-flex flex-column align-center justify-center fill-height pa-6">
    <document-transcript-player
      v-if="mediaKind"
      ref="player"
      :url="downloadUrl"
      :audio="mediaKind === 'audio'"
      :start="start"
      :autoplay="autoplay"
      class="documentInfoContentMedia"
      @time="$emit('time', $event)" />
    <button
      v-else
      :title="$t('documents.drawer.details.clickToOpen')"
      :aria-label="$t('documents.drawer.details.clickToOpen')"
      type="button"
      class="documentInfoContentOpen d-flex align-center justify-center"
      @click="$emit('open')">
      <img
        v-if="imageUrl"
        :src="imageUrl"
        :alt="file.name"
        class="documentInfoContentImage"
        @load="checkBlank"
        @error="imageFailed = true">
      <div v-else class="d-flex flex-column align-center text-center">
        <v-icon
          :class="icon && icon.class"
          :color="icon && icon.color"
          size="120" />
        <div class="documentInfoName font-weight-bold mt-4">{{ file.name }}</div>
        <div class="text-sub-title">{{ subtitle }}</div>
      </div>
    </button>
  </div>
</template>
<script>
import {isBlankImage} from '../js/BlankImage.js';

const PREVIEW_SIZE = '1200x1200';

export default {
  props: {
    file: {
      type: Object,
      default: null,
    },
    icon: {
      type: Object,
      default: null,
    },
    mediaKind: {
      type: String,
      default: null,
    },
    downloadUrl: {
      type: String,
      default: null,
    },
    readable: {
      type: Boolean,
      default: false,
    },
    start: {
      type: Number,
      default: 0,
    },
    autoplay: {
      type: Boolean,
      default: false,
    },
    subtitle: {
      type: String,
      default: null,
    },
  },
  data: () => ({
    imageFailed: false,
  }),
  computed: {
    /**
     * Shows an image at its own size, and any other file through its large
     * preview image when the server can render one.
     * @returns {string} the image's url, or null when there is none
     */
    imageUrl() {
      if (this.imageFailed || !this.file || this.file.folder) {
        return null;
      }
      if (this.file.mimeType?.startsWith('image/')) {
        return this.downloadUrl;
      }
      return this.$documentsUtils.getThumbnailUrl({...this.file, readable: this.readable}, PREVIEW_SIZE, this.file.modifiedDate);
    },
  },
  watch: {
    file() {
      this.imageFailed = false;
    },
  },
  methods: {
    /**
     * Shows the file's icon instead of the blank page the server rendered for
     * an office document or a PDF it could not render; an image keeps its
     * own look, however pale.
     * @param {Event} event the image's load event
     * @returns {void}
     */
    checkBlank(event) {
      if (this.readable && isBlankImage(event.target)) {
        this.imageFailed = true;
      }
    },
    /**
     * Moves the recording to a moment and plays it from there.
     * @param {number} seconds the moment, in seconds from the start
     * @returns {void}
     */
    seek(seconds) {
      this.$refs.player?.seek(seconds);
    },
  },
};
</script>
