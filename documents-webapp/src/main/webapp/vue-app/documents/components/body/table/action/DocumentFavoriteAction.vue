<template>
  <documents-favorite-button
    :file="file"
    @removed="removed"
    @added="added" />
</template>

<script>
export default {
  props: {
    file: {
      type: Object,
      default: null,
    },
  },
  methods: {
    removed() {
      this.$emit('removed');
      this.$root.$emit('documents-refresh-files');
    },
    added() {
      this.$emit('added');
      this.$root.$emit('documents-refresh-files');
    },
  },
};
</script>