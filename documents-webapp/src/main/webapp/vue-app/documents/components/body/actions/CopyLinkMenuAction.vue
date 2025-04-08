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
      let path = `${window.location.host}${eXo.env.portal.context}`;
      if (eXo.env.portal.spaceId){
        const pathParts = eXo.env.portal.selectedNodeUri.split('home');
        const nodeUri = pathParts.length > 1 ? pathParts[1] : eXo.env.portal.selectedNodeUri;
        path = `${path}/s/${eXo.env.portal.spaceId}${nodeUri}`;
      } else {
        path = `${path}/${eXo.env.portal.metaPortalName}/${eXo.env.portal.selectedNodeUri}`;
      }
      if (this.file.folder){
        path = `${path}?folderId=${this.file.id}`;
      } else if (Vue.prototype?.$supportedDocuments.filter(doc => doc.edit && doc.mimeType === this.file?.mimeType).length > 0){
        path = `${window.location.host}${eXo.env.portal.context}/${eXo.env.portal.metaPortalName}/oeditor?docId=${this.file.id}&mode=view`;
      } else {
        path = `${path}?documentPreviewId=${this.file.id}`;
      }
      path = `${window.location.protocol}//${path}`;
      if (navigator?.clipboard?.writeText) {
        navigator.clipboard.writeText(path).catch(() => {
          this.copy(path);
        });
      } else {
        this.copy(path);
      }
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
    copy(path) {
      const input = document.createElement('input');
      input.value = path;
      document.body.appendChild(input);
      input.select();
      document.execCommand('copy');
      document.body.removeChild(input);
    }
  },
};
</script>