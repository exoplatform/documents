<template>
  <div class="attachmentsUploadBlock" :class="hasAttachments ? 'compact' : 'hero'">
    <!-- EMPTY state: dominant hero drop-zone, the whole card is clickable and
         keyboard-focusable (role=button / tabindex / Enter-Space open the picker). -->
    <div
      v-if="!hasAttachments"
      ref="uploadHero"
      class="attachmentsUploadHero d-flex flex-column align-center justify-center text-center"
      role="button"
      tabindex="0"
      :aria-label="$t('attachments.drawer.hero.title')"
      @click="uploadFile"
      @keydown.enter.prevent="uploadFile"
      @keydown.space.prevent="uploadFile">
      <v-icon size="48" color="primary">fa-cloud-upload-alt</v-icon>
      <span class="attachmentsUploadHeroTitle font-weight-bold mt-4">{{ $t('attachments.drawer.hero.title') }}</span>
      <span class="text-sub-title mt-1">{{ $t('attachments.drawer.hero.paste') }}</span>
      <span class="text-sub-title">({{ $t('attachments.drawer.maxFileSize').replace('{0}', maxFileSize) }})</span>
    </div>
    <!-- POPULATED state: compact "add" button that keeps the list room. -->
    <v-btn
      v-else
      outlined
      class="attachmentsAddBarBtn"
      :aria-label="$t('attachments.upload')"
      @click="uploadFile">
      <v-icon size="18" class="me-1">fa-cloud-upload-alt</v-icon>
      <span>{{ $t('attachments.upload') }}</span>
    </v-btn>
    <div class="fileHidden d-none">
      <input
        ref="uploadInput"
        class="file"
        name="file"
        type="file"
        multiple="multiple"
        style="display:none"
        @change="handleFileUpload($refs.uploadInput.files)">
    </div>
  </div>
</template>

<script>

export default {
  props: {
    attachments: {
      type: Array,
      default: () => []
    },
    hasAttachments: {
      type: Boolean,
      default: false
    },
    maxFilesCount: {
      type: Number,
      default: parseInt(`${eXo.env.portal.maxToUpload}`)
    },
    maxFileSize: {
      type: Number,
      default: parseInt(`${eXo.env.portal.maxFileSize}`)
    },
    currentDrive: {
      type: Object,
      default: () => null
    },
    pathDestinationFolder: {
      type: Object,
      default: () => null
    },
  },
  data() {
    return {
      MESSAGES_DISPLAY_TIME: 5000,
      BYTES_IN_MB: 1048576,
      maxUploadInProgressCount: 2,
      uploadingFilesQueue: [],
      uploadingCount: 0,
      maxProgress: 100,
      newUploadedFiles: [],
      abortUploading: false,
      uploadedFilesCount: 0
    };
  },
  computed: {
    maxFileCountErrorLabel: function () {
      return this.$t('attachments.drawer.maxFileCount.error').replace('{0}', `<b> ${this.maxFilesCount} </b>`);
    },
    maxFileSizeErrorLabel: function () {
      return this.$t('attachments.drawer.maxFileSize.error').replace('{0}', `<b> ${this.maxFileSize} </b>`);
    },
    isNewUploadedFilesEmpty() {
      return this.newUploadedFiles && this.newUploadedFiles.length === 0;
    },
    uploadFinished() {
      return !this.isNewUploadedFilesEmpty && this.newUploadedFiles.every(file => file.uploadProgress && file.uploadProgress === 100);
    },
  },
  watch: {
    uploadFinished() {
      if (this.uploadFinished && this.uploadingCount === 0) {
        this.$root.$emit('link-new-added-attachments');
        this.uploadedFilesCount += this.newUploadedFiles.length;
        this.newUploadedFiles = [];
      }
    }
  },
  created() {
    // Drag & drop is now handled by the parent drawer (scoped to the drawer
    // element and cleaned up on destroy), which forwards dropped files here via
    // the existing 'handle-provided-files' event.
    this.$root.$on('handle-pasted-files-from-clipboard', this.handleFileUpload);
    this.$root.$on('reset-attachments-upload-input', () => this.resetUploadInput());
    this.$root.$on('abort-attachments-new-upload', () => this.abortUploadingNewAttachments());
    this.$root.$on('abort-uploading-new-file', this.abortUploadingNewFile);
    this.$root.$on('handle-provided-files', files => this.handleFileUpload(files));
    this.$root.$on('retry-upload-file', this.retryUpload);
    this.$root.$on('attachment-continue-upload', (file) => {
      this.sendFileToServer(file, true);
    });
  },
  beforeDestroy() {
    this.$root.$off('handle-pasted-files-from-clipboard', this.handleFileUpload);
    this.$root.$off('reset-attachments-upload-input', this.resetUploadInput);
    this.$root.$off('abort-attachments-new-upload', this.abortUploadingNewAttachments);
    this.$root.$off('abort-uploading-new-file', this.abortUploadingNewFile);
    this.$root.$off('handle-provided-files', this.handleFileUpload);
    this.$root.$off('retry-upload-file', this.retryUpload);
  },
  methods: {
    uploadFile: function () {
      this.$refs.uploadInput.click();
    },
    handleFileUpload: function (files) {
      if (this.$refs.uploadInput){
        this.abortUploading = false;
        const newFilesArray = Array.from(files);

        newFilesArray.sort(function (file1, file2) {
          return file1.size - file2.size;
        });

        this.newUploadedFiles = [];
        const newAttachedFiles = [];
        newFilesArray.forEach(file => {
          const controller = new AbortController();
          const signal = controller.signal;
          newAttachedFiles.push({
            originalFileObject: file,
            fileDrive: this.currentDrive,
            title: file.name,
            size: file.size,
            mimetype: file.type,
            acl: file.acl,
            uploadId: this.getNewUploadId(),
            uploadProgress: 0,
            destinationFolder: file.destinationFolder? `${this.pathDestinationFolder}/${file.destinationFolder}` :this.pathDestinationFolder,
            pathDestinationFolderForFile: '',
            isPublic: true,
            source: 'device',
            uploadFailed: false,
            signal: signal
          });
        });

        const existingAttachedFiles = newAttachedFiles.filter(file => this.attachments.some(f => f.title === file.title && f.destinationFolder === file.destinationFolder));
        if (existingAttachedFiles.length > 0) {
          const existingFiles = existingAttachedFiles.length === 1 ? existingAttachedFiles.map(file => file.title) : existingAttachedFiles.length;
          let sameFileErrorMessage = existingAttachedFiles.length === 1 ? this.$t('attachments.drawer.sameFile.error') : this.$t('attachments.drawer.sameFiles.error');
          sameFileErrorMessage = sameFileErrorMessage.replace('{0}', `<b> ${existingFiles} </b>`);
          document.dispatchEvent(new CustomEvent('alert-message', {detail: {
            useHtml: true,
            alertType: 'error',
            alertMessage: sameFileErrorMessage,
          }}));
        }

        newAttachedFiles.filter(file => !this.attachments.some(f => f.title === file.title)).every((newFile, index) => {
          if (index === this.maxFilesCount || this.maxFilesCount === 0 || this.uploadedFilesCount >= this.maxFilesCount) {
            document.dispatchEvent(new CustomEvent('alert-message', {detail: {
              useHtml: true,
              alertType: 'error',
              alertMessage: this.maxFileCountErrorLabel,
            }}));
            return false;
          } else {
            this.queueUpload(newFile);
            return true;
          }
        });
        this.$refs.uploadInput.value = null;
      
      }
    },
    queueUpload: function (file) {
      const fileSizeInMb = file.size / this.BYTES_IN_MB;
      if (fileSizeInMb > this.maxFileSize) {
        document.dispatchEvent(new CustomEvent('alert-message', {detail: {
          useHtml: true,
          alertType: 'error',
          alertMessage: this.maxFileSizeErrorLabel
        }}));
        return;
      }
      this.checkExistenceActions(file).then(actions => {
        if (actions.length > 0) {
          file.actions = actions;
          file.waitAction = true;
          this.$root.$emit('alert-message', this.$root.$t('attachments.upload.conflict.message'), 'warning');
          this.$root.$emit('start-loading-attachment-drawer');
        }
        this.$root.$emit('add-new-uploaded-file', file);
        this.newUploadedFiles.push(file);

        if (this.uploadingCount < this.maxUploadInProgressCount) {
          this.sendFileToServer(file);
        } else {
          this.uploadingFilesQueue.push(file);
        }
      });
    },
    checkExistenceActions(file) {
      const actions = [];
      const pathDestinationFolder = file.destinationFolder? file.destinationFolder :this.pathDestinationFolder;
      return this.$attachmentService.checkExistence(this.currentDrive.name, 'collaboration', pathDestinationFolder, file.title).then((data) => {
        const exist = data && data.firstChild;
        const versioned = exist && exist.firstChild;
        if (exist && exist.tagName === 'Existed') {
          actions.push('keepBoth');
        }
        if (versioned && (versioned.tagName === 'Versioned' || versioned.tagName === 'CanVersioning')) {
          actions.push('createVersion');
        }
        return actions;
      });
    },
    sendFileToServer(file, continueAction) {
      if (!file.aborted && !file.waitAction) {
        this.uploadingCount++;
        this.$uploadService.upload(file.originalFileObject, file.uploadId, file.signal)
          .then(() => delete file.originalFileObject)
          .catch(() => {
            this.$root.$emit('alert-message', this.$t('attachments.link.failed'), 'error');
            this.markUploadFailed(file);
          });
        this.controlUpload(file, continueAction);
      } else {
        this.processNextQueuedUpload();
      }
    },
    controlUpload(file, continueAction) {
      if (file.aborted) {
        this.uploadingCount--;
        this.processNextQueuedUpload();
      } else {
        if (file.uploadId) {
          window.setTimeout(() => {
            if (!file.countFirstProgressError) {
              file.countFirstProgressError=0;
            }
            this.$uploadService.getUploadProgress(file.uploadId)
              .then(percent => {
                delete file.countFirstProgressError;
                if (this.abortUploading) {
                  return;
                } else {
                  file.uploadProgress = file.inProcess && 100 || Number(percent);
                  if (!file.uploadProgress || file.uploadProgress < 100) {
                    this.controlUpload(file, continueAction);
                  } else {
                    this.uploadingCount--;
                    this.processNextQueuedUpload();
                  }
                  if (file.uploadProgress === 100 && !file.inProcess) {
                    file.inProcess = true;
                    this.$root.$emit('continue-upload-to-destination-path', file);
                    const index = this.newUploadedFiles.findIndex(f => f.id === file.id);
                    this.newUploadedFiles.splice(index, 1);
                  }
                }
              })
              .catch((err) => {
                if (err.message==='Uploaded resource not found' && file.countFirstProgressError != null && file.countFirstProgressError < 5) {
                  //if the upload request take more than 200ms to initialize, then the progress request arrives too early.
                  //In this case, redo the progress, until 5 errors to be sure
                  file.countFirstProgressError++;
                  this.controlUpload(file, continueAction);
                } else {
                  this.markUploadFailed(file);
                  this.$root.$emit('alert-message', this.$t('attachments.link.failed'), 'error');
                }
              });
          }, 200);
        }
      }
    },
    processNextQueuedUpload: function () {
      if (this.uploadingFilesQueue.length > 0) {
        this.sendFileToServer(this.uploadingFilesQueue.shift());
      }
    },
    getNewUploadId: function () {
      const maxUploadId = 100000;
      return Math.floor(Math.random() * maxUploadId);
    },
    removeAttachedFile(file) {
      this.$root.$emit('remove-attachment-item', file);
    },
    markUploadFailed(file) {
      // Keep the failed item in the list (with an inline error + Retry) instead
      // of silently removing it.
      this.$set(file, 'uploadFailed', true);
      this.$set(file, 'uploadProgress', 0);
      if (this.uploadingCount > 0) {
        this.uploadingCount--;
      }
      const queueIndex = this.uploadingFilesQueue.findIndex(f => f.uploadId === file.uploadId);
      if (queueIndex !== -1) {
        this.uploadingFilesQueue.splice(queueIndex, 1);
      }
      this.processNextQueuedUpload();
    },
    retryUpload(file) {
      if (!file || !file.uploadFailed) {
        return;
      }
      this.$set(file, 'uploadFailed', false);
      this.$set(file, 'uploadProgress', 0);
      file.aborted = false;
      delete file.inProcess;
      delete file.countFirstProgressError;
      const controller = new AbortController();
      file.signal = controller.signal;
      if (this.newUploadedFiles.findIndex(f => f.uploadId === file.uploadId) === -1) {
        this.newUploadedFiles.push(file);
      }
      if (this.uploadingCount < this.maxUploadInProgressCount) {
        this.sendFileToServer(file);
      } else {
        this.uploadingFilesQueue.push(file);
      }
    },
    resetUploadInput() {
      this.newUploadedFiles = [];
      this.uploadingCount = 0;
      this.uploadedFilesCount = 0;
    },
    abortUploadingNewAttachments() {
      this.resetUploadInput();
      this.abortUploading = true;
    },
    abortUploadingNewFile(file) {
      if (file && file.uploadId) {
        const fileIndex = this.newUploadedFiles.findIndex(f => f.uploadId === file.uploadId);
        this.newUploadedFiles.splice(fileIndex, 1);
      }
      file.aborted = true;
      this.$uploadService.abortUpload(file.uploadId);
    }
  }
};
</script>
