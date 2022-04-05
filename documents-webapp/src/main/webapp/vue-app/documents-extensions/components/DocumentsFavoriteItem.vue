<template>
  <v-list-item class="clickable" @click="openPreview">
    <v-list-item-icon class="me-3 my-auto">
      <v-icon size="22" class="icon-default-color"> fas fa-folder-open </v-icon>
    </v-list-item-icon>

    <v-list-item-content>
      <v-list-item-title class="text-color body-2">{{ documentTitle }}</v-list-item-title>
    </v-list-item-content>

    <v-list-item-action>
      <v-btn icon>
        <v-icon class="yellow--text text--darken-2" size="18">fa-star</v-icon>
      </v-btn>
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
    documentPreviewInit: {},
    dateFormat: {
      year: 'numeric',
      month: 'long',
      day: 'numeric',
      hour: 'numeric',
      minute: 'numeric',
      second: 'numeric',
    },
  }),
  created() {
    this.$attachmentService.getAttachmentById(this.id)
      .then(file => { 
        this.documentTitle = file.title;
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
    }
  }

};
</script>
