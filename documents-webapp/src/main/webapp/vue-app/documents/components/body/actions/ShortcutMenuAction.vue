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
    class="clickable mx-2"
    @click="addShortcut()">
    <v-icon
      size="13"
      class="pe-1 iconStyle">
      fas fa-share-square
    </v-icon>
    <span class="ps-1">{{ $t('documents.label.shortcut') }}</span>
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
  computed: {
    isMobile() {
      return this.$vuetify.breakpoint.name === 'xs' || this.$vuetify.breakpoint.name === 'sm';
    },
  },
  methods: {
    addShortcut() {
      this.$root.$emit('open-document-position-drawer', this.file, 'shortcut');
      if ( this.isMobile ) {
        this.$root.$emit('close-file-action-menu');
      }
    }
  },
};
</script>