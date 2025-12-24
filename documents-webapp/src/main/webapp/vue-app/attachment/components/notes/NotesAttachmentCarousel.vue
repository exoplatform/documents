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
  <span v-if="displayAttachmentItem">
    <card-carousel parent-class="note-files-parent">
      <notes-attachment-item
        v-for="(attachment, index) in attachments"
        :key="attachment.id"
        :index="index"
        :count="attachmentsCount"
        :attachment="attachment"
        :attachments="attachments"
        :preview-width="previewWidth"
        :preview-height="previewHeight"
        class="note-file-item" />
    </card-carousel>
  </span>
</template>
<script>
export default {
  props: {
    entityId: {
      type: String,
      default: null,
    },
    entityType: {
      type: String,
      default: null,
    },
  },
  data() {
    return {
      files: [],
      previewHeight: '152px',
      previewWidth: '250px',
    };
  },
  computed: {
    displayAttachmentItem() {
      return this.files.length > 0;
    },
    attachments() {
      if (!this.files) {
        return [];
      }
      const attachments = [];
      this.files.forEach(attachment => {
        const mimetype = attachment.mimetype;
        let name = attachment.title;
        const updatedDate = attachment.updated || attachment.created;
        try {
          name = decodeURIComponent(name.replace(/%25/g, '%').replace(/%([^2][^5])/g, '%25$1'));
        } catch (e) {
          // could happen, but ignore it
        }
        const imageURL = this.getImageUrl(attachment);
        attachments.push({
          id: attachment.id,
          image: imageURL,
          downloadUrl: this.getDownloadUrl(attachment),
          name,
          filename: name,
          mimetype,
          icon: this.getFileIcon(attachment),
          editable: this.isFileEditable(attachment),
          readable: this.isFileReadable(attachment),
          path: attachment.path,
          updatedDate,
          source: 'documents'
        });
      });
      return attachments;
    },
    attachmentsCount() {
      return this.attachments.length;
    },
  },
  async created() {
    this.files = await this.initEntityAttachmentsList();
  },
  methods: {
    async initEntityAttachmentsList() {
      if (this.entityType && this.entityId) {
        const files = await this.$attachmentService.getEntityAttachments(this.entityType, this.entityId);
        if (files?.length) {
          files.forEach(a => a.name = a.title);
          return files;
        }
      }
      return [];
    },
    getFileIcon(file) {
      return this.$documentsIconsExtension?.[0]?.get?.(file?.mimetype);
    },
    isFileEditable(file) {
      return  this.$supportedDocuments && this.$supportedDocuments.filter(doc => doc.edit && doc.mimeType === file.mimetype ).length > 0;
    },
    isFileReadable(file) {
      return this.$supportedDocuments && this.$supportedDocuments.filter(doc => doc.mimeType === file.mimetype).length > 0;
    },
    getImageUrl(file) {
      file.readable = this.isFileReadable(file);
      return this.$documentsUtils.getThumbnailUrl(file,'250x250',file.updatedDate);
    },
    getDownloadUrl(file) {
      return this.$documentsUtils.getDownloadUrl(file.id,file.updatedDate);
    },
  },
};
</script>