<template>
  <v-card
    :href="href"
    color="transparent"
    class="pa-2"
    flat>
    <v-icon
      size="16"
      class="px-1px me-1">
      {{ icon }}
    </v-icon>
    <span class="ps-1 text-body menu-text-color">{{ $t('documents.label.openInDesktop') }}</span>
  </v-card>
</template>
<script>
export default {
  props: {
    file: {
      type: Object,
      default: null,
    },
  },
  computed: {
    iconExtension() {
      return this.$documentsIconsExtension?.[0]?.get?.(this.file?.mimeType);
    },
    protocol() {
      return this.iconExtension.protocol;
    },
    icon() {
      return this.iconExtension.class;
    },
    href() {
      return `${this.protocol}${window.origin}/${eXo.env.portal.rest}/private/jcr/repository/collaboration${this.file.path}`;
    },
  },
};
</script>