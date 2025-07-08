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
  <v-card
    color="transparent"
    class="d-flex py-10px px-4"
    flat
    @click="openDialog">
    <v-card
      min-width="18"
      color="transparent"
      class="d-flex align-center justify-center me-2"
      flat>
      <v-icon size="16">{{ icon }}</v-icon>
    </v-card>
    <span class="ps-1 text-body menu-text-color">{{ $t('documents.label.openInDesktop') }}</span>
  </v-card>
</template>
<script>
export default {
  props: {
    file: {
      type: Object,
      default: null,
    },
  },
  data: () => ({
    open: false,
  }),
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
    href() {
      return `${this.protocol}${window.origin}/digest/jcr/repository/collaboration${this.file.path}`;
    },
  },
  methods: {
    async openDialog() {
      this.open = true;
      await this.$nextTick();
      window.setTimeout(() => this.$refs?.dialog?.open?.(), 200);
    },
  },
};
</script>