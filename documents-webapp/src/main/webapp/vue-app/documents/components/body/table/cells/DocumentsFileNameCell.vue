<template>
  <a
    class="attachment d-flex flex-nowrap text-color"
    @click="openPreview">
    <v-progress-circular
      v-if="loading"
      indeterminate
      size="16" />
    <v-icon
      v-else
      size="22"
      :color="icon.color">{{ icon.class }}</v-icon>
    <span
      :title="file.name"
      v-sanitized-html="file.name"
      class="text-truncate hover-underline ms-2">
    </span>
  </a>
</template>
<script>
export default {
  props: {
    file: {
      type: Object,
      default: null,
    },
    extension: {
      type: Object,
      default: null,
    },
  },
  data: () => ({
    loading: false,
  }),
  computed: {
    icon() {
      const type = this.file && this.file.mimeType || '';
      if (type.includes('pdf')) {
        return {
          class: 'fas fa-file-pdf',
          color: '#FF0000',
        };
      } else if (type.includes('presentation') || type.includes('powerpoint')) {
        return {
          class: 'fas fa-file-powerpoint',
          color: '#CB4B32',
        };
      } else if (type.includes('sheet') || type.includes('excel') || type.includes('csv')) {
        return {
          class: 'fas fa-file-excel',
          color: '#217345',
        };
      } else if (type.includes('word') || type.includes('opendocument') || type.includes('rtf') ) {
        return {
          class: 'fas fa-file-word',
          color: '#2A5699',
        };
      } else if (type.includes('plain')) {
        return {
          class: 'fas fa-file-alt',
          color: '#385989',
        };
      } else if (type.includes('image')) {
        return {
          class: 'fas fa-file-image',
          color: '#999999',
        };
      } else if (type.includes('video') || type.includes('octet-stream') || type.includes('ogg')) {
        return {
          class: 'fas fa-file-video',
          color: '#79577A',
        };
      } else if (type.includes('zip') || type.includes('war') || type.includes('rar')) {
        return {
          class: 'fas fa-file-archive',
          color: '#717272',
        };
      } else if (type.includes('illustrator') || type.includes('eps')) {
        return {
          class: 'fas fa-file-contract',
          color: '#E79E24',
        };
      } else {
        return {
          class: 'fas fa-file',
          color: '#578DC9',
        };
      }
    },
  },
  methods: {
    openPreview() {
      this.loading = true;
      this.$attachmentService.getAttachmentById(this.file.id)
        .then(attachment => {
          documentPreview.init({
            doc: {
              id: this.file.id,
              repository: 'repository',
              workspace: 'collaboration',
              path: attachment.path,
              title: attachment.title,
              icon: attachment.icon,
              size: attachment.size,
              openUrl: attachment.openUrl,
              downloadUrl: attachment.downloadUrl,
            },
            author: attachment.updater,
            showComments: false,
          });
        })
        .catch(e => console.error(e))
        .finally(() => {
          window.history.pushState('', '', `${eXo.env.server.portalBaseURL}?documentPreviewId=${this.file.id}`);
          this.loading = false;
        });
    },
  },
};
</script>