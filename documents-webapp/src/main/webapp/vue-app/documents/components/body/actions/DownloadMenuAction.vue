<template>
  <div
    class="downloadDocumentNewApp clickable mx-2"
    @click="download">
    <v-icon
      size="13"
      class="pe-1 iconStyle">
      fas fa-download
    </v-icon>
    <span class="ps-1">{{ $t('documents.label.download') }}</span>
    <v-divider
      v-if="isMobile"
      class="mt-2 dividerStyle"
      dark />
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
  computed: {
    isMobile() {
      return this.$vuetify.breakpoint.name === 'xs' || this.$vuetify.breakpoint.name === 'sm';
    },
    spaceId() {
      return eXo.env.portal.spaceId;
    },
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
            document.dispatchEvent(new CustomEvent('download-file', {
              detail: {
                'type': 'file',
                'id': this.file.id,
                'spaceId': this.spaceId,
              }
            }));
          });
        });
      if ( this.isMobile ) {
        this.$root.$emit('close-file-action-menu');
      }
    }
  },
};
</script>