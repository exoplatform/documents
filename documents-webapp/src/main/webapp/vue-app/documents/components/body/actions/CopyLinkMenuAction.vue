<template>
  <document-action-item
    icon="fas fa-link"
    :label="$t('documents.label.copy.link')"
    @click="copyLink" />
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
    publicAccessVisibilityChoice: 'COLLABORATORS_AND_PUBLIC_ACCESS',
    fileInDrive: null,
  }),
  watch: {
    file: {
      immediate: true,
      handler() {
        this.resolveFileInDrive();
      },
    },
  },
  methods: {
    resolveFileInDrive() {
      this.fileInDrive = null;
      const file = this.file;
      if (file && !file.folder) {
        this.$documentsUtils.getFileInDrive(file, this.$root.ownerId)
          .then(fileInDrive => {
            if (file === this.file) {
              this.fileInDrive = fileInDrive;
            }
          });
      }
    },

    copyLink() {
      this.loading = true;
      let path;
      const file = this.fileInDrive || this.file;
      if (this.file.folder){
        path = `${window.location.origin}${eXo.env.portal.context}`;
        if (this.$root.ownerId !== eXo.env.portal.userIdentityId && this.$root.spaceId){
          path = `${path}/s/${this.$root.spaceId}/documents?folderId=${this.file.id}`;
        }
        else if (eXo.env.portal.spaceId){
          const pathParts = eXo.env.portal.selectedNodeUri.split('home');
          const nodeUri = pathParts.length > 1 ? pathParts[1] : eXo.env.portal.selectedNodeUri.substring(eXo.env.portal.selectedNodeUri.indexOf('/documents'));
          path = `${path}/s/${eXo.env.portal.spaceId}${nodeUri}?folderId=${this.file.id}`;
        } else {
          path = `${path}/${eXo.env.portal.portalName}/${eXo.env.portal.selectedNodeUri}?folderId=${this.file.id}`;
        }
      } else if (this.isFileEditable())  {
        if (this.file?.acl?.canEdit){
          path =  `${window.location.origin}${this.$documentsUtils.getEditorUrl(file,null)}`;
        } else {
          path =  `${window.location.origin}${this.$documentsUtils.getEditorUrl(file,'view')}`;
        }
      } else if (this.isFileReadable())  {
        path =  `${window.location.origin}${this.$documentsUtils.getEditorUrl(file,'view')}`;
      } else {
        path = `${window.location.origin}${this.$documentsUtils.getParentFolderUrl(file)}?documentPreviewId=${this.file.id}`;
      }
      path = encodeURI(path);
      if (navigator?.clipboard?.writeText) {
        navigator.clipboard.writeText(path).catch(() => {
          this.copy(path);
        });
      } else {
        this.copy(path);
      }
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