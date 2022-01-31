<template>
  <div
    class="clickable pt-1"
    @click="$root.$emit('open-info-drawer',file.id)">
    <v-icon
      size="18">
      fa-info-circle
    </v-icon>
    <span>{{ $t('documents.drawer.details.title') }}</span>
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