<template>
  <div class="documents-body">
    <documents-breadcrumb class="pt-2 pe-1 pl-1" />
    <div class="ma-4 d-flex flex-column justify-center text-center text-color">
      <v-img
        :src="emptyDocs"
        class="mx-auto mb-4 overflow-visible"
        overflow="visible"
        :max-height="!isMobile ? 55 : 46"
        :max-width="!isMobile ? 61 : 51"
        contain
        eager>
        <v-icon :size="!isMobile ? 28 : 23" class="closeIcon text-light-color white float-right">fas fa-times-circle</v-icon>
      </v-img>
      <div>
        <p v-if="!isMobile" class="text-light-color">
          <span v-sanitized-html="noContentFolderLabel"></span>
        </p>
        <div>
          <span class="ps-1">{{ addFolderLabel }}</span>
          <v-icon
            size="13"
            class="pe-1 text-sub-title">
            fas fa-folder
          </v-icon>
          <span class="ps-1">{{ addDocumentLabel }}</span>
          <v-icon
            size="13"
            class="pe-1 text-sub-title"
            @click="openDrawer">
            fas fa-file-alt
          </v-icon>
        </div>
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
  computed: {
    noContentFolderLabel() {
      return this.$t && this.$t('documents.label.no-content.folder');
    },
    addFolderLabel() {
      return this.$t && this.$t('documents.label.no-content.addFolder');
    },
    addDocumentLabel() {
      return this.$t && this.$t('documents.label.no-content.addDocument');
    },
  },
  methods: {
    openDrawer() {
      document.dispatchEvent(new CustomEvent('open-attachments-app-drawer'));
    },
  }
};
</script>