<!--
 *
 * Copyright (C) 2024 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <gnu.org/licenses>.
 *
-->

<template>
  <div
    class="pt-1 mx-2 clickable"
    @click="changeFilterOption()">
    <v-icon
      size="21"
      v-if="activated"
      class="pe-1 pb-1 iconStyle">
      mdi-check-bold
    </v-icon>
    <span :class="activated?'ps-1':'ps-8'">{{ $t('documents.filter.all') }}</span>
  </div>
</template>
<script>
export default {
  props: {
    quickFilter: {
      type: Boolean,
      default: false,
    },
    quickFilterValue: {
      type: String,
      default: 'all',
    },
  },
  computed: {
    activated() {
      return this.quickFilterValue === 'all';
    },
    filterOption() {
      return this.quickFilterValue;
    },
  },
  created() {
    this.$root.$on('set-mobile-filter', this.setFilterOption);
  },
  methods: {
    setFilterOption(option){
      this.filterOption = option;
    },
    changeFilterOption(){
      if (!this.activated){
        this.filterOption = 'All';
        this.$root.$emit('documents-filter', 'all');
        this.$root.$emit('set-documents-filter', 'All');
      }
      this.$root.$emit('close-mobile-filter-menu',false);
    },
  },

};
</script>