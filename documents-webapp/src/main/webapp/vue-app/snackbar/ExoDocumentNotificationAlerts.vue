<template>
  <v-snackbar
    :value="displayAlerts"
    color="transparent"
    elevation="0"
    app
    bottom
    left>
    <exo-document-notification-alert
      v-for="alert in alerts"
      :key="alert.message"
      :alert="alert"
      :is-mobile="isMobile"
      @dismissed="deleteAlert(alert)" />
  </v-snackbar>
</template>

<script>

export default {
  data: () => ({
    alerts: [],
  }),
  computed: {
    displayAlerts() {
      return this.alerts && this.alerts.length;
    },
    isMobile() {
      return this.$vuetify.breakpoint.name === 'xs' || this.$vuetify.breakpoint.name === 'sm';
    },
  },
  created() {
    this.$root.$on('document-notification-alert', alert => this.alerts.push(alert));
    this.$root.$on('confirm-document-deletion', (file) => {
      if (file && file.id) {
        const clickMessage = this.$t('documents.label.undoDelete');
        const message = this.$t('documents.label.deleteSuccess', {'0': file.folder ? this.$t('documents.label.folder') : this.$t('documents.label.file') });
        const administratorMessage = this.isMobile ? '' : this.$t('documents.label.contact.administrator');
        this.$root.$emit('document-notification-alert', {
          message,
          administratorMessage,
          type: 'success',
          click: () => this.undoDeleteDocument(file.id, file.folder),
          clickMessage,
        });
      }
    });
  },
  methods: {
    addAlert(alert) {
      const time = 5000;
      if (alert) {
        this.alerts.push(alert);
        window.setTimeout(() => this.deleteAlert(alert), time);
      }
    },
    deleteAlert(alert) {
      const index = this.alerts.indexOf(alert);
      this.alerts.splice(index, 1);
      this.$forceUpdate();
    },
    undoDeleteDocument(documentId, isFolder) {
      this.$root.$emit('undo-delete-document', documentId);
      return this.$documentFileService.undoDeleteDocument(documentId)
        .then(() => {
          this.deleteAlert(alert);
          this.addAlert({
            message: this.$t('documents.label.deleteCanceled', {'0': isFolder ? this.$t('documents.label.folder') : this.$t('documents.label.file') }),
            type: 'success',
          });
        });
    },
  },
};
</script>