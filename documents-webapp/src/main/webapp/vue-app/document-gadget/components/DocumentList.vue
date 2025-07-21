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
        extra-class="application-body position-static border-box-sizing"
        no-margin>
        <template #title>
          <div class="d-flex flex-grow-1 flex-shrink-1 width-full justify-space-between align-center position-relative pa-5">
            <div
              class="widget-text-header text-none text-truncate d-flex align-center">
              {{ headerTitle }}
            </div>
            <div
              :class="{
                'l-0': $vuetify.rtl,
                'r-0': !$vuetify.rtl,
              }"
              class="position-absolute absolute-vertical-center pe-5 z-index-one">
              <v-btn
                v-if="displaySeeMore"
                :icon="hoverEdit"
                :small="hoverEdit"
                height="auto"
                min-width="auto"
                class="pa-0"
                text
                @click="$refs.listDrawer.open()">
                <v-icon
                  v-if="hoverEdit"
                  size="18"
                  color="primary">
                  fa-external-link-alt
                </v-icon>
                <span v-if="!hoverEdit && displaySeeMore" class="primary--text text-none">{{ $t('documents.documentGadget.seeMore') }}</span>
              </v-btn>
              <v-fab-transition hide-on-leave>
                <v-btn
                  v-show="hoverEdit"
                  :title="$t('documents.documentGadget.editTooltip')"
                  small
                  icon
                  @click="$root.$emit('document-gadget-settings')">
                  <v-icon size="18">fa-cog</v-icon>
                </v-btn>
              </v-fab-transition>
            </div>
          </div>
        </template>
        <template v-if="initialized" #default>
          <div>
            <v-list :class="!isCardsView && !!filesToDisplay && 'pb-4'" class="pa-0">
              <template v-if="isCardsView">
                <card-carousel parent-class="activity-files-parent px-4">
                  <document-list-widget-item-card
                    v-for="(file, index) in filesToDisplay"
                    :index="index"
                    :count="filesCount"
                    :key="file.id"
                    :file="file"
                    :files="filesToDisplay" />
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
        </template>
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
    initialized: false,
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
      return this.customHeader ? this.settings?.headerTitle : this.$t('documents.documentGadget.title');
    },
    maxDocumentsToList() {
      return this.settings.maxDocumentsToList;
    },
    displaySeeMore() {
      return this.settings.displaySeeMore;
    },
    documentType() {
      return this.settings.documentType;
    }
  },
  watch: {
    loading() {
      if (!this.loading) {
        this.initialized = true;
      }
    },
    initialized() {
      if (this.initialized) {
        this.$root.$applicationLoaded();
      }
    },
  },
  created() {
    this.$root.$on('documents-preview', this.previewDocument);
    this.getFiles();
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
        ownerId: eXo.env.portal.spaceIdentityId || eXo.env.portal.userIdentityId,
        listingType: this.documentType === 'sharedWithMe' ? 'FOLDER' : 'TIMELINE',
        folderPath: this.documentType === 'sharedWithMe' ? folderPath : null,
        favorites: this.documentType === 'favorites',
        sortField: 'lastUpdated',
      };
      return this.$documentFileService.getDocumentItems(filter, null, null, 0, this.maxDocumentsToList, null).then(files => {
        this.files = files;
      }).finally(() => this.loading = false);
    },
    settingsUpdated(settings, headerTitle, refreshList) {
      this.$root.settings.maxDocumentsToList = settings.maxDocumentsToList;
      this.$root.settings.viewOptions = settings.viewOptions;
      this.$root.settings.documentType = settings.documentType;
      this.$root.settings.customHeader = settings.customHeader;
      this.$root.settings.displaySeeMore = settings.displaySeeMore;
      this.$root.settings.headerTitle = headerTitle;
      if (refreshList) {
        this.getFiles();
      }
    },
    previewDocument(files, file) {
      const attachments = [];
      files.forEach((item) => {
        if (!item.folder && Vue.prototype?.$supportedDocuments.filter(doc =>doc.mimeType === item.mimeType).length === 0){
          attachments.push({'id': item.id,'filename': item.name,'mimetype': item.mimeType,'source': 'documents','downloadUrl': `/rest/v1/documents/content/${item.id}`, 'icon': this.$root.getFileIcon(item)});}
      });
      document.dispatchEvent(new CustomEvent('open-attachments-preview', {detail: {'attachments': files,'id': file.id }}));
    },
  },
};
</script>