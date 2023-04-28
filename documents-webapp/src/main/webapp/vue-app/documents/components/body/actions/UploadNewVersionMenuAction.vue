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
  <div
    class="clickable pt-1 mx-2"
    @click="uploadVersion">
    <v-icon
      size="13"
      class="pe-1 iconStyle">
      fas fa-upload
    </v-icon>
    {{ $t('documents.label.upload.newVersion') }}
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
    },
    isMobile: {
      type: Boolean,
      default: false
    }
  },
  methods: {
    uploadVersion() {
      this.$root.$emit('upload-new-version-action-invoke', this.file);
      if (this.isMobile) {
        this.$root.$emit('close-file-action-menu');
      }
    },
  }
};
</script>