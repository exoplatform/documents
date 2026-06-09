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
  <document-action-item
    icon="fa-th-large"
    :label="$t('documents.label.addCategories')"
    @click="addCategory" />
</template>
<script>
export default {
  props: {
    file: {
      type: Object,
      default: null,
    },
    isMultiSelection: {
      type: Boolean,
      default: false
    },
  },
  computed: {
    spaceId() {
      return eXo.env.portal.spaceId || eXo.env.portal.userIdentityId;
    },
  },
  methods: {
    addCategory() {
      if (!this.isMultiSelection) {
        document.dispatchEvent(new CustomEvent('category-form-drawer-open', {detail: {
          objectType: 'document',
          objectId: this.file.id,
          spaceId: this.spaceId,
          categoryIds: this.file?.categoryIds,
        }}));
      } else {
        this.$root.$emit('documents-bulk-edit-categories');
      }
      this.$root.$emit('close-file-action-menu');
    },
  },
};
</script>