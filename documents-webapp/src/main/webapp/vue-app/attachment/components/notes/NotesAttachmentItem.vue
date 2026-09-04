<!--
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
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
-->

<template>
  <v-hover
    v-model="hover"
    :class="marginClass">
    <v-card
      :id="id"
      :elevation="hover ? 4 : 0"
      :class="{ 'border-color': !hover }"
      :loading="loading"
      height="210px"
      max-height="210px"
      width="252px"
      max-width="100%"
      class="activity-attachment overflow-hidden d-flex flex-column clickable border-box-sizing">
      <v-card-text
        class="activity-attachment-thumbnail d-flex flex-grow-1 pa-0"
        :class="isMediaFile && 'black'"
        @click="openPreview">
        <video
          v-if="showPlayer"
          :src="`${attachment.downloadUrl}#t=0.001`"
          controls="controls"
          autoplay
          class="black mx-auto full-height full-width position-absolute"
          @error="playError($event)">
        </video>
        <div
          v-else-if="isAudioFile"
          class="ma-auto black"></div>
        <img
          v-else-if="image"
          :src="image"
          class="ma-auto"
          :alt="$t('attachment.image.alt', { 0: attachment.name })"
          @load="loading = false"
          @error="image = image!==attachment.downloadUrl?attachment.downloadUrl:null">
        <v-icon
          v-else
          :class="fileIconClass"
          :color="fileIconColor"
          class="ma-auto d-flex"
          size="80px" />
      </v-card-text>
      <v-expand-transition>
        <v-card
          v-if="isMediaFile && !showPlayer"
          class="d-flex flex-column transition-fast-in-fast-out light-grey-background-opacity-3 v-card--reveal"
          elevation="0"
          style="height: 100%;"
          @click="playMedia($event)">
          <v-card-text class="pb-0 d-flex flex-row">
            <div class="absolute-all-center align-center">
              <v-icon class="playIcon" size="60px">far fa-play-circle</v-icon>
            </div>
          </v-card-text>
        </v-card>
      </v-expand-transition>
      <v-expand-transition>
        <v-card
          v-if="(hover || isMobile ) && !loading && !invalid && !showPlayer"
          class="d-flex flex-column transition-fast-in-fast-out mask-color v-card--reveal no-border-radius my-auto"
          elevation="0"
          style="height: 36px;">
          <v-card-text class="d-flex font-weight-bold ps-1 pe-0 py-0 my-auto">
            <v-avatar
              color="white"
              class=" my-auto"
              size="20">
              <v-icon size="12" :color="fileIconColor">{{ fileIconClass }}</v-icon>
            </v-avatar>
            <v-card             
              max-width="198px"
              class="d-flex  px-1 my-auto transparent no-border elevation-0">
              <v-card-text
                :title="attachment.name"
                class="pa-0  my-auto white--text text-wrap text-break text-truncate"
                v-text="attachment.name" />
            </v-card>
            <v-spacer />
            <v-btn
              id="attachment-info"
              @click="showInfo()"
              :title="$t('attachments.label.details')"
              small
              icon
              class="white--text my-auto mx-0">
              <v-icon size="20">fa-info-circle</v-icon>
            </v-btn>
          </v-card-text>
        </v-card>
      </v-expand-transition>
      <v-expand-transition>
        <v-card
          v-if="invalid"
          class="d-flex flex-column transition-fast-in-fast-out disabled-background v-card--reveal"
          elevation="0"
          style="height: 100%;">
          <v-card-text class="pb-0 d-flex flex-row">
            <v-icon color="error">fa-exclamation-circle</v-icon>
            <p class="my-auto ms-2 font-weight-bold text-truncate-3">
              {{ playErrorLabel }}
            </p>
          </v-card-text>
          <v-card-text class="flex-grow-1">
            <p class="text-truncate-3">{{ playErrorDescription }}</p>
          </v-card-text>
          <v-card-actions>
            <v-btn
              v-if="showDownloadButton"
              :href="attachment.downloadUrl"
              :download="attachment.name"
              :title="$t('attachments.label.download')"
              medium
              icon
              class="my-auto">
              <v-icon size="20">fa-download</v-icon>
            </v-btn>
            <v-spacer />
            <v-btn
              text
              color="primary"
              @click="closeErrorBox">
              {{ $t('attachments.close') }}
            </v-btn>
          </v-card-actions>
        </v-card>
      </v-expand-transition>
    </v-card>
  </v-hover>
</template>

<script>
export default {
  props: {
    attachment: {
      type: Object,
      default: null,
    },
    attachments: {
      type: Array,
      default: () => []
    },
    index: {
      type: Number,
      default: 0,
    },
    count: {
      type: Number,
      default: 0,
    },
    previewHeight: {
      type: String,
      default: () => '152px',
    },
    previewWidth: {
      type: String,
      default: () => '250px',
    },
  },
  data: () => ({
    loading: true,
    invalid: false,
    showPlayer: false,
    showDownloadButton: false,
    image: false,
    hover: false,
    playErrorLabel: '',
    playErrorDescription: '',
    dateFormat: {
      year: 'numeric',
      month: 'long',
      day: 'numeric',
      hour: 'numeric',
      minute: 'numeric',
      second: 'numeric',
    },
  }),
  computed: {
    id() {
      return `PreviewAttachment_${this.attachment.id}_${this.index}`;
    },
    nextId() {
      return (this.index + 1) < this.count && `#PreviewAttachment_${this.attachment.id}_${this.index + 1}` || '';    },
    previousId() {
      return this.index && `#PreviewAttachment_${this.attachment.id}_${this.index - 1}` || '';
    },
    marginClass() {
      if (this.count === 1) {
        return 'mx-auto';
      }
      const lastIndex = (this.count - 1) === this.index;
      return this.index && (lastIndex && 'ms-2' || 'mx-2') || 'me-2';
    },
    fileIconClass() {
      return this.attachment.icon?.class || 'fas fa-file';
    },
    fileIconColor() {
      return this.attachment.icon?.color || 'secondary';
    },
    isMediaFile() {
      return this.attachment && this.attachment.mimetype && (this.attachment.mimetype.startsWith('video/')|| this.attachment.mimetype.startsWith('audio/'));
    },
    isAudioFile() {
      return this.attachment && this.attachment.mimetype &&  this.attachment.mimetype.startsWith('audio/');
    },
    isMobile() {
      return this.$root.isMobile;
    }
  },
  created() {
    this.image = this.attachment && this.attachment.image;
  },
  watch: {
    image(newVal) {
      this.loading = newVal;
    },
  },
  methods: {
    markDocumentAsViewed() {
      document.dispatchEvent(new CustomEvent('mark-attachment-as-viewed', {detail: {file: this.attachment}}));
    },
    closeErrorBox(event) {
      if (event) {
        event.preventDefault();
        event.stopPropagation();
      }
      window.setTimeout(() => {
        this.invalid = false;
      }, 50);
      this.showDownloadButton = false;
    },
    openPreview() {
      if (this.invalid || this.showPlayer) {
        return;
      }
      this.loading = true;
      if (this.attachment.editable)  {
        this.$attachmentService.getDocumentDetails(this.attachment.id)
          .then(document => {
            if (document?.acl?.canEdit){
              this.openFileInEditor();
            } else {
              this.openFileInEditor('view');
            }
          });
      } else if (this.attachment.readable)  {
        this.openFileInEditor('view');
      } else {
        document.dispatchEvent(new CustomEvent('open-attachments-preview', {detail: {'attachments': this.attachments.filter(doc => !doc.readable),'id': this.attachment.id }}));
      }
      this.markDocumentAsViewed();
      this.loading = false;
    },
    openFileInEditor(mode) {
      if (this.attachment && this.attachment.id) {
        const url = this.$documentsUtils.getEditorUrl(this.attachment,mode);
        this.$documentsUtils.openLink(url, '_blank');
      }
    },
    showInfo(event) {
      if (event) {
        event.preventDefault();
        event.stopPropagation();
      }
      document.dispatchEvent(new CustomEvent('open-document-info-drawer', {detail: this.attachment.id}));
    },
    playMedia(event) {
      if (event) {
        event.preventDefault();
        event.stopPropagation();
      }
      this.showPlayer = true;
    },
    playError(event){
      const video = event.target;
      if (video.error && video.error.code === 3 || video.error.code === 4) {
        this.playErrorLabel= this.$t('attachments.errorVideoFormatNotSupported');
        this.playErrorDescription=this.$t('attachments.alert.videoFormatNotSupported');
        this.showDownloadButton = true;
      } else {
        this.playErrorLabel= this.$t('attachments.errorAccessingFile');
        this.playErrorDescription=this.$t('attachments.alert.unableToAccessFile');
      }
      this.invalid = true;
      this.showPlayer = false;
    }
  },
};
</script>
