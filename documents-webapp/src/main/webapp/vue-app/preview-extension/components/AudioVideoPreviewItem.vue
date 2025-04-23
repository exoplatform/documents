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
  <video
    v-if="supported"
    :src="`${thumbnailUrl}#t=0.001`"
    controls="controls"
    class="black mx-auto full-height full-width position-absolute"
    @error="supported = false">
  </video>
  <attachments-default-preview
    v-else
    :attachment="attachment"
  />
</template>
<script>
export default {
  props: {
    attachment: {
      type: Object,
      default: null,
    },
    objectType: {
      type: String,
      default: null,
    },
    objectId: {
      type: String,
      default: null,
    },
  },
  data: () => ({
    supported: true,
  }),
  computed: {
    thumbnailUrl() {
      return this.attachment.downloadUrl?`${eXo.env.portal.context}${this.attachment.downloadUrl}`:`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/social/attachments/${this.objectType}/${this.objectId}/${this.attachment.id}`;
    },
    isMobile() {
      return this.$vuetify.breakpoint.name === 'sm' || this.$vuetify.breakpoint.name === 'xs' || this.$vuetify.breakpoint.name === 'md';
    }
  },
};
</script>

