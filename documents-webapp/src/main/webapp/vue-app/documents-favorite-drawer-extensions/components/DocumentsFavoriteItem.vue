<template>
  <v-list-item class="clickable" @click="openPreview()">
    <v-list-item-icon class="me-3 my-auto">
      <v-icon
        size="25"
        :color="iconColor">
        {{ iconClass }}
      </v-icon>
    </v-list-item-icon>

    <v-list-item-content>
      <v-list-item-title class="text-color body-2">{{ documentTitle }}</v-list-item-title>
    </v-list-item-content>

    <v-list-item-action>
      <favorite-button
        :id="id"
        :favorite="isFavorite"
        :top="top"
        :right="right"
        type="file"
        type-label="Documents"
        @removed="removed"
        @remove-error="removeError" />
    </v-list-item-action>
  </v-list-item>
</template>
<script>
export default {
  props: {
    id: {
      type: String,
      default: () => null,
    },
  },
  data: () => ({
    documentTitle: '',
    file: {},
    documentPreviewInit: {},
    dateFormat: {
      year: 'numeric',
      month: 'long',
      day: 'numeric',
      hour: 'numeric',
      minute: 'numeric',
      second: 'numeric',
    },
    iconColor: '#476A9C',
    iconClass: 'fas fa-file',
    isFavorite: true,
  }),
  created() {
    this.$attachmentService.getAttachmentById(this.id)
      .then(file => { 
        this.file = file;
        this.getFileIcon(this.file?.mimetype);
        this.documentTitle = file.title;
        try {
          decodeURI(decodeURI(file.title));
        } catch (error) {
          // No problem, we can use the title as is, it does not need to be decoded and it contains a % character
        }
        const updaterFullName = file?.updater?.profile?.fullname || '';
        const updateDate = new Date(file.updated);
        const updateDateInfo = this.$dateUtil.formatDateObjectToDisplay(updateDate, this.dateFormat);
        const fileInfo = `${this.$t('documents.preview.updatedOn')} ${updateDateInfo} ${this.$t('documents.preview.updatedBy')} ${updaterFullName} ${file.size}`;
        this.documentPreviewInit = {
          doc: {
            id: file.id,
            repository: 'repository',
            workspace: 'collaboration',
            title: file.title,
            downloadUrl: file.downloadUrl,
            openUrl: file.openUrl,
            breadCrumb: file.previewBreadcrumb,
            fileInfo: fileInfo,
            size: file.size,
            isCloudDrive: file.cloudDrive
          },
          author: file.updater,
          version: {
            number: file.version && Number(file.version) || 0,
          },
          showComments: false,
          showOpenInFolderButton: false,
        };
      });
  },
  methods: {
    getFileIcon(mimeType) {
      const extensions = extensionRegistry.loadExtensions('documents', 'documents-icons-extension');
      let extension = extensions[0].get(mimeType);
      if (!extension) {
        extension = extensions[0].get('file');
      }
      this.iconColor = extension.color;
      this.iconClass = extension.class;
    },
    openPreview() {
      documentPreview.init(this.documentPreviewInit);
      this.$root.$emit('close-favorite-drawer');
    },
    removed() {
      this.isFavorite = !this.isFavorite;
      this.displayAlert(this.$t('Favorite.tooltip.SuccessfullyDeletedFavorite', {0: this.$t('file.label')}));
      this.$emit('removed');
      this.$root.$emit('refresh-favorite-list');
    },
    removeError() {
      this.displayAlert(this.$t('Favorite.tooltip.ErrorDeletingFavorite', {0: this.$t('file.label')}), 'error');
    },
    displayAlert(message, type) {
      document.dispatchEvent(new CustomEvent('attachments-notification-alert', {
        detail: {
          messageObject: {
            message: message,
            type: type || 'success',
          }
        }
      }));
    },
  }

};
</script>
