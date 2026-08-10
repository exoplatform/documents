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
  <v-app>
    <v-hover v-model="hover">
      <widget-wrapper
        :loading="loading"
        extra-class="application-body">
        <template #title>
          <span
            class="text-header">
            <span class="text-truncate">{{ headerTitle }}</span>
          </span>
        </template>
        <template #action>
          <div>
            <v-btn
              v-if="!hover && displaySeeMore"
              color="primary"
              class="pa-0 text-font-size"
              small
              text
              link>
              <span class="primary--text text-none">
                {{ $t('documents.documentGadget.seeMore') }}
              </span>
            </v-btn>
            <div v-else>
              <v-btn
                v-if="hoverEdit"
                :title="$t('documents.documentGadget.editTooltip')"
                class="text-font-size"
                small
                link
                icon
                @click="$root.$emit('document-gadget-settings')">
                <v-icon
                  size="18">
                  fas fa-cog
                </v-icon>
              </v-btn>
              <v-btn
                v-if="filesCount"
                :title="$t('documents.documentGadget.seeMore')"
                color="primary"
                class="text-font-size"
                small
                link
                icon
                @click="$refs.listDrawer.open()">
                <v-icon
                  size="18">
                  fas fa-external-link-alt
                </v-icon>
              </v-btn>
            </div>
          </div>
        </template>
        <div v-if="filesCount">
          <v-list :class="!isCardsView && !!filesToDisplay && 'pb-4'" class="pa-0">
            <template v-if="isCardsView">
              <card-carousel parent-class="activity-files-parent px-4">
                <document-list-widget-item-card
                  v-for="(file, index) in filesToDisplay"
                  :index="index"
                  :count="filesCount"
                  :key="file.id"
                  :file="file"
                  :files="filesToDisplay"
                  extra-margin />
              </card-carousel>
            </template>
            <template v-else>
              <document-list-widget-item
                v-for="file in filesToDisplay"
                :key="file.id"
                :file="file"
                :files="filesToDisplay" />
            </template>
          </v-list>        
        </div>
        <v-card
          v-else
          class="d-flex flex-column flex-grow-1 justify-center align-center"
          min-height="188"
          flat>
          <document-list-empty-message
            v-if="displayPlaceholder"
            :title="$t('documents.documentGadget.placeholder')" />
        </v-card>
      </widget-wrapper>
    </v-hover>
    <document-list-settings-drawer v-if="canEdit" @settings-updated="settingsUpdated" />
    <document-list-drawer ref="listDrawer" />
  </v-app>
</template>
<script>
export default {
  data: () => ({
    hover: false,
    loading: false,
    applicationMounted: false,
    files: [],
  }),
  computed: {
    settings() {
      return this.$root.settings;
    },
    canEdit() {
      return this.settings?.canEdit;
    },
    hoverEdit() {
      return this.hover && this.canEdit;
    },
    filesCount() {
      return this.files.length;
    },
    displayPlaceholder() {
      return !this.filesCount && !this.loading;
    },
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
    isCardsView() {
      return this.settings?.viewOptions === 'cards';
    },
    customHeader() {
      return this.settings?.customHeader;
    },
    headerTitle() {
      return this.customHeader ? this.settings?.headerTitle : this.settings?.documentType === 'recentDocument' ? this.$t('documents.documentGadget.recentDocumentTitle') : this.settings?.documentType === 'sharedWithMe' ? this.$t('documents.documentGadget.sharedWithMeTitle') :  this.$t('documents.documentGadget.title');
    },
    maxDocumentsToList() {
      return this.settings.maxDocumentsToList;
    },
    displaySeeMore() {
      return this.settings.displaySeeMore;
    },
    documentType() {
      return this.settings.documentType;
    },
    selectedCategoryIds() {
      return this.settings.categoryIds;
    },
    excludedCategoryIds() {
      return this.settings.excludeCategoryIds;
    },
    selectedFoldersId() {
      return this.settings?.selectedFoldersId;
    },
    spaceIdentityId() {
      return this.settings.spaceIdentityId;
    },
  },
  created() {
    this.$root.$on('documents-preview', this.previewDocument);
    this.getFiles()
      .finally(() => {
        this.$root.$applicationLoaded();
      });
  },
  mounted() {
    this.applicationMounted = true;
  },
  methods: {
    getFiles() {
      if (!this.maxDocumentsToList) {
        this.files = [];
        return;
      }
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
      return this.$documentFileService.getDocumentItems(filter, this.selectedCategoryIds, this.excludedCategoryIds, 0, this.maxDocumentsToList, null).then(files => {
        this.files = files.filter(file => !file.folder);
      }).finally(() => this.loading = false);
    },
    settingsUpdated(settings, headerTitle, refreshList) {
      this.$root.settings.maxDocumentsToList = settings.maxDocumentsToList;
      this.$root.settings.viewOptions = settings.viewOptions;
      this.$root.settings.documentType = settings.documentType;
      this.$root.settings.customHeader = settings.customHeader;
      this.$root.settings.displaySeeMore = settings.displaySeeMore;
      this.$root.settings.spaceIdentityId = settings.spaceIdentityId;
      this.$root.settings.selectedFoldersId = settings.selectedFoldersId;
      this.$root.settings.headerTitle = headerTitle;
      this.$root.settings.displayAccessDrive = settings.displayAccessDrive;
      this.$root.settings.driveUrl = settings.driveUrl;
      this.$root.settings.opensInSameTab = settings.opensInSameTab;
      if (refreshList) {
        this.getFiles();
      }
    },
    previewDocument(file,files) {
      const attachments = [];
      files = files && files.length > 0 ? files : this.files;
      files.forEach((item) => {
        if (!item.folder && Vue.prototype?.$supportedDocuments.filter(doc =>doc.mimeType === item.mimeType).length === 0){
          attachments.push({'id': item.id,'filename': item.name,'mimetype': item.mimeType || item.mimetype,'source': 'documents','downloadUrl': `/rest/v1/documents/content/${item.id}`, 'icon': this.$root.getFileIcon(item)});}
      });
      document.dispatchEvent(new CustomEvent('open-attachments-preview', {detail: {'attachments': attachments,'id': file.id }}));
    },
  },
};
</script>