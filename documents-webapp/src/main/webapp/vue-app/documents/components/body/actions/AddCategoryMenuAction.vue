<!--
* Copyright (C) 2025 eXo Platform SAS
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
    class="clickable py-10px px-4"
    @click="addCategory">
    <v-icon
      size="16"
      class="pe-1">
      fa-th-large
    </v-icon>
    <span class="ps-1 text-body menu-text-color">{{ $t('documents.label.addCategories') }}</span>
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
    spaceId() {
      return eXo.env.portal.spaceId || eXo.env.portal.userIdentityId;
    },
  },
  methods: {
    addCategory() {
      document.dispatchEvent(new CustomEvent('category-form-drawer-open', {detail: {
        objectType: 'document',
        objectId: this.file.id,
        spaceId: this.spaceId,
        categoryIds: this.file?.categoryIds,
      }}));
    },
  },
};
</script>