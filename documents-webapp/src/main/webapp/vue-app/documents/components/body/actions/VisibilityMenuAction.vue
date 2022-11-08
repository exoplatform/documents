<template>
  <div
    class="clickable ma-auto pt-1 mx-2"
    @click="changeVisibility()">
    <v-icon
      size="13"
      class="pe-1 iconStyle">
      fas fa-eye
    </v-icon>
    <span class="ps-1">{{ $t('documents.label.visibility') }}</span>
  </div>
</template>
<script>
export default {
  props: {
    file: {
      type: Object,
      default: null,
    }
  },
  data: () => ({
    viewTab: 'RECENT',
    spaceId: eXo.env.portal.spaceId,
  }),
  computed: {
    isMobile() {
      return this.$vuetify.breakpoint.name === 'xs' || this.$vuetify.breakpoint.name === 'sm';
    }
  },
  methods: {
    changeVisibility(){
      this.$root.$emit('open-visibility-drawer', this.file);
      this.getDocumentView();
      document.dispatchEvent(new CustomEvent('manage-access', {
        detail: {
          'category': this.file.folder ? 'Folder' : 'Document',
          'spaceId': this.spaceId,
          'view': this.viewTab
        }
      }));
      if (this.isMobile) {
        this.$root.$emit('close-file-action-menu');
      }
    },
    getDocumentView() {
      const currentUrlSearchParams = window.location.search;
      const queryParams = new URLSearchParams(currentUrlSearchParams);
      if (queryParams.has('view')) {
        const view = queryParams.get('view');
        this.viewTab = view.toLowerCase() === 'folder' ? 'Folder' : 'RECENT';
      }
    }
  }
};
</script>