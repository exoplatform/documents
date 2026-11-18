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
  <div 
    v-if="displayAttachmentItem"
    id="notesAttachment">
    <card-carousel 
      parent-class="note-files-parent">
      <notes-attachment-item
        v-for="(attachment, index) in attachments"
        :key="attachment.id"
        :index="index"
        :count="attachmentsCount"
        :attachment="attachment"
        :attachments="attachments"
        :preview-width="previewWidth"
        :preview-height="previewHeight"
        :owner-id="spaceIdentityId"
        :allow-to-detach="allowToDetach"
        class="note-file-item"
        @detach="$emit('detach', fileById(attachment.id))" />
    </card-carousel>
  </div>
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
    spaceId: {
      type: [String, Number],
      default: null,
    },
    /** Opens every file in the editor's view mode, even for a viewer who may edit it */
    readonly: {
      type: Boolean,
      default: false,
    },
    /** The ids of attachments another view of the entity shows, left out of the cards */
    excludedIds: {
      type: Array,
      default: () => [],
    },
    /**
     * The files to show, kept up to date by the embedding app: when given, the entity's files are not
     * loaded
     */
    files: {
      type: Array,
      default: null,
    },
    /** Offers a detach button on each card, which emits detach with the file for the embedding app to detach */
    allowToDetach: {
      type: Boolean,
      default: false,
    },
  },
  data() {
    return {
      spaceIdentityId: null,
      entityFiles: [],
      previewHeight: '152px',
      previewWidth: '250px',
    };
  },
  computed: {
    /**
     * Lists the given files, or else the entity's, but the excluded ones (compared as strings) and those
     * not attached yet (an upload in progress has no id).
     *
     * @returns {Array} the files shown
     */
    shownFiles() {
      const files = (this.files || this.entityFiles || []).filter(file => file && file.id);
      const excludedIds = (this.excludedIds || []).map(String);
      return excludedIds.length ? files.filter(file => !excludedIds.includes(String(file.id))) : files;
    },
    /**
     * Tells whether a card shows: the entity has a file that is not excluded.
     *
     * @returns {Boolean} true when a card shows
     */
    displayAttachmentItem() {
      return this.shownFiles.length > 0;
    },
    /**
     * Maps the files shown to the cards' entries: their name, preview, links and how they open.
     *
     * @returns {Array} the cards' entries
     */
    attachments() {
      const attachments = [];
      this.shownFiles.forEach(attachment => {
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
          editable: !this.readonly && this.isFileEditable(attachment),
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
  watch: {
    entityId: {
      immediate: true,
      async handler() {
        this.entityFiles = this.files ? [] : await this.initEntityAttachmentsList();
      },
    },
    spaceId: {
      immediate: true,
      handler() {
        this.spaceIdentityId = null;
        const spaceId = this.spaceId;
        if (spaceId) {
          this.$spaceService.getSpaceById(spaceId)
            .then(space => {
              if (spaceId === this.spaceId) {
                this.spaceIdentityId = space?.identityId || null;
              }
            })
            .catch(() => this.spaceIdentityId = null);
        }
      },
    },
    displayAttachmentItem: {
      immediate: true,
      handler() {
        if (this.displayAttachmentItem) {
          document.dispatchEvent(new CustomEvent('file-attachments-enable'));
        } else {
          document.dispatchEvent(new CustomEvent('file-attachments-disable'));
        }
      }
    }
  },
  methods: {
    /**
     * Finds a shown file by its id, as the embedding app gave it or as the entity listed it.
     *
     * @param {String} id the file's id
     * @returns {Object} the file
     */
    fileById(id) {
      return this.shownFiles.find(file => String(file.id) === String(id));
    },
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