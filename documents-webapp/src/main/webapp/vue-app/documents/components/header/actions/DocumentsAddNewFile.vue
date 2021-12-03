<template>
  <div>
    <div :class="isMobile && !showMobileFilter || !isMobile ? '' : 'd-none'">
      <v-btn class="btn btn-primary" @click="openDrawer">
        <v-icon :class="!isMobile ? 'me-2' : ''">mdi-plus</v-icon>
        {{ !isMobile ? $t('documents.button.addNewFile') : '' }}
      </v-btn>
    </div>
    <div :class="isMobile && showMobileFilter || !isMobile ? '' : 'd-none'">
      <v-icon
        size="25"
        class="inputDocumentsFilter text-sub-title pa-1 my-auto "
        :class="isMobile && showMobileFilter ? '' : 'd-none'"
        @click="$root.$emit('document-search', null)">
        fas fa-arrow-left
      </v-icon>
    </div>
  </div>
</template>
<script>
export default {
  data: () => ({
    showMobileFilter: false,
  }),
  computed: {
    isMobile() {
      return this.$vuetify.breakpoint.name === 'xs' || this.$vuetify.breakpoint.name === 'sm';
    },
  },
  created() {
    this.$root.$on('show-mobile-filter', data => {
      this.showMobileFilter= data;
    });
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
