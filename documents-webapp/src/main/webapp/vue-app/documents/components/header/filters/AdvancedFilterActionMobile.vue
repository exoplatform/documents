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
  <div
    class="clickable pt-1 mx-2"
    @click="advancedFilter()">
    <v-icon
      size="21"
      class="pe-1 pb-1 iconStyle">
      mdi-filter-plus
    </v-icon>
    <span class="ps-1">{{ $t('documents.label.mobileAdvancedFilter') }}</span>
  </div>
</template>
<script>
export default {
  methods: {
    advancedFilter(){
      this.$root.$emit('open-advanced-filter-drawer');
      this.$root.$emit('close-mobile-filter-menu',false);
    },
  },
};
</script>