<template>
  <td
    v-if="!query"
    :colspan="colspan"
    class="white ps-1">
    <v-btn
      class="white no-border no-box-shadow ps-3"
      text
      @click="toggleFunction">
      <v-icon class="text-light-color">{{ icon }}</v-icon>
      <v-divider vertical class="mx-2" />
      {{ label }}
    </v-btn>
  </td>
</template>
<script>
export default {
  props: {
    group: {
      type: String,
      default: null,
    },
    query: {
      type: String,
      default: null,
    },
    headers: {
      type: Array,
      default: null,
    },
    files: {
      type: Array,
      default: null,
    },
    open: {
      type: Boolean,
      default: false,
    },
    toggleFunction: {
      type: Object,
      default: null,
    },
  },
  computed: {
    colspan() {
      return this.headers && this.headers.length || 0;
    },
    icon() {
      return this.open && 'mdi-chevron-down' || 'mdi-chevron-up';
    },
    filesCount() {
      return this.files && this.files.length || 0;
    },
    label() {
      if (!this.group) {
        return '';
      }
      const labelKey = this.group.split(':')[1];
      return this.$t(`documents.timeline.${labelKey}`);
    },
  },
};
</script>