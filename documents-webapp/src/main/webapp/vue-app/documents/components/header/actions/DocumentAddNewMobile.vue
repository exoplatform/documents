<template>
  <exo-drawer 
    ref="documentAddNewMobileMenu"
    :bottom="true">
    <template slot="content">
      <v-list dense>
        <v-list-item
          v-if="isFolderView"
          @click="addFolder()"
          class="px-2">
          <v-icon
            size="19"
            class="clickable pr-2">
            fa-folder
          </v-icon>
          <span class="body-2 text-color">{{ $t('documents.button.addNewFolder') }}</span>
        </v-list-item>
        <v-list-item
          @click="openCreateDocumentDrawer()"
          class="px-2">
          <v-icon
            size="19"
            class="clickable pr-2">
            fa-file-alt
          </v-icon>
          <span class="body-2 text-color">{{ $t('documents.button.addNewFile') }}</span>
        </v-list-item>
        <v-list-item
          @click="uploadFromDevice()"
          class="px-2">
          <v-icon
            size="19"
            class="clickable pr-2">
            fas fa-upload
          </v-icon>
          <span class="body-2 text-color">{{ $t('documents.button.upload') }}</span>
        </v-list-item>
        <v-list-item
          @click="openImportDrawer()"
          class="px-2">
          <v-icon
            size="19"
            :class="importBtnColorClass"
            class="clickable pr-2">
            fas fa-upload
          </v-icon>
          <span 
            :class="importBtnColorClass" 
            class="body-2">
            {{ $t('documents.label.zip.upload') }}
          </span>
        </v-list-item>
      </v-list>
      <input
        ref="deviceUploadInput"
        type="file"
        multiple
        class="d-none"
        @change="onDeviceFilesSelected">
      <exo-drawer />
    </template>
  </exo-drawer>
</template>
<script>

export default {
  props: {
    selectedView: {
      type: String,
      default: '',
    },
    isMobile: {
      type: Boolean,
      default: false
    },
  },

  data: () => ({
    importEnabled: true,
  }),

  created() {
    this.$root.$on('enable-import', (importEnabled) => {
      this.importEnabled = importEnabled;
    });
  },

  computed: {
    isFolderView() {
      return this.selectedView === 'folder';
    },

    importBtnColorClass(){
      if (this.importEnabled) {
        return '';
      } else {
        return 'disabled--text';
      } 
    }
  },

  methods: {
    open() {
      this.$refs.documentAddNewMobileMenu.open();
    },
    close() {
      this.$refs.documentAddNewMobileMenu.close();
    },
    openCreateDocumentDrawer() {
      this.$root.$emit('documents-open-create-document-drawer');
      this.close();
    },
    uploadFromDevice() {
      // Open the OS file picker; the chosen files are uploaded into the current
      // folder through the Drive's existing upload path (openDrawer(files)).
      this.$refs.deviceUploadInput.value = '';
      this.$refs.deviceUploadInput.click();
    },
    onDeviceFilesSelected(event) {
      const files = event?.target?.files;
      if (files && files.length) {
        this.$root.$emit('documents-open-drawer', Array.from(files));
      }
      this.close();
    },
    addFolder() {
      this.$root.$emit('documents-add-folder');
      this.close();
    },
    openImportDrawer() {
      if (this.importEnabled){
        this.$root.$emit('open-upload-zip-drawer');
        this.close();
      }
    },
  }
};
</script>
