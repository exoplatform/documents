<!--
* Copyright (C) 2023 eXo Platform SAS
*
*  This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU Affero General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU Affero General Public License
* along with this program.  If not, see <gnu.org/licenses>.
-->


<template>
  <div v-if="showFilter">
    <extension-registry-components
      name="DocumentsFilters"
      type="documents-filters"
      :params="params"
      class="d-flex flex-no-wrap align-center documents-header-filter-container fill-available"
      parent-element="div"
      element="div"
      element-class="mx-auto ma-lg-0 documents-header-filter-element" />
  </div>
</template>
<script>

export default {
  props: {
    query: {
      type: String,
      default: '',
    },
    primaryFilter: {
      type: String,
      default: 'all',
    },
    fileType: {
      type: Array,
      default: () => []
    },
    afterDate: {
      type: Number,
      default: null,
    },
    beforDate: {
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
  },
  data: () => ({
    showFilter: false,
  }),
  created() {
    this.$root.$on('show-mobile-filter', data => {
      this.showFilter= data;
    });
  },
  computed: {
    params() {
      return {
        query: this.query,
        primaryFilter: this.primaryFilter,
        fileType: this.fileType,
        afterDate: this.afterDate,
        beforDate: this.beforDate,
        minSize: this.minSize,
        maxSize: this.maxSize,
        isMobile: this.isMobile,
      };
    }
  },
 
};
</script>
