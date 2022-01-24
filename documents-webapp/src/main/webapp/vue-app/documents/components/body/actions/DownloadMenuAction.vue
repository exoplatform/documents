<template>
  <div
    class="downloadDocumentNewApp clickable d-flex"
    @click="download">
    <v-icon
      size="18"
      class="primary--text pe-2">
      mdi-download
    </v-icon>
    <span>{{ $t('documents.label.download') }}</span>
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
  methods: {
    download() {
      this.$attachmentService.getAttachmentById(this.file.id)
        .then(attachment => {
          this.downloadUrl = attachment.downloadUrl;
        })
        .catch(e => console.error(e))
        .finally(() => {
          const urlDownload = this.downloadUrl;
          const fileName = this.file.name;
          if (urlDownload.indexOf('/') > 0 && !urlDownload.includes(window.location.hostname)) {
            return;
          }
          return fetch(urlDownload, {
            credentials: 'include',
            method: 'GET',
          }).then(resp => {
            if (resp && resp.ok) {
              return resp.blob();
            } else {
              throw new Error(`Error downloading file '${urlDownload}' from server`);
            }
          }).then(blob => {
            const url = window.URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = fileName.replace(/\[[0-9]*\]$/g, '');
            document.body.appendChild(a);
            a.click();
            a.remove();
          });
        });
    }
  },
};
</script>