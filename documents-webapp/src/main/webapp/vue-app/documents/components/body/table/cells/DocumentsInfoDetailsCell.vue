<template>
  <div :id="`info-cell-file-${fileId}`">
    <v-tooltip bottom>
      <template #activator="{ on, attrs }">
        <v-btn
          icon
          small
          v-bind="attrs"
          v-on="on">
          <v-icon
            v-bind="attrs"
            v-on="on"
            class="text-sub-title d-none mx-0 px-0"
            size="16"
            @click="displayDetails">
            fa-info-circle
          </v-icon>
        </v-btn>
      </template>
      <span>
        {{ documentInfoActionTooltip }}
      </span>
    </v-tooltip>
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
  computed: {
    fileId() {
      return this.file && this.file.id;
    },
    documentInfoActionTooltip() {
      return this.$t('documents.label.show.details');
    },
  },

  methods: {
    displayDetails(){
      document.dispatchEvent(new CustomEvent('open-document-info-drawer', {detail: this.fileId}));
    }
  },
};
</script>