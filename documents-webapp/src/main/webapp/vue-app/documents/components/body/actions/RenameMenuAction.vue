<template>
  <div
    class="clickable"
    @click="editNameMode">
    <v-icon
      size="18"
      class="primary--text pe-1">
      mdi-form-textbox
    </v-icon>
    <span>{{ $t('document.label.rename') }}</span>
    <v-divider
      v-if="!isMobile"
      class="mt-2"
      dark />
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
    }
  },
};
</script>