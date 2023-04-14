<template>
  <div class="align-center">
    <span class="text-light-color">{{ getSize(file) }}</span>
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
    getSize(file) {
      if (file.folder) {
        return ;
      }
      const k = file.size > 0 ? Math.floor((Math.log2(file.size) / 10)) : 0;
      const rank = `${k > 0 ? 'KMGT'[k - 1] : ''}B`;
      const count = (file.size / Math.pow(1024, k)).toFixed(2);
      return count + rank;
    }

  }
};
</script>