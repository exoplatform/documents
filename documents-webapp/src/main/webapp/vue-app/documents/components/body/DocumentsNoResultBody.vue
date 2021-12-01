<template>
  <div class="documents-body">
    <div class="ma-4 d-flex flex-column justify-center text-center text-color">
      <v-img
        :src="emptyDocs"
        class="mx-auto mb-4 overflow-visible"
        overflow="visible"
        :max-height="!isMobile ? 55 : 46"
        :max-width="!isMobile ? 61 : 51"
        contain
        eager>
        <v-icon
          :size="!isMobile ? 48 : 32"
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
  props: {
    isMobile: {
      type: Boolean,
      default: false
    }
  },
  data: () => ({
    emptyDocs: '/documents-portlet/images/docs.png',
  }),
};
</script>