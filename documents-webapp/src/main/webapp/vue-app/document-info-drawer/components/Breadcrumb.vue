/*
 * Copyright (C) 2025 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program; if not, write to the Free Software Foundation,
 */

<template>
  <div v-if="breadcrumbLength">
    <div class="d-flex align-center">
      <div
        id="breadcrumb-list-items"
        data-isfolder="true"
        class="pa-0 d-flex width-fit-content">
        <div
          v-for="(folder, index) in documentsBreadcrumbToDisplay"
          :key="index"
          :data-fileId="folder.id"
          class="d-flex text-truncate">
          <v-tooltip max-width="300" bottom>
            <template #activator="{ on, attrs }">
              <v-btn
                height="20px"
                min-width="20px"
                class="pa-0"
                icon
                v-bind="attrs"
                v-on="on"
                @click="openFolder(folder)">
                <v-icon
                  v-if="folder.ellipsis"
                  size="16"
                  class="pe-1 not-clickable">
                  fas fa-ellipsis-h
                </v-icon>
                <v-card
                  v-else-if="index>0"
                  :max-width=" breadcrumbLength < 3 ? '320px' : breadcrumbLength === 3 ? '155px' : '130px'"
                  class="no-border elevation-0">
                  <a  
                    class="text-truncate text-wrap text-break clickable font-weight-bold show hover-underline">
                    {{ folder.name }}
                  </a>
                </v-card>
                <v-icon
                  v-else
                  size="18"
                  class="pa-0 pe-4">
                  fas fa-home
                </v-icon>
                <v-icon
                  v-if="folder.symlink"
                  size="10"
                  class="pe-1">
                  mdi-link-variant
                </v-icon>
              </v-btn>
            </template>
            <span class="caption">
              {{ getName(folder) }}
              <v-icon
                v-if="folder.symlink"
                size="10"
                class="pe-1">
                mdi-link-variant
              </v-icon>
            </span>
          </v-tooltip>
          <v-icon
            v-if="index < breadcrumbLength-1"
            size="12"
            class="px-1">
            fa-chevron-right
          </v-icon>
        </div>
      </div>
    </div>
  </div>
</template>
<script>

export default {
  props: {
    folderId: {
      type: String,
      default: '',
    },
    isMobile: {
      type: Boolean,
      default: false
    }
  }, 

  data: () => ({
    documentsBreadcrumb: [],
    documentsBreadcrumbToDisplay: [],
    breadcrumbLength: 0,
    folderPath: '',
    ownerId: eXo.env.portal.spaceIdentityId || eXo.env.portal.userIdentityId
  }),
  created() {
    this.$root.$on('update-breadcrumb',fileId => {
      this.getBreadCrumbs(fileId);
    }); 
    this.getBreadCrumbs(this.folderId);
  },
  methods: {
    openFolder(folder) {
      if (folder.ellipsis) {
        return;
      }
      this.$root.$emit('open-folder', folder);
    },
    getBreadCrumbs(fileId) {
      return this.$documentFileService
        .getBreadCrumbs(fileId,this.ownerId)
        .then(breadCrumbs => {
          this.documentsBreadcrumb = breadCrumbs;
          this.documentsBreadcrumbToDisplay = this.getDocumentsBreadcrumbToDisplay();
        })
        .finally(() => this.loading = false);
    },
    getDocumentsBreadcrumbToDisplay() {
      if (!this.documentsBreadcrumb || this.documentsBreadcrumb.length <= 3) {
        this.breadcrumbLength = this.documentsBreadcrumb.length;
        return this.documentsBreadcrumb || [];
      } else {
        const length = this.documentsBreadcrumb.length;
        const documentsBreadcrumbToDisplay = [this.documentsBreadcrumb[0], ... this.documentsBreadcrumb.slice(length - 3, length)];
        documentsBreadcrumbToDisplay[1] = Object.assign({}, documentsBreadcrumbToDisplay[1], {
          name: '...',
          ellipsis: true,
        });
        this.breadcrumbLength = documentsBreadcrumbToDisplay.length;
        return documentsBreadcrumbToDisplay;
      }
    },
    getName(folder){
      if (folder.ellipsis){
        return `${this.$t('documents.label.parentFolders')}: ${this.documentsBreadcrumb.slice(1, this.documentsBreadcrumb.length - 2).map(item => item.name).join(' , ')}`;
      }
      if (folder.name==='Private'){
        return `${this.$t('documents.label.access')} ${this.$t('documents.label.userHomeDocuments')}`;
      } else if (folder.name==='Documents'){
        return `${this.$t('documents.label.access')} ${this.$t('documents.label.spaceHomeDocuments')}`;
      }
      return `${this.$t('documents.label.access')} ${folder.name}`;
    },
  }
};
</script>
