<template>
  <div class="text-center pe-1">
    <v-menu offset-y>
      <template v-slot:activator="{ on, attrs }">
        <v-btn
          icon
          small
          v-bind="attrs"
          v-on="on">
          <v-icon
            class="pb-2"
            :size="14">
            mdi-chevron-down
          </v-icon>
        </v-btn>
      </template>
      <v-list>
        <v-list-item
          v-for="(item, index) in accessibilityLabel"
          :key="index">
          <v-list-item-title @click="$emit('visibility-user', item.value)" class="clickable">{{ item.title }}</v-list-item-title>
        </v-list-item>
      </v-list>
    </v-menu>
  </div>
</template>
<script>
export default {
  data: () => ({
    items: [
      { title: 'edit' },
      { title: 'read' },
    ],
  }),
  computed: {
    accessibilityLabel() {
      return [
        {
          title: this.$t('documents.label.accessibility.view'),
          value: 'read',
        },
        {
          title: this.$t('documents.label.accessibility.edit'),
          value: 'edit',
        },
      ];
    },
  }
};
</script>