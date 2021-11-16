<template>
  <v-btn class="btn btn-primary" @click="openDrawer">
    <v-icon class="me-2">mdi-plus</v-icon>
    {{ $t('documents.button.addNewFile') }}
  </v-btn>
</template>
<script>
export default {
  created() {
    document.addEventListener('entity-attachments-updated', this.refreshFilesList);
  },
  destroyed() {
    document.removeEventListener('entity-attachments-updated', this.refreshFilesList);
  },
  methods: {
    refreshFilesList() {
      this.$root.$emit('documents-refresh-files');
    },
    openDrawer() {
      document.dispatchEvent(new CustomEvent('open-attachments-app-drawer'));
    },
  },
};
</script>
