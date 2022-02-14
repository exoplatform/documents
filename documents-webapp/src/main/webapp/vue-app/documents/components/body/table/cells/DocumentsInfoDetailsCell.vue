<template>
  <div :id="`info-cell-file-${fileId}`">
    <v-tooltip bottom>
      <template v-slot:activator="{ on, attrs }">
        <v-btn
          icon
          small
          v-bind="attrs"
          v-on="on">
          <v-icon
            class="text-sub-title d-none mx-0 px-0"
            size="16"
            @click="$emit('open-info-drawer')"
            v-bind="attrs"
            v-on="on">
            fa-info-circle
          </v-icon>
        </v-btn>
      </template>
      <span>
        {{ documentInfoActionTooltip }}
      </span>
    </v-tooltip>
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
    documentInfoActionTooltip() {
      return this.$t('documents.label.show.details');
    },

  },
  methods: {
    openInfoDetailsDrawer(){
      this.$refs.documentInfoDrawer.open();
    }
  },
};
</script>