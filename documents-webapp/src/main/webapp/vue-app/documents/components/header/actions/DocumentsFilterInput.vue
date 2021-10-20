<template>
  <v-text-field
    v-model="keyword"
    :placeholder="$t('documents.label.filterDocuments')"
    prepend-inner-icon="fa-filter"
    class="inputDocumentsFilter pa-0 my-auto" />
</template>
<script>
export default {
  data: () => ({
    keyword: null,
  }),
};
</script>