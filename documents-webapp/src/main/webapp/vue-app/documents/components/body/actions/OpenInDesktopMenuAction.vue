<!--

  Copyright (C) 2025 eXo Platform SAS

  This program is free software: you can redistribute it and/or modify
  it under the terms of the GNU Affero General Public License as published by
  the Free Software Foundation, either version 3 of the License, or
  (at your option) any later version.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU Affero General Public License
  along with this program.  If not, see <gnu.org/licenses>.

-->
<template>
  <document-action-item
    :icon="icon"
    :label="$t('documents.label.openInDesktop')"
    @click="openDialog" />
</template>
<script>
export default {
  props: {
    file: {
      type: Object,
      default: null,
    },
  },
  computed: {
    iconExtension() {
      return this.$documentsIconsExtension?.[0]?.get?.(this.file?.mimeType);
    },
    protocol() {
      return this.iconExtension.protocol;
    },
    icon() {
      return this.iconExtension.class;
    },
  },
  methods: {
    openDialog() {    
      const pathParts = this.file.path.split('/');
      pathParts.pop();
      this.$root.$emit('open-in-desktop-dialog', this.protocol, `${pathParts.join('/')}/${this.file.name}`);
    },
  },
};
</script>