/*
 * Copyright (C) 2025 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program; if not, write to the Free Software Foundation,
 */

<template>
  <video
    v-if="supported"
    ref="player"
    :src="`${thumbnailUrl}#t=${startTime}`"
    controls="controls"
    class="black mx-auto full-height full-width position-absolute"
    @error="playError($event)">
  </video>
  <attachments-default-preview
    v-else
    :attachment="attachment"
    :error-message="playErrorDescription" />
</template>
<script>
export default {
  props: {
    attachment: {
      type: Object,
      default: null,
    },
    objectType: {
      type: String,
      default: null,
    },
    objectId: {
      type: String,
      default: null,
    },
  },
  data: () => ({
    supported: true,
    playErrorDescription: ''
  }),
  computed: {
    thumbnailUrl() {
      return this.attachment.downloadUrl || `${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/social/attachments/${this.objectType}/${this.objectId}/${this.attachment.id}`;
    },
    startTime() {
      const seconds = Number(this.attachment.startSeconds);
      return Number.isFinite(seconds) && seconds > 0 && seconds || 0.001;
    },
    isMobile() {
      return this.$vuetify.breakpoint.name === 'sm' || this.$vuetify.breakpoint.name === 'xs' || this.$vuetify.breakpoint.name === 'md';
    }
  },

  created() {
    document.addEventListener('document-preview-seek', this.seek);
  },
  beforeDestroy() {
    document.removeEventListener('document-preview-seek', this.seek);
  },
  methods: {
    /**
     * Moves the recording to the moment the event names, when it names this
     * file, and plays it from there.
     *
     * @param {CustomEvent} event its detail holds documentId and seconds; it is
     *          marked handled once the recording moved
     * @returns {void}
     */
    seek(event) {
      const seconds = Number(event?.detail?.seconds);
      const player = this.$refs.player;
      if (!player || event?.detail?.documentId !== this.attachment?.id || !Number.isFinite(seconds) || seconds < 0) {
        return;
      }
      event.preventDefault();
      player.currentTime = seconds;
      player.play()?.catch(() => {
        // a browser that refuses to play without a user gesture still shows the moment
      });
    },
    playError(event){
      const video = event.target;
      if (video.error && video.error.code === 3 || video.error.code === 4) {
        this.playErrorDescription=this.$t('attachment.alert.videoFormatNotSupported');
      } else {
        this.playErrorDescription=this.$t('attachment.alert.unableToAccessFile');
      }
      this.supported = false;
    }
  }
};
</script>

