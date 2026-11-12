<template>
  <v-tooltip bottom>
    <template #activator="{ on }">
      <v-btn
        v-on="on"
        :class="{'d-sr-only-focusable': hidden}"
        :aria-label="fileName ? $t('documents.label.show.details.of', {0: fileName}) : $t('documents.label.show.details')"
        icon
        @click.stop.prevent="displayDetails">
        <v-icon
          v-on="on"
          size="16">
          fa-info-circle
        </v-icon>
      </v-btn>
    </template>
    <span>{{ $t('documents.label.show.details') }}</span>
  </v-tooltip>
</template>
<script>
export default {
  props: {
    fileId: {
      type: String,
      default: null,
    },
    fileName: {
      type: String,
      default: null,
    },
    hidden: {
      type: Boolean,
      default: false,
    },
  },
  methods: {
    displayDetails(){
      document.dispatchEvent(new CustomEvent('open-document-info-drawer', {detail: this.fileId}));
    }
  },
};
</script>