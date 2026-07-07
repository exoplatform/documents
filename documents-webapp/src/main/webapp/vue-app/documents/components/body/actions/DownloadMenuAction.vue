<template>
  <document-action-item
    icon="fas fa-download"
    :label="$t('documents.label.download')"
    :is-mobile="isMobile"
    show-divider
    @click="download" />
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
    },
    disabledExtension: {
      type: Boolean,
      default: false
    },
    isMultiSelection: {
      type: Boolean,
      default: false
    },
  },
  computed: {
    spaceId() {
      return eXo.env.portal.spaceId;
    },
  },
  methods: {
    download() {
      if (!this.isMultiSelection) {
        if (this.file?.folder) {
          this.$root.$emit('documents-folder-download', this.file);
          this.$root.$emit('close-file-action-menu');
          return;
        }
        // Download through the document content REST endpoint, which references the
        // document by its id. The file name never appears in the URL path (it is
        // returned by the server in the Content-Disposition header), so names
        // containing special characters such as "+" download correctly instead of
        // returning a 404.
        const urlDownload = this.$documentsUtils.getDownloadUrl(this.file.id, this.file.lastModified);
        const fileName = this.file.name;
        const a = document.createElement('a');
        a.href = urlDownload;
        a.download = fileName.replace(/\[[0-9]*\]$/g, '');
        document.body.appendChild(a);
        a.click();
        a.remove();
        document.dispatchEvent(new CustomEvent('download-file', {
          detail: {
            'type': 'file',
            'id': this.file.id,
            'spaceId': this.spaceId,
          }
        }));
        if ( this.isMobile ) {
          this.$root.$emit('close-file-action-menu');
        }
      } else {
        this.$root.$emit('documents-bulk-download');
        this.$root.$emit('close-file-action-menu');
      }
    }
  },
};
</script>
