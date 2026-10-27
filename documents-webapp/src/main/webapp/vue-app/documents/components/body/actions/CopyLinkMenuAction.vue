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
    fileInDriveRequest: null,
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
      this.fileInDriveRequest = null;
      const file = this.file;
      if (file && !file.folder) {
        this.fileInDriveRequest = this.$documentsUtils.getFileInDrive(file, this.$root.ownerId);
        this.fileInDriveRequest.then(fileInDrive => {
          if (file === this.file) {
            this.fileInDrive = fileInDrive;
          }
        });
      }
    },

    async copyLink() {
      this.loading = true;
      // Waits only while the drive path is still being resolved
      const file = this.fileInDrive || this.fileInDriveRequest && await this.fileInDriveRequest || this.file;
      const path = encodeURI(this.file.folder ? this.getFolderLink() : this.getDocumentLink(file));
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
    getFolderLink() {
      const path = `${window.location.origin}${eXo.env.portal.context}`;
      if (this.$root.ownerId !== eXo.env.portal.userIdentityId && this.$root.spaceId){
        return `${path}/s/${this.$root.spaceId}/documents?folderId=${this.file.id}`;
      } else if (eXo.env.portal.spaceId){
        const pathParts = eXo.env.portal.selectedNodeUri.split('home');
        const nodeUri = pathParts.length > 1 ? pathParts[1] : eXo.env.portal.selectedNodeUri.substring(eXo.env.portal.selectedNodeUri.indexOf('/documents'));
        return `${path}/s/${eXo.env.portal.spaceId}${nodeUri}?folderId=${this.file.id}`;
      } else {
        return `${path}/${eXo.env.portal.portalName}/${eXo.env.portal.selectedNodeUri}?folderId=${this.file.id}`;
      }
    },
    getDocumentLink(file) {
      if (this.isFileEditable())  {
        return `${window.location.origin}${this.$documentsUtils.getEditorUrl(file, this.file?.acl?.canEdit ? null : 'view')}`;
      } else if (this.isFileReadable())  {
        return `${window.location.origin}${this.$documentsUtils.getEditorUrl(file,'view')}`;
      } else {
        return `${window.location.origin}${this.$documentsUtils.getParentFolderUrl(file)}?documentPreviewId=${this.file.id}`;
      }
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