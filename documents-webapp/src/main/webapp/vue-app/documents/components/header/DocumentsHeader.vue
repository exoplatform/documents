<template>
  <div class="d-flex flex-row">
    <documents-header-left />
    <v-spacer />
    <documents-header-right v-if="!isMobile" />
  </div>
</template>

<script>
export default {
  props: {
    isMobile: {
      type: Boolean,
      default: false
    }
  }
};
</script>