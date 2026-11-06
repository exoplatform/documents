<template>
  <td
    :colspan="colspan"
    class="transparent ps-1">
    <v-btn
      :aria-expanded="open ? 'true' : 'false'"
      class="transparent no-border no-box-shadow ps-3"
      text
      @click="toggleFunction">
      <v-icon aria-hidden="true">{{ icon }}</v-icon>
      <v-divider
        class="mx-2"
        aria-hidden="true"
        vertical />
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
    }
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
      return this.$t(`documents.timeline.label.${labelKey}`);
    }
  },
};
</script>