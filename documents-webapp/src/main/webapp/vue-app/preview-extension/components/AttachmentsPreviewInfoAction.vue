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
  <v-btn
    v-if="attachment && attachment.source === 'documents'"
    id="preview-attachment-info"
    :class="!isMobile && 'icon-large-size' || 'icon-medium-size'"
    :title="$t('attachment.details')"
    icon
    class="white--text ml-1"
    @click="showInfo()">
    <i class="fas fa-info-circle"></i>
  </v-btn>
</template>
<script>
export default {
  props: {
    attachment: {
      type: Object,
      default: null,
    },
    isMobile: {
      type: Boolean,
      default: false,
    },
  },
  methods: {
    showInfo() {
      document.dispatchEvent(new CustomEvent('open-document-info-drawer', {detail: this.attachment.id}));
    },
  },
};
</script>

