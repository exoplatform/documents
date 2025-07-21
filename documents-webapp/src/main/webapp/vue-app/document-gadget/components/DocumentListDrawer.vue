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
  <exo-drawer
    id="documentListDrawer"
    ref="drawer"
    v-model="drawer"
    :loading="loading > 0"
    :right="!$vuetify.rtl"
    allow-expand
    @expand-updated="expanded = $event">
    <template #title>
      {{ $t('documents.documentGadget.title') }}
    </template>
    <template #content>
      <document-list-empty-message v-if="!hasDocuments && !loading" :title="noDocumentMessage" />
      <document-list-widget-item
        v-else
        v-for="file in fileToDisplay"
        :key="file.id"
        :file="file"
        :files="fileToDisplay" />
    </template>
    <template #footer>
      <div
        v-if="hasMore && !expanded"
        class="d-flex justify-center">
        <v-btn
          :loading="loading > 0"
          class="btn"
          block
          text
          @click="loadMore">
          {{ $t('rules.loadMore') }}
        </v-btn>
      </div>
    </template>
  </exo-drawer>
</template>
<script>
export default {
  props: {
    programId: {
      type: String,
      default: null,
    },
  },
  data: () => ({
    limit: Math.round((window.innerHeight - 122) / 53),
    pageSize: 10,
    page: 1,
    drawer: false,
    loading: 0,
    files: [],
    spaceId: eXo.env.portal.spaceId,
    expanded: false
  }),
  computed: {
    fileToDisplay() {
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
    hasDocuments() {
      return !!this.files.length;
    },
    hasMore() {
      return this.limit < this.files.length || (this.loading && !this.files.length);
    },
    settings() {
      return this.$root.settings;
    },
    documentType() {
      return this.settings?.documentType;
    },
    noDocumentMessage() {
      return this.$t(`documents.documentGadget.${this.documentType}.noDocumentMessage`);
    }
  },
  watch: {
    limit() {
      this.retrieveFiles();
    },
    loading() {
      if (this.loading) {
        this.$refs.documentListDrawer.startLoading();
      } else {
        this.$refs.documentListDrawer.endLoading();
      }
    },
  },
  created() {
    this.$root.$on('documents-list-drawer', this.open);
  },
  beforeDestroy() {
    this.$root.$off('documents-list-drawer', this.open);
  },
  methods: {
    open() {
      this.files = [];
      this.retrieveFiles();
      this.$refs.drawer.open();
    },
    loadMore() {
      if (this.hasMore) {
        this.limit += this.pageSize;
      }
    },
    retrieveFiles() {
      this.loading = true;
      const filter = {
        ownerId: eXo.env.portal.spaceIdentityId || eXo.env.portal.userIdentityId,
        listingType: this.documentType === 'sharedWithMe' ? 'FOLDER' : 'TIMELINE',
        folderPath: this.documentType === 'sharedWithMe' ? 'Documents/Shared' : null,
        favorites: this.documentType === 'favorites',
        sortField: 'lastUpdated',
      };
      return this.$documentFileService.getDocumentItems(filter, null, null, 0, this.limit + 1, null).then(files => {
        this.files = files;
      }).finally(() => this.loading = false);
    },
  },
};
</script>