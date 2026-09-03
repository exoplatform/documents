<template>
  <v-progress-circular
    v-if="loading"
    size="24"
    color="primary"
    indeterminate />
  <div
    v-else-if="DocumentTitle"
    class="clickable float-left primary--text text-truncate"
    :style="`max-width: ${cellWidth}`"
    :title="DocumentTitle"
    @click="openPreview">
    {{ DocumentTitle }}
  </div>
  <div v-else-if="DocumentAccessDenied" class="d-flex">
    <i :title="$t('analytics.errorRetrievingDataForValue', {0: value})" class="uiIconColorError my-auto"></i>
    <span class="text-no-wrap text-sub-title my-auto ml-1">
      {{ $t('analytics.notAccessibleFile') }}
    </span>
  </div>
  <div v-else class="d-flex">
    <i :title="$t('analytics.errorRetrievingDataForValue', {0: value})" class="uiIconColorError my-auto"></i>
    <span class="text-no-wrap text-sub-title my-auto ml-1">
      {{ $t('analytics.DeletedFile') }}
    </span>
  </div>
</template>

<script>
export default {
  props: {
    value: {
      type: Object,
      default: () => null,
    },
    column: {
      type: Object,
      default: () => null,
    },
  },
  data: () => ({
    loading: true,
    attachment: {},
  }),
  computed: {
    cellWidth() {
      return this.column && this.column.width || '30vw';
    },
    DocumentTitle() {
      return this.attachment && this.attachment.name && unescape(this.attachment.name);
    },
    DocumentAccessDenied() {
      return this.attachment && this.attachment.acl && !this.attachment.acl.canAccess;
    },
    currentLanguage() {
      return eXo && eXo.env && eXo.env.portal && eXo.env.portal.language.replace('_','-') || 'en';
    },
    isFileEditable() {
      return  this.$supportedDocuments && this.$supportedDocuments.filter(doc => doc.edit && doc.mimeType === this.attachment.mimeType ).length > 0;
    },
    isFileReadable() {
      return this.$supportedDocuments && this.$supportedDocuments.filter(doc => doc.mimeType === this.attachment.mimeType).length > 0;
    },
    downloadUrl() {
      return this.$documentsUtils.getDownloadUrl(this.attachment.id, this.modifiedDate);
    },
    modifiedDate() {
      return this.attachment?.modifiedDate;
    },
    icon(){
      const fileIcon =  this.$documentsIconsExtension[0]?.get(this.attachment?.mimeType);
      return fileIcon ? fileIcon : this.file.folder ? this.$documentsIconsExtension[0]?.get('folder') : this.$documentsIconsExtension[0]?.get('file');
    },
  },
  created() {
    if (this.value) {
      this.loading = true;
      this.error = false;
      this.$attachmentService.getDocumentDetails(this.value)
        .then(attachment => {
          this.attachment = attachment;
        })
        .catch(() => this.attachment = {
          notFound: true,
          id: this.value
        })
        .finally(() => this.loading = false);
    } else {
      this.loading = false;
    }
  },
  methods: {
    openPreview() {
      this.loading = true;
      if (this.isFileEditable)  {
        if (this.attachment?.acl?.canEdit){
          this.openFileInEditor();
        } else {
          this.openFileInEditor('view');
        }
      } else if (this.isFileReadable)  {
        this.openFileInEditor('view');
      } else {
        const attachments = [];
        attachments.push({
          id: this.attachment.id,
          downloadUrl: this.downloadUrl,
          name: this.attachment.name,
          filename: this.attachment.name,
          mimetype: this.attachment.mimeType,
          icon: this.icon,
          editable: this.isFileEditable,
          readable: this.isFileReadable,
          path: this.attachment.path,
          source: 'documents'
        });
        document.dispatchEvent(new CustomEvent('open-attachments-preview', {detail: {'attachments': attachments,'id': this.attachment.id }}));
      }
      document.dispatchEvent(new CustomEvent('mark-attachment-as-viewed', {detail: {file: this.attachment}}));
      this.loading = false;
    },
    openFileInEditor(mode) {
      if (this.attachment && this.attachmentId) {
        const url = this.$documentsUtils.getEditorUrl(this.attachment,mode);
        this.$documentsUtils.openLink(url,'_blank');
      }
    },
  }
};
</script>
