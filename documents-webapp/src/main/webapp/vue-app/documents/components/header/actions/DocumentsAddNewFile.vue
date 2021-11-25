<template>
  <v-btn class="btn btn-primary" @click="openDrawer">
    <v-icon :class="!isMobile ? 'me-2' : ''">mdi-plus</v-icon>
    {{ !isMobile ? $t('documents.button.addNewFile') : '' }}
  </v-btn>
</template>
<script>
export default {
  computed: {
    isMobile() {
      return this.$vuetify.breakpoint.name === 'xs' || this.$vuetify.breakpoint.name === 'sm';
    },
  },
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
