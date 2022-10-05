<!--
* Copyright (C) 2022 eXo Platform SAS
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
  <div
    class="clickable ma-auto pt-1 mx-2"
    @click="showVersionHistory">
    <v-icon
      size="13"
      class="pe-1 iconStyle">
      fa fa-history
    </v-icon>
    <span class="ps-1">{{ $t('documents.label.showVersionHistory') }}</span>
    <v-divider
        class="mt-1 dividerStyle" />
  </div>
</template>

<script>
export default {
  props: {
    file: {
      type: Object,
      default: null,
    }
  },
  methods: {
    showVersionHistory() {
      this.$root.$emit('show-version-history', this.file?.id);
    }
  }
};
</script>