<template>
  <div class="align-center">
    <span class="text-light-color">{{ size }}</span>
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
      if (this.file.folder) {
        return '';
      }
      const size = this.$documentsUtils.getSize(this.file.size );
      return  `${size.value} ${this.$t(`document.size.label.unit.${size.unit}`)}`;
    }
  }
};
</script>