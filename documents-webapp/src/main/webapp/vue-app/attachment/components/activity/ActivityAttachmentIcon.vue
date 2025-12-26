<!--
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
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
-->

<template>
  <span
    v-if="displayAttachmentIcon"
    :title="attachmentTitle">
    <v-icon
      size="20"
      class="icon-default-color">
      fa-solid fa-paperclip
    </v-icon>
    <span class="ms-1 text-subtitle text-color">{{ attachmentsCount }}</span>
  </span>
</template>
<script>

export default {
  props: {
    activity: {
      type: Object,
      default: null,
    },
  },
  data() {
    return {
      attachments: [],
      entityId: '',
      entityType: '',
    };
  },
  computed: {
    displayAttachmentIcon() {
      return  this.attachments?.length > 0;
    },
    attachmentsCount() {
      return this.attachments?.length > 9 ? '9+' : this.attachments?.length;
    },
    attachmentTitle() {
      if (!this.attachments?.length) {
        return null;
      }
      return  this.attachments.length === 1 ? `1 ${this.$t('attachments.item')}` : `${this.attachments.length} ${this.$t('attachments.list')}`;
    },
  },
  async created() {
    this.entityId = this.activity?.news?.latestVersionId;
    this.entityType = 'WIKI_PAGE_VERSIONS';
    this.attachments = await this.initEntityAttachmentsList();
  },
  methods: {
    async initEntityAttachmentsList() {
      if (this.entityType && this.entityId) {
        const attachments = await this.$attachmentService.getEntityAttachments(this.entityType, this.entityId);
        if (attachments?.length) {
          attachments.forEach(a => a.name = a.title);
          return attachments;
        }
      }
      return [];
    },
  },
};
</script>