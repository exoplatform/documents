<template>
  <div
    class="clickable ma-auto pt-1 mx-2"
    @click="changeVisibility()">
    <v-icon
      size="13"
      class="pe-1 iconStyle">
      fas fa-eye
    </v-icon>
    <span class="ps-1">{{ $t('documents.label.visibility') }}</span>
    <v-divider
      v-if="!versionHistoryEnabled"
      class="mt-1 dividerStyle" />
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
  data: () => ({
  }),
  computed: {
    isMobile() {
      return this.$vuetify.breakpoint.name === 'xs' || this.$vuetify.breakpoint.name === 'sm';
    },
    versionHistoryEnabled() {
      return eXo.env.portal.versionHistoryEnabled;
    }
  },
  methods: {
    changeVisibility(){
      this.$root.$emit('open-visibility-drawer', this.file);
      if (this.isMobile) {
        this.$root.$emit('close-file-action-menu');
      }
    }
  },
};
</script>