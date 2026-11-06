<template>
  <div :id="`info-cell-file-${fileId}`">
    <v-tooltip
      :open-delay="500"
      bottom>
      <template #activator="{ on, attrs }">
        <v-btn
          v-bind="attrs"
          v-on="on"
          :aria-label="$t('documents.label.show.details.of', {0: file.name})"
          class="documents-hover-reveal"
          icon
          small
          @click="displayDetails">
          <v-icon
            class="mx-0 px-0"
            size="16"
            aria-hidden="true">
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
      document.dispatchEvent(new CustomEvent('open-document-info-drawer', {detail: {id: this.fileId, ownerId: this.$root.ownerId}}));
    }
  },
};
</script>