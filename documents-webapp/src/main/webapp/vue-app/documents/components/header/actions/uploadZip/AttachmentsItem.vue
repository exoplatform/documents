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
  <div class="attachment d-flex clickable ps-1">
    <v-list-item-avatar
      class="border-radius me-3">
      <div v-if="attachmentInProgress" class="fileProgress">
        <v-progress-circular
          :rotate="-90"
          :size="40"
          :width="4"
          :value="attachment.uploadProgress"
          color="primary">
          {{ attachment.uploadProgress }}
        </v-progress-circular>
      </div>
      <div
        v-else
        class="fileType">
        <i class="uiIconFileTypeapplicationzip uiIconFileTypeDefault"></i>
      </div>
    </v-list-item-avatar>
    <v-list-item-content>
      <v-list-item-title class="uploadedFileTitle" :title="attachmentTitle">
        {{ attachmentTitle }}
      </v-list-item-title>
    </v-list-item-content>
    <v-list-item-action class="d-flex flex-row align-center">
      <div
        :title="$t('documents.label.zip.attachments.delete')"
        class="remove-button">
        <v-btn
          class="d-flex align-end"
          icon
          small
          size="24"
          @click="removeAttachedFile(attachment)">
          <i v-if="attachmentInProgress" class="uiIconCloseCircled error--text"></i>
          <v-icon
            v-else 
            small
            class="fas fa-trash error--text" />
        </v-btn>
      </div>
    </v-list-item-action>
  </div>
</template>
<script>
export default {
  props: {
    attachment: {
      type: Object,
      default: () => null
    },
  },
  computed: {
    attachmentInProgress() {
      return this.attachment.uploadProgress < 100;
    },
    attachmentTitle() {
      return this.attachment && this.attachment.name && unescape(this.attachment.name);
    },
  },
  methods: {
    removeAttachedFile: function() {
      this.$root.$emit('delete-uploaded-file', this.attachment);
    },

  }
};
</script>