<template>
  <div
    :id="`info-cell-file-${fileId}`"
    class="px-4">
    <v-icon
      class="text-sub-title d-none px-3"
      size="16"
      @click="$emit('open-info-drawer')"
      :title="$t('documents.label.show.details')">
      fa-info-circle
    </v-icon>
    <documents-info-drawer ref="documentInfoDrawer" :file="file" />
  </div>
</template>

<script>
export default {
  props: {
    file: {
      type: Object,
      default: null,
    },
    extension: {
      type: Object,
      default: null,
    },
  },
  data: () => ({

  }),
  computed: {
    fileId() {
      return this.file && this.file.id;
    },
  },
  methods: {
    openInfoDetailsDrawer(){
      this.$refs.documentInfoDrawer.open();
    }
  },
};
</script>