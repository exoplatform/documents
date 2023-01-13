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
    <div
      class="extendFilterButton"
      v-show="showExtend && extendedSearchEnabled"
      @click="extendFilter()">
      <v-icon
        size="24"
        class="extendIcon">
        mdi-file-search
      </v-icon>
      <span> {{ $t('documents.message.extendedSearch') }}</span>
    </div>
  </div>
</template>

<script>
export default {
  props: {
    isMobile: {
      type: Boolean,
      default: false
    },
    showExtendFilter: {
      type: Boolean,
      default: false
    },
    query: {
      type: String,
      default: null,
    }
  },
  params() {
    return {
      query: this.query,
      isMobile: this.isMobile,
    };
  },
  data: () => ({
    emptyDocs: '/documents-portlet/images/docs.png',
  }),
  computed: {
    extendedSearchEnabled() {
      return eXo.env.portal.extendedSearchEnabled && !this.isMobile;
    },
    showExtend(){
      return this.showExtendFilter;
    }
  },
  methods: {
    extendFilter(){
      this.$root.$emit('document-extended-search');
    },
  }
};
</script>