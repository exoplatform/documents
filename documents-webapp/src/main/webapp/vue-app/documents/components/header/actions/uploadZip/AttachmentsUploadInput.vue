<!--
* Copyright (C) 2023 eXo Platform SAS
*
*  This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU Affero General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU Affero General Public License
* along with this program.  If not, see <gnu.org/licenses>.
-->

<template>
  <div class="uploadFiles">
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
      uploadErrorMassage: this.$t('import.drawer.fileUpload.error'),
      fileTypes: ['zip','application/zip','application/x-zip','application/x-zip-compressed'],
      maxFilesCount: eXo.env.portal?.documentsMaxZipCount?parseInt(`${eXo.env.portal.documentsMaxZipCount}`):1,
      maxFileSize: eXo.env.portal?.documentsMaxZipSize?parseInt(`${eXo.env.portal.documentsMaxZipSize}`):eXo.env.portal?.uploadLimit?parseInt(`${eXo.env.portal.uploadLimit}`):parseInt(`${eXo.env.portal.maxFileSize}`)
    };
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
          message: this.$t('import.drawer.fileType.error'),
          type: 'error',
        });
        return;
      }
      const fileSizeInMb = file.size / this.BYTES_IN_MB;
      if (fileSizeInMb > this.maxFileSize) {
        this.$root.$emit('show-alert', {
          message: this.$t('import.drawer.maxFileSize.error').replace('{0}', `${this.maxFileSize}`),
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
            this.removeAttachedFile(file);
            this.$root.$emit('show-alert', {
              message: this.uploadErrorMassage,
              type: 'error',
            });
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
              file.uploadProgress = Number(percent);
              if (!file.uploadProgress || file.uploadProgress < 100) {
                this.controlUpload(file);
              } else {
                this.uploadingCount--;
                this.processNextQueuedUpload();
              }
            })
            .catch(() => {
              this.removeAttachedFile(file);
              this.$root.$emit('show-alert', {
                message: this.uploadErrorMassage,
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