<template>
  <document-action-item
    icon="fas fa-shield-alt"
    :label="$t('documents.label.visibility')"
    @click="changeVisibility" />
</template>
<script>
export default {
  props: {
    file: {
      type: Object,
      default: null,
    },
    isMobile: {
      type: Boolean,
      default: false
    }
  },
  data: () => ({
    viewTab: 'RECENT',
    spaceId: eXo.env.portal.spaceId,
  }),
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