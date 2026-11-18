<template>
  <div
    id="attachmentIntegration"
    :class="entityType && entityId && 'v-card__text pl-0'"
    class="attachment-application border-box-sizing transparent">
    <div class="d-flex attachmentsIntegrationSlot">
      <div
        v-if="$scopedSlots.attachmentsButton"
        class="openAttachmentsButton me-2"
        @click="openAttachmentsAppDrawer()">
        <slot name="attachmentsButton"></slot>
      </div>
      <div v-else-if="entityId && entityType && (!readonly || shownAttachments.length)" :class="!shownAttachments.length && 'v-main align-center'">
        <v-icon size="18" color="primary">
          fa-paperclip
        </v-icon>
        <div
          v-if="!shownAttachments.length && !readonly"
          class="addAttachments d-flex align-center ms-3"
          @click="openAttachmentsAppDrawer()">
          <a class="addAttachmentLabel primary--text font-weight-bold text-decoration-underline">{{
            $t('attachments.add')
          }}</a>
          <v-btn
            icon
            color="primary">
            <v-icon size="16">
              fa-plus
            </v-icon>
          </v-btn>
        </div>
      </div>
      <div
        v-if="$scopedSlots.attachmentsList"
        class="attachedFilesList"
        @click="openAttachmentsDrawerList()">
        <slot :attachments="attachments" name="attachedFilesList"></slot>
      </div>
      <div v-else-if="entityId && entityType" class="attachmentsPreview v-card__text ms-3 pa-0">
        <div v-if="shownAttachments.length" class="attachmentsList">
          <a
            class="viewAllAttachments primary--text font-weight-bold text-decoration-underline"
            @click="openAttachmentsDrawerList()">
            {{ $t('attachments.view.all') }} ({{ shownAttachments.length }})
          </a>
          <v-list v-if="!$scopedSlots.attachmentsList" dense>
            <v-list-item-group>
              <attachment-item
                v-for="attachment in shownAttachments.slice(0, 2)"
                :key="attachment.id"
                :attachment="attachment"
                :attachments="shownAttachments"
                :can-access="attachment.acl && attachment.acl.canAccess"
                :allow-to-detach="false"
                :allow-to-edit="false"
                :is-file-editable="!readonly && isFileEditable(attachment)"
                :entity-id="readonly ? entityId : ''"
                :is-file-readable="isFileReadable(attachment)"
                open-in-editor
                allow-to-preview
                small-attachment-icon />
            </v-list-item-group>
          </v-list>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  props: {
    entityId: {
      type: String,
      default: ''
    },
    entityType: {
      type: String,
      default: ''
    },
    defaultDrive: {
      type: Object,
      default: () => null
    },
    defaultFolder: {
      type: String,
      default: ''
    },
    spaceId: {
      type: String,
      default: ''
    },
    /** Shows the attachments only: no add, no detach, no edit nor fill-in, here and in the list drawer */
    readonly: {
      type: Boolean,
      default: false
    },
    /** The ids of attachments another view of the entity shows, left out of this one and of the list drawer */
    excludedIds: {
      type: Array,
      default: () => []
    },
  },
  data() {
    return {
      attachments: [],
      iconExtensions: []
    };
  },
  computed: {
    /**
     * Lists the entity's attachments but the excluded ones.
     *
     * @returns {Array} the attachments shown
     */
    shownAttachments() {
      const excludedIds = (this.excludedIds || []).map(String);
      return excludedIds.length ? this.attachments.filter(attachment => !excludedIds.includes(String(attachment.id))) : this.attachments;
    },
    attachmentAppConfiguration() {
      return {
        'entityId': this.entityId,
        'entityType': this.entityType,
        'defaultDrive': this.defaultDrive,
        'defaultFolder': this.defaultFolder,
        'spaceId': this.spaceId,
        'attachments': this.shownAttachments,
        'readonly': this.readonly,
      };
    }
  },
  watch: {
    entityType() {
      this.initEntityAttachmentsList();
    },
    entityId() {
      this.initEntityAttachmentsList();
    },
  },
  created() {
    Promise.resolve(this.initEntityAttachmentsList())
      .finally(() => this.$root.$applicationLoaded());
    document.addEventListener('entity-attachments-updated', this.initEntityAttachmentsList);
  },
  beforeDestroy() {
    // an embedding app may mount the list again at each opening: a removed list stops listening
    document.removeEventListener('entity-attachments-updated', this.initEntityAttachmentsList);
  },
  methods: {
    openAttachmentsAppDrawer() {
      document.dispatchEvent(new CustomEvent('open-attachments-app-drawer', {detail: this.attachmentAppConfiguration}));
    },
    openAttachmentsDrawerList() {
      document.dispatchEvent(new CustomEvent('open-attachments-list-drawer', {detail: this.attachmentAppConfiguration}));
    },
    initEntityAttachmentsList() {
      if (this.entityType && this.entityId) {
        this.$attachmentService.getEntityAttachments(this.entityType, this.entityId).then(attachments => {
          attachments.forEach(attachments => {
            attachments.name = attachments.title;
          });
          this.attachments = attachments;
        });
      }
    },
    isFileEditable(attachment) {
      const type = attachment && attachment.mimetype || '';
      return this.$supportedDocuments && this.$supportedDocuments.filter(doc => doc.edit && doc.mimeType === type
                                                                                    && !attachment.cloudDriveFile).length > 0;
    },
    isFileReadable(attachment) {
      const type = attachment && attachment.mimetype || '';
      return this.$supportedDocuments && this.$supportedDocuments.filter(doc => doc.mimeType === type && !attachment.cloudDriveFile).length > 0;
    },

  }
};
</script>