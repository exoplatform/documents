<template>
  <div
    class="clickable pt-1 mx-2"
    @click="duplicate()">
    <v-icon
      size="13"
      class="pe-1 iconStyle">
      fas fa-clone
    </v-icon>
    <span class="ps-1">{{ $t('documents.label.duplicate') }}</span>
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
    workspace: 'collaboration',
  }),
  computed: {
    isMobile() {
      return this.$vuetify.breakpoint.name === 'xs' || this.$vuetify.breakpoint.name === 'sm';
    }
  },
  methods: {
    duplicate(){
      this.$root.$emit('duplicate-document',this.file);
      if ( this.isMobile ) {
        this.$root.$emit('close-file-action-menu');
      }
    }
  },
};
</script>