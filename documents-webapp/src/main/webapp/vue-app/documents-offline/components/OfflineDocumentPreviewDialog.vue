<!--
 Copyright (C) 2025 eXo Platform SAS.

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU Affero General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU Affero General Public License for more details.

 You should have received a copy of the GNU Affero General Public License
 along with this program. If not, see <http://www.gnu.org/licenses/>.
-->
<template>
  <v-dialog
    v-model="dialog"
    :persistent="false"
    width="80vw"
    max-width="80vw"
    height="100%"
    overlay-opacity="0.9"
    content-class="overflow-y-initial">
    <template v-if="dialog">
      <div class="d-flex justify-end">
        <v-btn
          id="preview-attachment-download"
          :class="!isMobile && 'icon-large-size' || 'icon-medium-size'"
          :title="$t('OfflineApp.pwa.download')"
          icon
          class="white--text ms-1"
          @click="$emit('download', file)">
          <v-icon>fa-download</v-icon>
        </v-btn>
        <v-btn
          id="preview-attachment-close"
          :class="!isMobile && 'icon-large-size' || 'icon-medium-size'"
          :title="$t('OfflineApp.pwa.closePreview')"
          icon
          class="white--text ms-1"
          @click="close">
          <v-icon>fa-times</v-icon>
        </v-btn>
      </div>
      <v-card 
        v-if="src"
        min-height="75vh"
        height="calc(100% - 80px)"
        max-height="calc(100% - 80px)"
        class="d-flex align-center justify-center my-auto position-relative"
        color="transparent"
        flat>
        <v-img
          v-if="isImage"
          :src="src"
          aspect-ratio="2"
          contain
          eager />
        <video
          v-else-if="isVideo || isAudio"
          :src="src"
          controls="controls"
          autoplay
          class="black mx-auto full-height full-width position-absolute">
        </video>
      </v-card>
    </template>
  </v-dialog>
</template>
<script>
export default {
  data: () => ({
    dialog: false,
    file: null, 
    extension: null, 
    src: null, 
  }),
  computed: {
    isMobile() {
      return this.$vuetify.breakpoint.smAndDown;
    },
    isImage() {
      return this.extension?.isImage;
    },
    isVideo() {
      return this.extension?.isVideo;
    },
    isAudio() {
      return this.extension?.isAudio;
    },
  },
  watch: {
    dialog() {
      if (this.dialog) {
        document.dispatchEvent(new CustomEvent('modalOpened'));
      } else {
        document.dispatchEvent(new CustomEvent('modalClosed'));
      }
    }
  },
  created() {
    document.addEventListener('keydown', this.closeOnEscape);
  },
  beforeDestroy() {
    document.removeEventListener('keydown', this.closeOnEscape);
  },
  methods: {
    async open(file, extension) {
      this.file = file;
      this.extension = extension;
      this.src = null;
      this.dialog = true;
      this.src = URL.createObjectURL(await this.file.handle.getFile());
    },
    closeOnEscape(event) {
      if (this.$refs.attachmentsCarousel) {
        if (event.key === 'Escape') {
          this.close();
        }
      }
    },
    close() {
      this.dialog = false;
    },
  }
};
</script>
