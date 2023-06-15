<template>
  <div class="uploadFiles">
    <div class="d-flex align-center">
      <v-subheader class="text-sub-title pl-0 d-flex">
        {{ $t('documents.label.zip.upload') }}
      </v-subheader>
      <v-divider />
    </div>
    <div class="contentUpload d-flex flex-column">
      <div
        id="DropFileBox"
        ref="dropFileBox"
        class="dropFileBox py-10 ml-5 d-flex flex-column align-center theme--light"
        aria-controls
        @click="uploadFile">
        <i class="uiIconEcmsUploadVersion uiIcon32x32"></i>
        <v-subheader
          class="upload-drag-drop-label text-sub-title mt-3 d-flex flex-column">
          <span>{{ $t('documents.label.zip.attachments.uploadOrDrop') }}</span>
          <span>({{ $t('documents.label.zip.attachments.maxFileSize').replace('{0}', maxFileSize) }})</span>
        </v-subheader>
      </div>
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
  </div>
</template>

<script>
export default {
  props: {
    attachments: {
      type: Array,
      default: () => []
    },
  },
  data() {
    return {
      BYTES_IN_MB: 1048576,
      maxUploadInProgressCount: 2,
      uploadingFilesQueue: [],
      uploadingCount: 0,
      filesCountLimitError: false,
      fileSizeLimitError: false,
      fileSizeNullError: false,
      sameFileError: false,
      fileTypes: ['zip','application/zip','application/x-zip','application/x-zip-compressed'],
      maxFilesCount: eXo.env.portal?.documents?.maxFilesCount?parseInt(`${eXo.env.portal.documents.maxFilesCount}`):1,
      maxFileSize: eXo.env.portal?.documents?.maxZipSize?parseInt(`${eXo.env.portal.documents.maxZipSize}`):2000
    };
  },
  computed: {
    maxFileSizeErrorLabel: function () {
      return this.$t('import.drawer.maxFileSize.error').replace('{0}', `${this.maxFileSize}`);
    },
    fileTypeErrorLabel: function () {
      return this.$t('import.drawer.fileType.error');
    },

  },
  methods: {
    uploadFile: function () {
      this.$refs.uploadInput.click();
    },
    handleFileUpload: function (files) {
      const newFilesArray = Array.from(files);

      newFilesArray.sort(function (file1, file2) {
        return file1.size - file2.size;
      });

      const newAttachedFiles = [];
      newFilesArray.forEach(file => {
        const controller = new AbortController();
        const signal = controller.signal;
        newAttachedFiles.push({
          originalFileObject: file,
          name: file.name,
          size: file.size,
          mimetype: file.type,
          uploadId: this.getNewUploadId(),
          uploadProgress: 0,
          isPublic: true,
          signal: signal
        });
      });

      newAttachedFiles.forEach(newFile => {
        this.queueUpload(newFile);
      });
      this.$refs.uploadInput.value = '';
    },
    getNewUploadId: function () {
      const max = Math.floor(100000);
      const random = crypto.getRandomValues(new Uint32Array(1))[0];
      return random % max;
    },
    queueUpload: function (file) {
      if (this.attachments.length >= this.maxFilesCount) {
        this.filesCountLimitError = true;
        return;
      }
      if ( !this.fileTypes.includes(file.mimetype) ) {
        this.$root.$emit('show-alert', {
          message: this.fileTypeErrorLabel,
          type: 'error',
        });
        return;
      }
      const fileSizeInMb = file.size / this.BYTES_IN_MB;
      if (fileSizeInMb > this.maxFileSize) {
        this.$root.$emit('show-alert', {
          message: this.maxFileSizeErrorLabel,
          type: 'error',
        });
        return;
      }

      if (fileSizeInMb === 0) {
        this.fileSizeNullError = true;
        window.setTimeout(() => this.fileSizeNullError = false, 2000);
        return;
      }

      const fileExists = this.attachments.some(f => f.name === file.name);
      if (fileExists) {
        this.sameFileErrorMessage = this.sameFileErrorMessage.replace('{0}', file.name);
        this.sameFileError = true;
        return;
      }
      this.$root.$emit('add-new-uploaded-file', file);
      this.attachments.push(file);
      if (this.uploadingCount < this.maxUploadInProgressCount) {
        this.sendFileToServer(file);
      } else {
        this.uploadingFilesQueue.push(file);
      }
    },
    sendFileToServer(file) {
      if (!file.aborted) {
        this.uploadingCount++;
        this.$uploadService.upload(file.originalFileObject, file.uploadId, file.signal)
          .catch(() => {
            this.$root.$emit('attachments-notification-alert', {
              message: this.$t('attachments.link.failed'),
              type: 'error',
            });
            this.removeAttachedFile(file);
          });
        this.controlUpload(file);
      } else {
        this.processNextQueuedUpload();
      }
    },
    controlUpload(file) {
      if (file.aborted) {
        this.uploadingCount--;
        this.processNextQueuedUpload();
      } else {
        window.setTimeout(() => {
          this.$uploadService.getUploadProgress(file.uploadId)
            .then(percent => {
              if (!percent) {
                return;
              } else {
                file.uploadProgress = Number(percent);
                if (!file.uploadProgress || file.uploadProgress < 100) {
                  this.controlUpload(file);
                } else {
                  this.uploadingCount--;
                  this.processNextQueuedUpload();
                }
              }
            })
            .catch(() => {
              this.removeAttachedFile(file);
              this.$root.$emit('attachments-notification-alert', {
                message: this.$t('attachments.link.failed'),
                type: 'error',
              });
            });
        }, 200);
      }
    },
    processNextQueuedUpload: function () {
      if (this.uploadingFilesQueue.length > 0) {
        this.sendFileToServer(this.uploadingFilesQueue.shift());
      }
    },
    removeAttachedFile: function() {
      this.$root.$emit('delete-uploaded-file', this.attachment);
    },
  }
};
</script>