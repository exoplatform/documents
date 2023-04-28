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
  <select
    id="filterDocumentsSelect"
    v-model="primaryFilter"
    v-if="!isMobile"
    name="documentsFilter"
    class="selectPrimaryFilter input-block-level ignore-vuetify-classes  pa-0 my-auto mx-1"
    @change="changeDocumentsFilter">
    <option
      v-for="item in filterDocuments"
      :key="item.name"
      :value="item.name">
      {{ $t('documents.filter.'+item.name.toLowerCase()) }}
    </option>
  </select>
</template>
<script>
export default {
  props: {
    isMobile: {
      type: Boolean,
      default: false
    },
    primaryFilter: {
      type: String,
      default: 'all',
    },
  },
  data: () => ({
    filterDocuments: [{name: 'all'},{name: 'favorites'}],
    query: '',
    extended: false,
  }),
  created() {
    this.$root.$on('set-documents-filter', data => {
      this.primaryFilter= data;
    });
    this.$root.$on('set-documents-search', data => {
      this.query= data.query;
      this.extended= data.extended;
    });
  },
  methods: {
    changeDocumentsFilter(){
      this.$root.$emit('documents-filter', this.primaryFilter.toLowerCase());
      this.$root.$emit('set-mobile-filter', this.primaryFilter);
    },
  },
};
</script>