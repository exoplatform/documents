<template>
  <div class="documents-body">
    <div class="ma-4 d-flex flex-column justify-center text-center text-color">
      <v-img
        :src="emptyDocs"
        class="mx-auto mb-4 overflow-visible"
        overflow="visible"
        max-height="150"
        max-width="250"
        contain
        eager>
        <v-icon
          size="48"
          class="searchIcon text-light-color float-right">
          fas fa-search
        </v-icon>
      </v-img>
      <div class="text-light-color">
        <p>
          {{ $t('documents.label.no-result') }}
        </p>
      </div>
    </div>
  </div>
</template>

<script>
export default {

  data: () => ({
    emptyDocs: '/documents-portlet/images/docs.png',
  }),
};
</script>