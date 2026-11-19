<!--

  Copyright (C) 2026 eXo Platform SAS.

  This program is free software; you can redistribute it and/or
  modify it under the terms of the GNU Affero General Public License
  as published by the Free Software Foundation; either version 3
  of the License, or (at your option) any later version.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with this program; if not, see<http://www.gnu.org/licenses/>.

-->
<template>
  <exo-drawer
    id="DocumentsPublicLinksDrawer"
    ref="drawer"
    :loading="loading"
    right
    @closed="$emit('closed')">
    <template #title>
      {{ $t('documents.spaceSettings.publicLinks.title', {0: size}) }}
    </template>
    <template #content>
      <v-list v-if="documents.length" dense>
        <v-list-item
          v-for="document in documents"
          :key="document.id"
          :href="documentUrl(document)"
          target="_blank"
          rel="noopener noreferrer"
          class="documents-public-link-item">
          <v-list-item-icon class="me-3">
            <v-icon
              :color="iconOf(document).color"
              size="22">
              {{ iconOf(document).class }}
            </v-icon>
          </v-list-item-icon>
          <v-list-item-content>
            <v-list-item-title :title="document.name">
              {{ document.name }}
            </v-list-item-title>
          </v-list-item-content>
        </v-list-item>
      </v-list>
      <div
        v-else-if="failed"
        class="pa-5 text-sub-title">
        {{ $t('documents.spaceSettings.publicLinks.error') }}
      </div>
      <div
        v-else-if="!loading"
        class="pa-5 text-sub-title">
        {{ $t('documents.spaceSettings.publicLinks.empty') }}
      </div>
    </template>
    <template v-if="hasMore" #footer>
      <div class="d-flex justify-center">
        <v-btn
          id="DocumentsPublicLinksLoadMore"
          :loading="loading"
          class="btn"
          @click="loadMore">
          {{ $t('documents.spaceSettings.publicLinks.loadMore') }}
        </v-btn>
      </div>
    </template>
  </exo-drawer>
</template>
<script>
const PAGE_SIZE = 20;

export default {
  props: {
    spaceId: {
      type: [String, Number],
      default: null,
    },
  },
  data: () => ({
    documents: [],
    size: 0,
    loading: false,
    failed: false,
  }),
  computed: {
    hasMore() {
      return this.documents.length < this.size;
    },
  },
  methods: {
    // size: the count the confirmation shows, until the first page answers
    open(size) {
      this.documents = [];
      this.size = size || 0;
      this.failed = false;
      this.$refs.drawer.open();
      return this.loadPage(0);
    },
    loadMore() {
      return this.loadPage(this.documents.length);
    },
    loadPage(offset) {
      this.loading = true;
      return this.$spaceDocumentsExposureService.getPublicLinkDocuments(this.spaceId, offset, PAGE_SIZE)
        .then(page => {
          // a document already listed moved between two pages: listed once
          const listed = new Set(this.documents.map(document => document.id));
          this.documents.push(...(page?.documents || []).filter(document => !listed.has(document.id)));
          this.size = page?.size || 0;
          this.failed = false;
        })
        .catch(() => {
          this.failed = true;
          this.$root.$emit('alert-message', this.$t('documents.spaceSettings.publicLinks.error'), 'error');
        })
        .finally(() => this.loading = false);
    },
    documentUrl(document) {
      const parameter = document.fileType === 'folder' ? 'folderId' : 'documentPreviewId';
      return `${eXo.env.portal.context}/s/${this.spaceId}/documents?${parameter}=${document.id}`;
    },
    // the icon the Documents app gives a file of this type, or a folder
    iconOf(document) {
      const icons = this.$documentsIconsExtension?.[0];
      return (document?.fileType === 'folder' ? icons?.get('folder') : icons?.get(document?.fileType))
        || icons?.get('file')
        || {class: 'fas fa-file', color: '#476A9C'};
    },
  },
};
</script>
