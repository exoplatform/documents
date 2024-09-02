<template>
  <v-card
    class="d-flex align-center justify-center grey-background"
    flat>
    <v-card
      min-height="72"
      width="340"
      max-width="100%"
      class="d-flex flex-column flex-grow-0 align-center justify-center overflow-hidden pa-4"
      flat>
      <documents-widget-header
        :settings="settings"
        class="full-width"
        has-documents-widget />
      <documents-widget-list
        :settings="settings"
        :documents-widget="documentsWidgetToDisplay"
        class="mx-auto full-width white" />
    </v-card>
  </v-card>
</template>

<script>
export default {
  props: {
    settings: {
      type: Object,
      default: null,
    },
    documentsWidget: {
      type: Array,
      default: null,
    },
  },
  computed: {
    isColumn() {
      return this.settings?.type === 'COLUMN';
    },
    documentsWidgetToDisplay() {
      return this.documentsWidget?.slice?.(0, 6) || [];
    },
  },
};
</script>
