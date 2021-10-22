<template>
  <a
    class="attachment d-flex flex-nowrap text-color"
    @click="openPreview">
    <v-progress-circular
      v-if="loading"
      indeterminate
      size="16" />
    <i v-else :class="fileTypeClass"></i>
    <span
      :title="file.name"
      v-sanitized-html="file.name"
      class="text-truncate ms-2">
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
    fileTypeClass() {
      if (this.file.mimeType) {
        const fileMimeTypeClass = this.file.mimeType.replace(/\./g, '').replace('/', '').replace('\\', '');
        return `uiIconFileType${fileMimeTypeClass} uiIconFileTypeDefault my-auto`;
      } else {
        return 'uiIconFileTypeDefault my-auto';
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
        .finally(() => this.loading = false);
    },
  },
};
</script>