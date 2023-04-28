<!--
  This file is part of the Meeds project (https://meeds.io/).
  Copyright (C) 2022 Meeds Association
  contact@meeds.io
  This program is free software; you can redistribute it and/or
  modify it under the terms of the GNU Lesser General Public
  License as published by the Free Software Foundation; either
  version 3 of the License, or (at your option) any later version.
  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
  Lesser General Public License for more details.
  You should have received a copy of the GNU Lesser General Public License
  along with this program; if not, write to the Free Software Foundation,
  Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
-->
<template>
  <div>
    <v-icon
      size="24"
      class="text-sub-title pa-1 my-auto mt-1"
      v-show="!showFilter"
      @click="mobileFilter()">
      {{ filterIcon }}
    </v-icon>
  </div>
</template>
<script>
export default {
  props: {
    query: {
      type: String,
      default: null,
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
  computed: {
    filterIcon() {
      return (this.query?.length > 0)  || this.primaryFilter !== 'all' || this.fileType?.length>0 || this.afterDate || this.beforDate || this.minSize || this.maxSize ? 'mdi-filter' : 'mdi-filter-outline';
    }
  },

  created() {
    this.$root.$on('resetSearch', this.cancelSearch);
    this.$root.$on('mobile-filter', this.mobileFilter);
  },
  methods: {
    mobileFilter(){
      this.showFilter = !this.showFilter;
      this.$root.$emit('show-mobile-filter', this.showFilter);
    },
    cancelSearch(){
      this.query = null;
      this.$refs.inputQuery.blur();
    },
  },
};
</script>