<template>
  <v-scale-transition>
    <select
      id="filterDocumentsSelect"
      v-model="filterDocumentsSelected"
      name="documentsFilter"
      class="selectPrimaryFilter input-block-level ignore-vuetify-classes  pa-0 my-auto"
      @change="changeDocumentsFilter">
      <option
        v-for="item in filterDocuments"
        :key="item.name"
        :value="item.name">
        {{ $t('documents.filter.'+item.name.toLowerCase()) }}
      </option>
    </select>
  </v-scale-transition>
</template>
<script>
export default {
  data: () => ({
    filterDocumentsSelected: 'All',
    filterDocuments: [{name: 'All'},{name: 'Favorites'}],
  }),
  methods: {
    changeDocumentsFilter(){
      this.$root.$emit('documents-filter', this.filterDocumentsSelected.toLowerCase());
    },
  },
};
</script>