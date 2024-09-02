<template>
  <div :class="!isColumn ? 'my-auto' : ''">
    <component
      v-if="documentsWidget && documentsWidget.length"
      :is="isColumn ? 'v-list' : 'card-carousel'"
      :class="isColumn ? 'pa-0' : 'mt-n2 mb-n4'"
      class="full-width"
      v-bind="isColumn ? { dense: !largeIcon } : {}">
      <component
        v-for="widget in documentsWidget"
        :key="widget.id"
        :is="componentName"
        :documents-widget="widget"
        :type="type"
        :large-icon="largeIcon"
        :show-name="showName"
        :show-description="showDescription" />
    </component>
  </div>
</template>

<script>
export default {
  props: {
    settings: {
      type: Object,
      default: () => ({}),  // Default to an empty object if no settings are provided
    },
    documentsWidget: {
      type: Array,
      default: () => [],  // Default to an empty array if no documentsWidget is provided
    },
  },
  computed: {
    type() {
      return this.settings.type || 'CARD';
    },
    showName() {
      return this.settings.showName || false;
    },
    showDescription() {
      return this.settings.showDescription || false;
    },
    largeIcon() {
      return this.settings.largeIcon || false;
    },
    header() {
      return this.settings.header?.[this.$root.language] || this.settings.header?.[this.$root.defaultLanguage];
    },
    seeMoreUrl() {
      return this.$documentsWidgetService.toLinkUrl(this.settings.seeMore);
    },
    isColumn() {
      return this.type === 'COLUMN';
    },
    componentName() {
      return this.isColumn ? 'documents-widget-column' : 'documents-widget-card';
    },
  },
};
</script>
