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
  <div class="d-flex flex-wrap border-box-sizing">
    <div
      v-for="(file, index) in filesToDisplay"
      :key="file.id"
      class="flex-grow-1 flex-shrink-0 col-3 mb-3 pa-0">
      <documents-item-card
        :index="index"
        :count="filesCount"
        :key="file.id"
        :file="file"
        :files="files" />
    </div>
    <v-col
      v-if="hasMore"
      cols="12"
      class="px-3">
      <v-btn
        :loading="loading"
        class="loadMoreButton btn"
        block
        @click="$root.$emit('document-load-more')">
        {{ $t('documents.loadMore') }}
      </v-btn>
    </v-col>
  </div>
</template>
<script>
export default {
  props: {
    files: {
      type: Array,
      default: null,
    },
    hasMore: {
      type: Boolean,
      default: false,
    },
    loading: {
      type: Boolean,
      default: false,
    },
  },
  computed: {
    filesToDisplay() {
      const files = this.files ?? [];
      return files.map(file => {
        const decodedName = this.$root.safeDecodeURIComponent(file.name);
        return {
          id: file.id,
          name: decodedName,
          filename: decodedName,
          modifiedDate: file?.modifiedDate,
          createdDate: file?.createdDate,
          mimetype: file?.mimeType,
          sourceID: file?.sourceID,
          acl: file?.acl,
          image: this.$root.getImageUrl(file),
          downloadUrl: this.$root.getDownloadUrl(file),
          icon: this.$root.getFileIcon(file),
          editable: this.$root.isFileEditable(file),
          readable: this.$root.isFileReadable(file),
          path: file?.docPath,
          source: 'documents',
        };
      });
    },
    filesCount() {
      return this.files && this.files.length || 0;
    },
  },
};
</script>