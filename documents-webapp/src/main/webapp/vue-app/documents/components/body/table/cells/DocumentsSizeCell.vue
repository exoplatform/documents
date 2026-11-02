<template>
  <div class="documents-size-value text-no-wrap">
    <span
      v-if="size"
      class="text-light-color">{{ size }}</span>
    <template v-else>
      <span class="text-light-color" aria-hidden="true">—</span>
      <span class="d-sr-only">{{ $t('documents.label.size.none') }}</span>
    </template>
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
    size(){
      if (this.file.folder || this.file.drive) {
        return '';
      }
      const size = this.$documentsUtils.getSize(this.file.size );
      return  `${size.value} ${this.$t(`document.size.label.unit.${size.unit}`)}`;
    }
  }
};
</script>
