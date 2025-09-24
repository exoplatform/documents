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
  <v-hover v-slot="{hover}" :class="extraMargin && marginClass">
    <v-card
      :elevation="hover ? 4 : 0"
      :class="{ 'border-color': !hover }"
      :loading="loading"
      :height="height"
      :max-height="maxHeight"
      :width="width"
      max-width="100%"
      class="activity-attachment overflow-hidden d-flex flex-column clickable border-box-sizing">
      <v-card-text
        class="activity-attachment-thumbnail d-flex flex-grow-1 pa-0"
        :class="isMediaFile && 'black'"
        @click="openPreview">
        <video
          v-if="showPlayer"
          :src="`${downloadUrl}#t=0.001`"
          controls="controls"
          autoplay
          class="black mx-auto full-height full-width position-absolute"
          @error="playError($event)">
        </video>
        <div
          v-else-if="isAudioFile"
          class="ma-auto black"></div>
        <img
          v-else-if="fileImage"
          :src="fileImage"
          class="ma-auto"
          @load="loading = false"
          @error="fileImage = fileImage !== downloadUrl ? downloadUrl : null">
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
          height="100%"
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
          v-if="(hover || isMobile || showDetails) && !loading && !invalid && !showPlayer"
          class="d-flex flex-column transition-fast-in-fast-out mask-color v-card--reveal no-border-radius my-auto"
          elevation="0"
          style="height: 36px;">
          <v-card-text class="d-flex font-weight-bold ps-1 pe-0 py-0 my-auto">
            <v-avatar
              color="white"
              class=" my-auto"
              size="20">
              <v-icon size="12" :color="fileIconColor"> {{ fileIconClass }} </v-icon>
            </v-avatar>
            <v-card
              :max-width="(hover || selectedDocuments.length) && showDetails ? '50%' : showDetails ? '90%': '80%'"
              class="d-flex  px-1 my-auto  no-border elevation-0">
              <v-card-text
                :title="fileName"
                :class="showDetails && 'text-subtitle-2'"
                class="pa-0  my-auto white--text text-wrap text-break text-truncate"
                v-text="fileName" />
            </v-card>
            <v-spacer />
            <div class="d-flex">
              <v-simple-checkbox
                v-show="(hover || selectedDocuments.length) && showDetails"
                v-model="checked"
                color="white"
                @click="selectDocument($event)"
                @mouseover="checkRangeSelect"
                @mouseleave="resetRangeSelect" />
              <v-btn
                id="attachment-info"
                v-show="hover"
                @click="showInfo()"
                :title="$t('attachments.label.details')"
                small
                icon
                class="white--text my-auto mx-0">
                <v-icon size="20">fa-info-circle</v-icon>
              </v-btn>  
              <div v-show="hover && showDetails">
                <v-menu
                  v-model="menuDisplayed"
                  bottom>
                  <template #activator="{ on, attrs }">
                    <v-btn
                      :title="$t('documents.label.menu.action.tooltip')"
                      small
                      icon
                      class="white--text my-auto mx-0"
                      v-bind="attrs"
                      v-on="on">
                      <v-icon size="20">mdi-dots-vertical</v-icon>
                    </v-btn>
                  </template>
                  <documents-actions-menu
                    :file="file"
                    :current-view="currentView"
                    :is-search-result="isSearchResult"
                    :is-mobile="isMobile" />
                </v-menu>
              </div>
            </div>  
          </v-card-text>
        </v-card>
      </v-expand-transition>
      <v-expand-transition>
        <v-card
          v-if="invalid"
          class="d-flex flex-column transition-fast-in-fast-out disabled-background v-card--reveal"
          elevation="0"
          height="100%">
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
              :href="downloadUrl"
              :download="fileName"
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
    file: {
      type: Object,
      default: null,
    },
    files: {
      type: Array,
      default: () => []
    },
    selectedDocuments: {
      type: Array,
      default: () => [],
    },
    index: {
      type: Number,
      default: 0,
    },
    count: {
      type: Number,
      default: 0,
    },
    extraMargin: {
      type: Boolean,
      default: false
    },
    showDetails: {
      type: Boolean,
      default: false
    },
    height: {
      type: String,
      default: '210px',
    },
    maxHeight: {
      type: String,
      default: '210px',
    },
    width: {
      type: String,
      default: '252px',
    },
    currentView: {
      type: String,
      default: ''
    },
    isSearchResult: {
      type: Boolean,
      default: false
    }
  },
  data: () => ({
    loading: true,
    fileImage: false,
    invalid: false,
    showPlayer: false,
    showDownloadButton: false,
    playErrorLabel: '',
    playErrorDescription: '',
    checked: false,
    show: false,
    canSelectRange: false,
    rangeSelectTimer: null,
    menuDisplayed: false,
    waitTimeUntilCloseMenu: 200,
  }),
  computed: {
    fileId() {
      return this.file?.id;
    },
    fileName() {
      return this.file?.name;
    },
    fileIconClass() {
      return this.file?.icon?.class || 'fas fa-file';
    },
    fileIconColor() {
      return this.file?.icon?.color || 'secondary';
    },
    marginClass() {
      if (this.count === 1) {
        return 'mx-auto';
      }
      const lastIndex = (this.count - 1) === this.index;
      return this.index && (lastIndex && 'ms-2' || 'mx-2') || 'me-2';
    },
    downloadUrl() {
      return this.file?.downloadUrl;
    },
    id() {
      return `PreviewDoc_${this.fileId}_${this.index}`;
    },
    isFolder() {
      return this.file?.folder;
    },
    mimeType() {
      return this.file?.mimetype;
    },
    isMediaFile() {
      return this.mimeType?.startsWith('video/') || this.mimeType?.startsWith('audio/');
    },
    isAudioFile() {
      return this.mimeType.startsWith('audio/');
    },
    isMobile() {
      return this.$root.isMobile;
    },
    canEdit() {
      return this.file?.acl?.canEdit;
    },
    isFileEditable() {
      const type = this.mimeType || '';
      return this.$supportedDocuments && this.$supportedDocuments.filter(doc => doc.edit && doc.mimeType === type && !this.file.cloudDriveFile).length > 0;
    },
    isFileOnlyReadable() {
      const type = this.mimeType || '';
      return this.$supportedDocuments && this.$supportedDocuments.filter(doc => !doc.edit && doc.mimeType === type && !this.file.cloudDriveFile).length > 0;
    },
  },
  watch: {
    fileImage(newVal) {
      this.loading = newVal;
    },
  },
  created() {
    this.$root.$on('update-selection-documents-list', this.handleUpdateSelectionList);
    this.$root.$on('show-selection-input', this.handleShowSelectionInput);
    this.$root.$on('hide-selection-input', this.handleHideSelectionInput);
    this.$root.$on('select-all-documents', this.handleSelectAllDocuments);
    this.$root.$on('select-target-document', this.handleSelectTargetDocument);
    this.$root.$on('reset-selections', this.handleResetSelections);
    this.fileImage = this.file?.image;
    this.initSelected();
    $(document).on('mousedown', (event) => {
      if (!event.target.closest('.group-menu-action') && this.menuDisplayed) {
        window.setTimeout(() => {
          this.menuDisplayed = false;
        }, this.waitTimeUntilCloseMenu);
      }
    });
  },
  beforeDestroy() {
    this.$root.$off('update-selection-documents-list', this.handleUpdateSelectionList);
    this.$root.$off('show-selection-input', this.handleShowSelectionInput);
    this.$root.$off('hide-selection-input', this.handleHideSelectionInput);
    this.$root.$off('select-all-documents', this.handleSelectAllDocuments);
    this.$root.$off('select-target-document', this.handleSelectTargetDocument);
    this.$root.$off('reset-selections', this.handleResetSelections);
  },
  mounted() {
    document.addEventListener('mousedown', (event) => {
      if (!event.target.closest('.group-menu-action') && this.menuDisplayed) {
        setTimeout(() => {
          this.menuDisplayed = false;
        }, this.waitTimeUntilCloseMenu);
      }
    });
  },
  methods: {
    displayActionMenu() {
      if (this.isMobile){
        this.$root.$emit('open-file-action-menu', this.file);
      } else {
        this.menuDisplayed = true;
      }
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
      this.loading = true;
      if (this.isFolder) {
        this.$root.$emit('document-open-folder', this.file);
      } else if (this.isFileEditable && this.canEdit) {
        this.$root.openInEditMode(this.file);
      } else if (this.isFileOnlyReadable) {
        this.$root.openInReadOnlyMode(this.file);
      } else {
        this.$root.$emit('documents-preview', this.file);
      }
      this.loading = false;
      this.$root.$emit('mark-document-as-viewed', this.file);
    },
    showInfo(event) {
      if (event) {
        event.preventDefault();
        event.stopPropagation();
      }
      document.dispatchEvent(new CustomEvent('open-document-info-drawer', {detail: this.fileId}));
    },
    playMedia(event) {
      if (event) {
        event.preventDefault();
        event.stopPropagation();
      }
      this.showPlayer = true;
    },
    playError(event) {
      const video = event.target;
      if (video.error && video.error.code === 3 || video.error.code === 4) {
        this.playErrorLabel = this.$t('attachments.errorVideoFormatNotSupported');
        this.playErrorDescription = this.$t('attachments.alert.videoFormatNotSupported');
        this.showDownloadButton = true;
      } else {
        this.playErrorLabel = this.$t('attachments.errorAccessingFile');
        this.playErrorDescription = this.$t('attachments.alert.unableToAccessFile');
      }
      this.invalid = true;
      this.showPlayer = false;
    },
    isFileSelected(file) {
      return this.selectedDocuments.findIndex(object => object.id === file?.id) !== -1;
    },
    selectRangeDocuments(start, end) {
      this.files.slice(start, end + 1).forEach(file => {
        this.$root.$emit('select-target-document', file);
      });
    },
    selectDocument(event) {
      if (event && event.shiftKey && this.selectedDocuments.length) {
        const currentPosition = this.files.findIndex(file => file.id === this.fileId);
        const startPosition  = this.getFirstSelectedAbovePosition(currentPosition, this.files);
        const endPosition  = this.getFirstSelectedBelowPosition(currentPosition, this.files);
        if (startPosition !== -1 && endPosition !== -1) {
          this.selectRangeDocuments(startPosition, endPosition);
        } else if (startPosition !== -1) {
          this.selectRangeDocuments(startPosition, currentPosition);
        } else if (endPosition !== -1) {
          this.selectRangeDocuments(currentPosition, endPosition);
        }
      } else {
        this.$root.$emit('update-selection-documents-list', this.checked, this.file);
        if (this.checked) {
          this.$emit('document-selected', this.file);
        } else {
          this.$emit('document-unselected', this.file);
        }
      }
    },
    checkRangeSelect() {
      this.resetRangeSelect();
      this.rangeSelectTimer = setInterval(() => {
        this.canSelectRange = !this.isFileSelected(this.file) && this.selectedDocuments.length > 0;
      },500);
    },
    resetRangeSelect() {
      clearInterval(this.rangeSelectTimer);
      this.canSelectRange = false;
    },
    getFirstSelectedAbovePosition(currentPosition, files) {
      let position = -1;
      for (let index = currentPosition; index >= 0; index--) {
        if (this.isFileSelected(files[index])) {
          position = index;
          break;
        }
      }
      return position;
    },
    getFirstSelectedBelowPosition(currentPosition, files) {
      let position = -1;
      for (let index = currentPosition;  index < files.length; index ++)  {
        if (this.isFileSelected(files[index])) {
          position = index;
          break;
        }
      }
      return position;
    },
    initSelected() {
      if (this.selectAllChecked) {
        this.checked = true;
        this.selectDocument();
      } else {
        this.checked = this.selectedDocuments.findIndex(folder => folder.id === this.folderId) !== -1;
      }
      this.show = this.selectedDocuments.length > 0;
    },
    handleSelectAllDocuments() {
      this.selectAllChecked = true;
      this.checked = true;
      this.selectDocument();
    },
    handleShowSelectionInput(file, check) {
      if (this.fileId === file.id) {
        this.show = true;
        if (check) {
          this.checked = !this.checked;
          this.selectDocument();
        }
      }
    },
    handleHideSelectionInput(file) {
      if (this.fileId === file.id && !this.checked && !this.selectedDocuments.length) {
        this.show = false;
      }
    },
    handleUpdateSelectionList() {
      this.show = this.selectedDocuments.length > 0;
    }
  },
};
</script>