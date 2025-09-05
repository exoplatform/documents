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
  <div>
    <documents-folder-cards-view
      v-if="cardsDisplay"
      :files="files"
      :has-more="hasMore"
      :is-mobile="isMobile"
      :selected-documents="selectedDocuments"
      :loading="loading" />
    <documents-folder-list-view
      v-else
      :files="files"
      :page-size="pageSize"
      :offset="offset"
      :limit="limit"
      :sort-field="sortField"
      :query="query"
      :extended-search="extendedSearch"
      :initialized="initialized"
      :loading="loading"
      :has-more="hasMore"
      :ascending="ascending"
      :primary-filter="primaryFilter"
      :file-type="fileType"
      :after-date="afterDate"
      :before-date="beforeDate"
      :min-size="minSize"
      :max-size="maxSize"
      :is-mobile="isMobile"
      :selected-view="selectedView"
      :selected-documents="selectedDocuments" />
  </div>
</template>

<script>
export default {
  props: {
    files: {
      type: Array,
      default: null,
    },
    pageSize: {
      type: Number,
      default: 20
    },
    offset: {
      type: Number,
      default: 20
    },
    limit: {
      type: Number,
      default: 20
    },
    sortField: {
      type: String,
      default: null
    },
    query: {
      type: String,
      default: null
    },
    extendedSearch: {
      type: Boolean,
      default: false,
    },
    initialized: {
      type: Boolean,
      default: false
    },
    loading: {
      type: Boolean,
      default: false
    },
    hasMore: {
      type: Boolean,
      default: false,
    },
    ascending: {
      type: Boolean,
      default: false,
    },
    primaryFilter: {
      type: String,
      default: null,
    },
    fileType: {
      type: Array,
      default: () => []
    },
    afterDate: {
      type: Number,
      default: null,
    },
    beforeDate: {
      type: Number,
      default: null,
    },
    minSize: {
      type: Number,
      default: null,
    },
    maxSize: {
      type: Number,
      default: null,
    },
    isMobile: {
      type: Boolean,
      default: false
    },
    selectedView: {
      type: String,
      default: null
    },
    folderPath: {
      type: String,
      default: null
    },
    selectedDocuments: {
      type: Array,
      default: () => []
    },
    viewType: {
      type: String,
      default: null
    },
  },
  computed: {
    cardsDisplay() {
      return !this.$root.isMobile && this.viewType === 'cardsView';
    },
  }
};
</script>
