<template>
  <div
    class="clickable pt-1"
    @click="editNameMode">
    <v-icon
      size="13"
      class="pe-1 iconStyle">
      mdi-form-textbox
    </v-icon>
    <span>{{ $t('document.label.rename') }}</span>
    <v-divider
      v-if="!isMobile"
      class="mt-2 dividerStyle" />
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
    editName: false
  }),
  computed: {
    isMobile() {
      return this.$vuetify.breakpoint.name === 'xs' || this.$vuetify.breakpoint.name === 'sm';
    },
  },
  methods: {
    editNameMode(){
      this.$root.$emit('update-file-name', this.file);
      if ( this.isMobile ) {
        this.$root.$emit('close-file-action-menu');
      }
    }
  },
};
</script>