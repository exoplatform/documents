<template>
  <div>
    <div
      class="clickable py-10px px-4"
      @click="editNameMode()">
      <v-icon
        size="16"
        class="pe-1">
        fas fa-pen
      </v-icon>
      <span class="text-body menu-text-color ps-1">{{ $t('document.label.rename') }}</span>
    </div>
  </div>
</template>
<script>
export default {
  props: {
    file: {
      type: Object,
      default: null,
    },
    isMobile: {
      type: Boolean,
      default: false
    }
  },
  data: () => ({
    editName: false
  }),
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