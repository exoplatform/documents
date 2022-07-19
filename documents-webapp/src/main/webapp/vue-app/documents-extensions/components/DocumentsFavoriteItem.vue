<template>
  <v-list-item class="clickable" @click="openPreview()">
    <v-list-item-icon class="me-3 my-auto">
      <v-icon size="22" class="icon-default-color"> fas fa-folder-open </v-icon>
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
    isFavorite: true,
  }),
  created() {
    this.$attachmentService.getAttachmentById(this.id)
      .then(file => { 
        this.file = file;
        this.documentTitle = decodeURI(decodeURI(file.title)) ;
        const updaterFullName = file && file.updater && file.updater.profile && file.updater.profile.fullname || '';
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
      document.dispatchEvent(new CustomEvent('notification-alert', {detail: {
        message,
        type: type || 'success',
      }}));
    },
  }

};
</script>
