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
  <v-hover v-slot="{ hover }">
    <v-list-item
      class="pa-0 px-3"
      @click="openPreview">
      <v-list-item-avatar
        class="my-0"
        tile>
        <v-avatar tile>
          <v-icon
            :color="fileIconColor"
            size="34">
            {{ fileIconClass }}
          </v-icon>
        </v-avatar>
      </v-list-item-avatar>
      <v-list-item-content
        class="pa-0">
        <v-list-item-title class="text-color text-truncate text-wrap spaceTitle">
          {{ fileName }}
        </v-list-item-title>
        <v-list-item-subtitle>
          <date-format
            :value="modifiedDate"
            :format="dateFormat" />
        </v-list-item-subtitle>
      </v-list-item-content>
      <v-list-item-action v-if="hover" class="ma-0">
        <v-btn
          icon
          @click.stop="showInfo()">
          <v-icon size="20">fa-info-circle</v-icon>
        </v-btn>
      </v-list-item-action>
    </v-list-item>
  </v-hover>
</template>
<script>
export default {
  props: {
    file: {
      type: Object,
      default: () => null,
    },
    files: {
      type: Array,
      default: () => []
    }
  },
  data: () => ({
    loading: false,
    dateFormat: {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
    },
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
    mimeType() {
      return this.file?.mimeType;
    },
    isFileEditable() {
      const type = this.mimeType || '';
      return this.$supportedDocuments && this.$supportedDocuments.filter(doc => doc.edit && doc.mimeType === type && !this.file.cloudDriveFile).length > 0;
    },
    canEdit() {
      return this.file?.acl?.canEdit;
    },
    isFileOnlyReadable() {
      const type = this.mimeType || '';
      return this.$supportedDocuments && this.$supportedDocuments.filter(doc => !doc.edit && doc.mimeType === type && !this.file.cloudDriveFile).length > 0;
    },
    modifiedDate() {
      return this.file?.modifiedDate || this.file?.createdDate;
    },
  },
  methods: {
    openPreview() {
      this.loading = true;
      if (this.file?.folder) {
        this.$root.$emit('document-open-folder', this.file);
      } else if (this.isFileEditable)  {
        if (this.canEdit) {
          this.$root.openInEditMode(this.file);
        } else {
          this.$root.openInReadOnlyMode(this.file);
        }        
      } else if (this.isFileOnlyReadable) {
        this.$root.openInReadOnlyMode(this.file);
      } else {
        this.$root.$emit('documents-preview', this.file, this.files);
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
  }
};
</script>