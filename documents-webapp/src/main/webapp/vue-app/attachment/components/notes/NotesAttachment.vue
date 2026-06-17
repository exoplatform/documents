<template>
  <span v-if="displayAttachmentIcon">
    <v-btn
      :aria-label="$t('notes.open.attachments.list')"
      icon
      @click="openAttachmentsList">
      <v-icon
        size="20"
        class="noteAttachmentsIcon">
        fa-solid fa-paperclip
      </v-icon>
    </v-btn>
    <attachments-list-drawer
      ref="attachmentsListDrawer"
      :attachments="attachments"
      :allow-to-detach="false"
      :display-open-attachment-drawer-button="false"
      :open-attachments-in-editor="true" />
  </span>
</template>
<script>

export default {
  props: {
    entityId: {
      type: String,
      default: null,
    },
    spaceId: {
      type: String,
      default: null,
    },
    entityType: {
      type: String,
      default: null,
    },
    lang: {
      type: String,
      default: ''
    },
    isEmptyNoteTranslation: {
      type: Boolean,
      default: false
    },
    editMode: {
      type: Boolean,
      default: true
    }
  },
  data() {
    return {
      attachments: [],
      displayUploadedFiles: true,
      createEntityTypeFolder: false,
      displayCreateDocumentInput: false,
      originalAttachmentsList: [],
      attachmentListUpdated: false,
      isDrawerClosedEventHandled: false,
      initDone: false,
      hasLocalStoredAttachments: false,
    };
  },
  watch: {
    entityId() {
      if (!this.editMode) {
        this.originalAttachmentsList = [];
        this.attachments = [];
        this.initEntityAttachmentsList();
      }
    }
  },
  computed: {
    displayAttachmentIcon() {
      return !this.editMode && this.attachments.length > 0;
    },
    attachmentAppConfiguration() {
      return {
        'entityId': this.entityId,
        'entityType': this.entityType,
        'defaultDrive': null,
        'defaultFolder': 'Activity Stream Documents',
        'spaceId': this.spaceId,
        'attachments': this.attachments,
        'displayUploadedFiles': this.displayUploadedFiles,
        'createEntityTypeFolder': this.createEntityTypeFolder,
        'sourceApp': 'note',
        'attachToEntity': this.attachToEntity,
        'displayCreateDocumentInput': this.displayCreateDocumentInput
      };
    },
    attachToEntity() {
      return !!this.entityId && this.entityType !== 'WIKI_PAGE_VERSIONS';
    },
    processAutoSave() {
      return this.attachmentListUpdated && !this.attachToEntity;
    },
  },
  mounted() {
    this.retrieveAttachmentsFromLocalStorage();
  },
  created() {
    if (!this.editMode) {
      this.initEntityAttachmentsList();
    }
    document.addEventListener('open-notes-attachments', this.openAttachmentDrawer);
    document.addEventListener('attachments-app-drawer-closed', this.handleDrawerClosedEvent);
    document.addEventListener('note-draft-auto-save-done', this.handleDraftAutoSave);
    document.addEventListener('article-draft-auto-save-done', this.handleDraftAutoSave);
    document.addEventListener('preview-attachment', this.previewAttachment);
  },
  beforeDestroy() {
    document.removeEventListener('open-notes-attachments', this.openAttachmentDrawer);
    document.removeEventListener('attachments-app-drawer-closed', this.handleDrawerClosedEvent);
    document.removeEventListener('article-draft-auto-save-done', this.handleDraftAutoSave);
    document.removeEventListener('note-draft-auto-save-done', this.handleDraftAutoSave);
    document.removeEventListener('preview-attachment', this.previewAttachment);
  },
  methods: {
    openAttachmentDrawer() {
      this.originalAttachmentsList = [];
      this.attachments = this.hasLocalStoredAttachments && this.attachments || [];
      if (this.entityId > 0 && this.entityType && !this.isEmptyNoteTranslation) {
        this.initDone = false;
        this.waitInit();
        this.initEntityAttachmentsList().then(() => {
          this.initDone = true;
          document.dispatchEvent(new CustomEvent('end-loading-attachment-drawer'));
        });
      }
      this.hasLocalStoredAttachments = false;
      document.dispatchEvent(new CustomEvent('open-attachments-app-drawer', {detail: this.attachmentAppConfiguration}));
    },
    retrieveAttachmentsFromLocalStorage() {
      const files = JSON.parse(localStorage.getItem('activity-composer-files'));
      localStorage.removeItem('activity-composer-files');
      if (!files?.length) {
        return;
      }
      this.hasLocalStoredAttachments = true;
      this.attachments.push(...files);
      this.emitEditorExtensionsDataUpdatedEvent();
    },
    openAttachmentsList() {
      this.$root.$emit('open-attachments-list-drawer');
    },
    handleDrawerClosedEvent() {
      if (!this.isDrawerClosedEventHandled) {
        this.emitEditorExtensionsDataUpdatedEvent(event);
        this.isDrawerClosedEventHandled = true;
        setTimeout(() => {
          this.isDrawerClosedEventHandled = false;
        }, 1000);
      }
    },
    emitEditorExtensionsDataUpdatedEvent() {
      const attachmentAdded = this.attachments.filter((item) => !this.originalAttachmentsList.some(originalItem => originalItem.id === item.id)).length > 0;
      const attachmentRemoved = this.originalAttachmentsList.filter((originalItem) => !this.attachments.some(item => item.id === originalItem.id)).length > 0;
      this.attachmentListUpdated = attachmentRemoved || attachmentAdded;
      if (this.attachmentListUpdated) {
        document.dispatchEvent(new CustomEvent('note-editor-extensions-data-updated', {
          detail: {
            showAutoSaveMessage: true,
            processAutoSave: this.processAutoSave
          }
        }));
      }
    },
    initEntityAttachmentsList() {
      if (this.entityType && this.entityId) {
        return this.$attachmentService.getEntityAttachments(this.entityType, this.entityId).then(attachments => {
          if (attachments && attachments.length) {
            attachments.forEach((attachment) => {
              attachment.name = attachments.title;
            });
            this.attachments.push(...attachments);
            this.originalAttachmentsList.push(...attachments);
          }
        });
      } else {return Promise.resolve();}
    },
    waitInit() {
      setTimeout(() => {
        if (!this.initDone) {
          document.dispatchEvent(new CustomEvent('start-loading-attachment-drawer'));
        } else {
          this.waitInit();
        }
      }, 200);
    },
    updateLinkedAttachmentsToEntity(entityId) {
      const attachmentIds = this.attachments.filter(attachment => attachment.id).map(attachment => attachment.id);
      if (attachmentIds.length === 0) {
        return this.$attachmentService.removeAllAttachmentsFromEntity(entityId, 'WIKI_DRAFT_PAGES').then(() => {
          document.dispatchEvent(new CustomEvent('entity-attachments-updated'));
          this.attachmentListUpdated = false;
        }).catch(e => {
          console.error(e);
          this.$refs.attachmentsAppDrawer.endLoading();
          this.$root.$emit('alert-message', this.$t('attachments.link.failed'), 'error');
          this.attachmentListUpdated = false;
        });
      } else {
        return this.$attachmentService.updateLinkedAttachmentsToEntity(entityId, 'WIKI_DRAFT_PAGES', attachmentIds)
          .then(() => {
            document.dispatchEvent(new CustomEvent('entity-attachments-updated'));
            this.attachmentListUpdated = false;
          })
          .catch(e => {
            this.attachmentListUpdated = false;
            console.error(e);
            this.$root.$emit('alert-message', this.$t('attachments.link.failed'), 'error');
          });
      }
    },
    handleDraftAutoSave(event) {
      if (this.attachmentListUpdated && event.detail.draftId) {
        this.updateLinkedAttachmentsToEntity(event.detail.draftId);
      }
    },
    previewAttachment(event) {
      const file = event?.detail;
      const files = [];
      this.attachments.forEach((item) => {
        files.push({'id': item.id,'filename': item.title,'mimetype': item.mimetype,'downloadUrl': item.downloadUrl,'icon': this.getFileIcon(item)});}
      );
      document.dispatchEvent(new CustomEvent('open-attachments-preview', {detail: {'attachments': files,'id': file.id }}));
    },
    getFileIcon(attachment) {
      const extensions = this.$documentsIconsExtension;
      let extension = extensions[0].get(attachment?.mimeType);
      if (!extension) {
        extension = extensions[0].get('file');
      }
      return extension;
    },
  }
};
</script>