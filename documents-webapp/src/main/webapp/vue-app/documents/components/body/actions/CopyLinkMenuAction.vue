<template>
  <div
    class="clickable pt-1 mx-2"
    @click="copyLink()">
    <v-icon
      size="13"
      class="pe-1 iconStyle">
      mdi-link-variant
    </v-icon>
    <span class="ps-1">{{ $t('documents.label.copy.link') }}</span>
    <v-divider
      v-if="!file.cloudDriveFolder"
      class="mt-1 dividerStyle" />
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
  methods: {
    copyLink() {
      const inputTemp = $('<input>');
      const pathParts = window.location.href.toLowerCase().split(eXo.env.portal.selectedNodeUri.toLowerCase());
      let path = `${pathParts[0]}${eXo.env.portal.selectedNodeUri}`;
      if (this.file.folder){
        path = `${path}?folderId=${this.file.id}`;
      } else {
        path = `${path}?documentPreviewId=${this.file.id}`;
      }
      $('body').append(inputTemp);
      inputTemp.val(path).select();
      document.execCommand('copy');
      inputTemp.remove();
      this.$root.$emit('show-alert', {type: 'success', message: this.$t('documents.alert.success.label.linkCopied')});
      this.getDocumentView();
      document.dispatchEvent(new CustomEvent('document-change', {
        detail: {
          'category': this.file.folder ? 'Folder' : 'Document',
          'spaceId': this.spaceId,
          'name': 'Action copy link',
          'view': this.viewTab
        }
      }));
      this.$root.$emit('close-file-action-menu');
    },
    getDocumentView() {
      const currentUrlSearchParams = window.location.search;
      const queryParams = new URLSearchParams(currentUrlSearchParams);
      if (queryParams.has('view')) {
        const view = queryParams.get('view');
        this.viewTab = view.toLowerCase() === 'folder' ? 'Folder' : 'RECENT';
      }
    },
  },
};
</script>