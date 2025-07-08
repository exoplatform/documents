<template>
  <div>
    <div
      class="clickable py-10px px-4"
      @click="copyLink()">
      <v-icon
        size="16"
        class="pe-1">
        fas fa-link
      </v-icon>
      <span class="ps-1 text-body menu-text-color">{{ $t('documents.label.copy.link') }}</span>
    </div>
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
    publicAccessVisibilityChoice: 'COLLABORATORS_AND_PUBLIC_ACCESS'
  }),
  methods: {

    copyLink() {
      this.loading = true;
      let path;
      if (this.isFileEditable())  {
        if (this.file?.acl?.canEdit){
          path =  `${window.location.host}${this.$documentsUtils.getEditorUrl(this.file,null)}`;
        } else {
          path =  `${window.location.host}${this.$documentsUtils.getEditorUrl(this.file,'view')}`;
        }
      } else if (this.isFileReadable())  {
        path =  `${window.location.host}${this.$documentsUtils.getEditorUrl(this.file,'view')}`;
      } else {
        path = `${window.location.host}${this.$documentsUtils.getParentFolderUrl(this.file)}?documentPreviewId=${this.file.id}`;
      }
      const input = document.createElement('input');
      input.value = path;
      document.body.appendChild(input);
      input.select();
      document.execCommand('copy');
      document.body.removeChild(input);
      this.loading = false;
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
    },
    isFileEditable() {
      return  this.$supportedDocuments && this.$supportedDocuments.filter(doc => doc.edit && doc.mimeType === this.file.mimeType ).length > 0;
    },
    isFileReadable() {
      return this.$supportedDocuments && this.$supportedDocuments.filter(doc => doc.mimeType === this.file.mimeType).length > 0;
    }
  },
};
</script>