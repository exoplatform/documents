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
  <v-list-item
    class="pa-1 pb-1"
    @click="openPreview">
    <v-list-item-avatar
      class="my-0"
      tile>
      <v-avatar :size="avatarSize" tile>
        <v-icon
          :color="fileIconColor"
          size="24">
          {{ fileIcon }}
        </v-icon>
      </v-avatar>
    </v-list-item-avatar>
    <v-list-item-content
      class="pa-0">
      <v-list-item-title class="text-color text-truncate-2 text-wrap spaceTitle">
        {{ fileName }}
      </v-list-item-title>
    </v-list-item-content>
  </v-list-item>
</template>
<script>
export default {
  props: {
    file: {
      type: Object,
      default: () => null,
    },
  },
  data: () => ({
    loading: false,
    avatarSize: 37,
  }),
  computed: {
    extension() {
      return this.$documentsIconsExtension?.[0]?.get?.(this.file?.mimeType);
    },
    canPreview() {
      return this.extension?.canPreview;
    },
    fileName() {
      return this.file?.name;
    },
    fileIcon() {
      return this.extension?.class || 'fas fa-file';
    },
    fileIconColor() {
      return this.extension?.color || 'secondary';
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
  methods: {
    openPreview() {
      this.loading = true;
      if (this.file?.folder) {
        this.$root.$emit('document-open-folder', this.file);
      } else if (this.isFileEditable) {
        this.openInEditMode(this.file);
      } else if (this.isFileOnlyReadable) {
        this.openInReadOnlyMode(this.file);
      } else {
        this.$root.$emit('documents-preview', this.file);
      }
      this.loading = false;
      this.$root.$emit('mark-document-as-viewed', this.file);
    },
    openInEditMode(file) {
      const fileId = file.sourceID ? file.sourceID : file.id;
      window.open(`${eXo.env.portal.context}/${eXo.env.portal.metaPortalName}/oeditor?docId=${fileId}&backTo=${window.location.pathname}`, '_blank');
    },
    openInReadOnlyMode(file) {
      const fileId = file.sourceID ? file.sourceID : file.id;
      window.open(`${eXo.env.portal.context}/${eXo.env.portal.metaPortalName}/oeditor?docId=${fileId}&mode=view&backTo=${window.location.pathname}`, '_blank');
    },
  }
};
</script>