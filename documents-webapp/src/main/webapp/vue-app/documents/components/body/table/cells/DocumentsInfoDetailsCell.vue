<template>
  <div
    :id="`info-cell-file-${fileId}`">
    <div>
      <v-icon
        class="text-light-color d-none ma-1 px-1"
        @click="$emit('open-info-drawer')"
        :title="$t('documents.label.show.details')"
        small>
        fa-info-circle
      </v-icon>
    </div>
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