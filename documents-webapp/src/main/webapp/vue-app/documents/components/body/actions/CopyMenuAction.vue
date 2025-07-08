/*
 * Copyright (C) 2025 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program; if not, write to the Free Software Foundation,
 */

<template>
  <div>
    <div
      class="clickable py-10px px-4"
      @click="copy()">
      <v-icon
        size="16"
        class="pe-1">
        fas fa-copy
      </v-icon>
      <span class="ps-1 text-body menu-text-color">{{ $t('documents.label.copy') }}</span>
    </div>
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
    copy() {
      const copyContent = {
        id: this.file.id,
        path: this.file.path,
      };
      localStorage.setItem('documentCopiedItem', JSON.stringify(copyContent));
      this.$emit.$on('copied-element');
    }
  },
};
</script>