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
    :right="!$vuetify.rtl">
    <template #title>
      {{ $t('documents.documentGadget.title') }}
    </template>
    <template #titleIcons>
      <v-btn
        :title="$t('documents.documentGadget.seeMore')"
        v-if="$root.settings.displayAccessDrive"
        link
        icon
        @click="openDrive">
        <v-icon
          size="20px">
          fas fa-external-link-alt
        </v-icon>
      </v-btn> 
    </template>
    <template #content>
      <document-list-empty-message v-if="!hasDocuments && !loading" :title="noDocumentMessage" />
      <document-list-widget-item
        v-else
        v-for="file in filesToDisplay"
        :key="file.id"
        :file="file"
        :files="filesToDisplay" />
    </template>
    <template #footer>
      <div
        v-if="hasMore"
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
  }),
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
    selectedCategoryIds() {
      return this.settings?.categoryIds;
    },
    excludedCategoryIds() {
      return this.settings?.excludeCategoryIds;
    },
    selectedFoldersId() {
      return this.settings?.selectedFoldersId;
    },
    noDocumentMessage() {
      return this.$t(`documents.documentGadget.${this.documentType}.noDocumentMessage`);
    },
    spaceIdentityId() {
      return this.settings?.spaceIdentityId;
    },
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
      const folderPath = eXo.env.portal.spaceIdentityId ? 'Shared' : 'Documents/Shared';
      const filter = {
        ownerId: this.spaceIdentityId ? this.spaceIdentityId : eXo.env.portal.spaceIdentityId || eXo.env.portal.userIdentityId,
        listingType: this.spaceIdentityId ? 'TIMELINE' : this.documentType === 'sharedWithMe' ? 'FOLDER' : 'TIMELINE',
        folderPath: this.spaceIdentityId ? null : this.documentType === 'sharedWithMe' ? folderPath : null,
        favorites: this.spaceIdentityId ? false : this.documentType === 'favorites',
        sortField: 'lastUpdated',
        parentFolderId: this.selectedFoldersId,
      };
      return this.$documentFileService.getDocumentItems(filter, this.selectedCategoryIds, this.excludedCategoryIds, 0, this.limit + 1, null).then(files => {
        this.files = files.filter(file => !file.folder);
      }).finally(() => this.loading = false);
    },
    openDrive() {
      const target = this.$root?.settings?.opensInSameTab ? '_self' : '_blank';
      const url = this.$root?.settings?.driveUrl;
      this.$documentsUtils.openLink(url, target);
    },
  },
};
</script>