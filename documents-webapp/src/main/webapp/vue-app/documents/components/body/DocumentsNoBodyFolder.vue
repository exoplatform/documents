<template>
  <div class="documents-body">
    <upload-overlay />
    <div class="ma-4 d-flex documents-no-body flex-column justify-center text-center text-color">
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
        <p class="text-light-color">
          <span>{{ noContentFolderLabel }}</span>
        </p>
        <div>
          <span class="ps-1">{{ addFolderLabel }}</span>
          <v-icon
            size="13"
            class="pe-1 text-sub-title"
            @click="addFolder()">
            fas fa-folder
          </v-icon>
          <span class="ps-1">{{ addDocumentLabel }}</span>
          <v-icon
            size="13"
            class="pe-1 text-sub-title"
            @click="openDrawer()">
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
    },
    query: {
      type: String,
      default: null,
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
    addFolder() {
      this.$root.$emit('documents-add-folder');
    },
    openDrawer() {
      this.$root.$emit('documents-open-attachments-drawer');
    },
  }
};
</script>