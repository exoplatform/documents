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
  <div class="d-flex">
    <component
      :is="isMobile ? 'folder-treeview-drawer' : 'folder-tree-view'"
      :tree-view-expended="treeViewExpended"
      :is-mobile="isMobile"
      :folder-path="folderPath" />
    <v-card
      flat
      class="width-full">
      <documents-breadcrumb
        :is-mobile="isMobile"
        :tree-view-expended="treeViewExpended" />
      <upload-overlay />
      <div :class="treeViewExpended && 'ms-2'" class="d-flex mb-5 align-center">
        <v-menu v-model="sortMenu" offset-y>
          <template #activator="{ on, attrs }">
            <v-btn
              id="documentSort"
              small
              elevation="0"
              class="px-0"
              v-bind="attrs"
              v-on="on">
              <div class="text-header">{{ selectedSort.label }}</div>
            </v-btn>
          </template>
          <v-list class="pa-0">
            <v-list-item
              v-for="item in sortFields"
              :key="item.value"
              @click="setSortField(item)">
              <div>{{ item.label }}</div>
            </v-list-item>
          </v-list>
        </v-menu>
        <v-btn icon @click="updatedSortDirection">
          <v-icon class="ms-1" size="16">{{ sortDirectionIcon }}</v-icon>
        </v-btn>
      </div>
      <v-card
        :class="treeViewExpended && 'ms-2'"
        class="d-flex flex-wrap border-box-sizing"
        flat>
        <div
          v-for="folder in folderToDisplay"
          :key="folder.id"
          class=" flex-shrink-0 mb-3 me-3 pa-0">
          <documents-folder-card
            :folder="folder"
            :files="folderToDisplay"
            :select-all-checked="selectAll"
            :selected-documents="selectedDocuments"
            @document-selected="handleDocumentSelection"
            @document-unselected="handleDocumentSelection" />
        </div>
      </v-card>
      <v-card
        :class="treeViewExpended && 'ms-2'"
        class="d-flex flex-wrap border-box-sizing"
        flat>
        <div
          v-for="(file, index) in filesToDisplay"
          :key="file.id"
          class=" flex-shrink-0 mb-3 me-3 pa-0">
          <documents-item-card
            :index="index"
            :count="filesCount"
            :key="file.id"
            :file="file"
            :files="files"
            :select-all-checked="selectAll"
            :selected-documents="selectedDocuments"
            @document-selected="handleDocumentSelection"
            @document-unselected="handleDocumentSelection"
            height="175px"
            max-height="175px"
            width="215px"
            show-details />
        </div>
      </v-card>
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
    </v-card>
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
    isMobile: {
      type: Boolean,
      default: false
    },
    folderPath: {
      type: String,
      default: null
    },
    selectedDocuments: {
      type: Array,
      default: () => []
    },
  },
  data: () => ({
    treeViewExpended: true,
    sortMenu: false,
    sortField: 'lastUpdated',
    ascending: false,
    selectAll: false
  }),
  computed: {
    filesToDisplay() {
      const files = this.files ? this.files.filter(item => !item.folder && !item.drive) : [];
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
          folder: file.folder,
        };
      });
    },
    filesCount() {
      return this.files && this.files.length || 0;
    },
    folderToDisplay() {
      return this.files ? this.files.filter(item => item.folder || item.drive) : [];
    },
    sortFields() {
      return [
        {value: 'lastUpdated', label: this.$t('documents.label.lastUpdated')},
        {value: 'name', label: this.$t('documents.label.name')},
        {value: 'size', label: this.$t('documents.label.size')}
      ];
    },
    selectedSort() {
      const item = this.sortFields.find(i => i.value === this.sortField);
      return item ? item : null;
    },
    sortDirectionIcon() {
      return this.ascending ? 'fa-arrow-down' : 'fa-arrow-up';
    }
  },
  created() {
    this.treeViewExpended =  localStorage.getItem('expendedTreeView')!=null ? localStorage.getItem('expendedTreeView') === 'true' : (this.$root.settings?.expendedTreeView !== null ? this.$root.settings.expendedTreeView : true);
    this.$root.$on('tree-view-expend', this.extendTreeView);
    this.$root.$on('reset-selections', () => this.selectAll = false);
  },
  methods: {
    extendTreeView(value) {
      this.treeViewExpended = value;
      localStorage.setItem('expendedTreeView', value);
    },
    setSortField(item) {
      this.sortField = item.value;
      this.ascending = false;
      this.$root.$emit('documents-sort', this.sortField, this.ascending);
      this.sortMenu = false;
    },
    updatedSortDirection() {
      this.ascending = !this.ascending;
      this.$root.$emit('documents-sort', this.sortField, this.ascending);
    },
    handleDocumentSelection() {
      this.selectAll = this.files.length === this.selectedDocuments.length;
    },
    selectAllDocuments() {
      this.$root.$emit('select-all-documents', this.selectAll);
    },
  }
};
</script>