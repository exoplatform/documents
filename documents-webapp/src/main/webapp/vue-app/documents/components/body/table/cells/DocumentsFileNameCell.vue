<template>
  <a
    class="attachment d-flex flex-nowrap text-color"
    @click="openPreview">
    <v-progress-circular
      v-if="loading"
      indeterminate
      size="16" />
    <v-icon v-else :color="icon.color">{{ icon.class }}</v-icon>
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
          class: 'mdi-file-pdf',
          color: '#d07b7b',
        };
      } else if (type.includes('presentation') || type.includes('powerpoint')) {
        return {
          class: 'mdi-file-powerpoint',
          color: '#e45030',
        };
      } else if (type.includes('sheet') || type.includes('excel')) {
        return {
          class: 'mdi-file-excel',
          color: '#1a744b',
        };
      } else if (type.includes('word') || type.includes('opendocument') || type.includes('rtf') ) {
        return {
          class: 'mdi-file-word',
          color: '#094d7f',
        };
      } else if (type.includes('plain')) {
        return {
          class: 'mdi-clipboard-text',
          color: '#1c9bd7',
        };
      } else if (type.includes('image')) {
        return {
          class: 'mdi-image',
          color: '#eab320',
        };
      } else {
        return {
          class: 'mdi-file',
          color: '#cdcccc',
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