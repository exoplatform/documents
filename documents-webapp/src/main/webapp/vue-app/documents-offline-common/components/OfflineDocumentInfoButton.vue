<template>
  <v-tooltip bottom>
    <template #activator="{ on, attrs }">
      <v-btn
        v-bind="attrs"
        v-on="on"
        icon
        @click.stop.prevent="displayDetails">
        <v-icon
          v-bind="attrs"
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
  },
  methods: {
    displayDetails(){
      document.dispatchEvent(new CustomEvent('open-document-info-drawer', {detail: this.fileId}));
    }
  },
};
</script>