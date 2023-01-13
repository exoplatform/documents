<template>
  <div
    class="clickable mx-2"
    @click="displayDetails()">
    <v-icon
      size="13"
      class="pe-1 iconStyle">
      fa-info-circle
    </v-icon>
    <span class="ps-1">{{ $t('documents.drawer.details.title') }}</span>
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
    displayDetails(){
      this.$root.$emit('open-info-drawer', this.file);
      if (this.isMobile) {
        this.$root.$emit('close-file-action-menu');
      }
    }
  },
};
</script>