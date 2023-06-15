<template>
  <div
    class="downloadDocumentNewApp clickable mx-2"
    @click="download()">
    <v-icon
      size="13"
      class="pe-1 iconStyle">
      fas fa-download
    </v-icon>
    <span class="ps-1">{{ $t('documents.label.download') }}</span>
  </div>
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
        this.$attachmentService.getAttachmentById(this.file.id)
          .then(attachment => {
            const nodeName = attachment.path.substring(attachment.path.lastIndexOf('/') + 1 );
            this.downloadUrl = attachment.downloadUrl.replace(nodeName, encodeURIComponent(nodeName).replaceAll('%', '%25')) ;
          })
          .catch(e => console.error(e))
          .finally(() => {
            const urlDownload = this.downloadUrl;
            const fileName = this.file.name;
            if (urlDownload.indexOf('/') > 0 && !urlDownload.includes(window.location.hostname)) {
              return;
            }
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
          });
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
