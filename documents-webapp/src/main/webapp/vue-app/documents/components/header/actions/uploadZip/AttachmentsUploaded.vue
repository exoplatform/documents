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
  <div class="uploadedFiles">
    <div class="uploadedFiles ma-2">
      <div v-if="attachments.length === 0" class="no-files-attached d-flex flex-column align-center text-sub-title">
        <div class="d-flex pl-6 not-files-icon">
          <i class="uiIconAttach uiIcon64x64"></i>
          <i class="uiIconCloseCircled uiIcon32x32"></i>
        </div>
        <span>{{ $t('documents.label.zip.no.attachments') }}</span>
      </div>
      <div class="uploadedFilesItems">
        <div
          v-for="attachedFile in attachments"
          :key="attachedFile.name"
          class="uploadedFilesItem">
          <div class="showDestination">
            <div class="showFile">
              <documents-zip-item :attachment="attachedFile" />
            </div>
          </div>
        </div>
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
  methods: {
    removeAttachedFile: function() {
      this.$root.$emit('delete-uploaded-file', this.attachments);
    },
  },
};
</script>