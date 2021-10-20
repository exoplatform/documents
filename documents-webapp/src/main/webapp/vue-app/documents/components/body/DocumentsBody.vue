<template>
  <div class="documents-body">
    <extension-registry-component
      v-if="viewExtension"
      :component="viewExtension"
      :element="div" />
  </div>
</template>

<script>
export default {
  props: {
    viewExtension: {
      type: Object,
      default: null,
    },
  },
};
</script>