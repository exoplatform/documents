<template>
  <div class="attachment d-flex flex-nowrap">
    <i :class="fileTypeClass"></i>
    <span
      v-sanitized-html="file.name"
      class="text-truncate ms-2">
    </span>
  </div>
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
};
</script>