<template>
  <div>
    <div v-show="isMobile && !showFilter || !isMobile" class="d-flex">
      <documents-multi-select-menu
        v-if="showSelectionsMenu"
        :is-mobile="isMobile"
        :selected-documents="selectedDocuments"
        :selected-view="selectedView" />
      <documents-add-new-file-menu
        v-else
        :is-mobile="isMobile"
        :selected-view="selectedView" />
      <div
        v-if="actionLoading"
        @click="openActionDrawer()"
        class="d-inline">
        <v-tooltip
          bottom>
          <template #activator="{ on, attrs }">
            <v-progress-circular
              v-bind="attrs"
              v-on="on"
              class="ms-2 position-absolute mt-1"
              color="primary"
              :indeterminate="action !== 'import'">
              <span v-if="action === 'import'">{{ Math.ceil(progress) }}</span>
            </v-progress-circular>
          </template>
          {{ actionLoadingMessage }}
        </v-tooltip>
      </div>
      <div v-show="isMobile && showFilter || !isMobile">
        <v-icon
          size="20"
          class="text-sub-title pa-1 my-auto"
          v-show="isMobile && showFilter"
          @click="$root.$emit('mobile-filter')">
          fas fa-arrow-left
        </v-icon>
      </div>
    </div>
  </div>
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
    selectedDocuments: {
      type: Array,
      default: () => []
    }
  },
  data: () => ({
    showFilter: false,
    currentFolder: null,
    showSelectionsMenu: false,
    selectionsLength: 0,
    actionLoading: false,
    actionLoadingMessage: null,
    action: '',
    progress: '',
    importEnabled: true,
  }),
  computed: {
    isFolderView() {
      return this.selectedView === 'folder';
    },
    disableButton(){
      return this.currentFolder && this.currentFolder.accessList && this.currentFolder.accessList.canEdit === false ;
    },
    importTooltipText(){
      if (this.importEnabled) {
        return this.$t('documents.label.btn.upload.zip.tooltip');
      } else {
        return this.$t('documents.label.btn.upload.zip.disabled.tooltip');
      }
    },

    importBtnColorClass(){
      if (this.importEnabled) {
        return 'dark-grey-color';
      } else {
        return 'disabled--text';
      } 
    }
  },
  created() {
    this.$root.$on('show-mobile-filter', data => {
      this.showFilter= data;
    });
    this.$root.$on('set-progress', (progress) => {
      this.progress=progress;
    });
    document.addEventListener('entity-attachments-updated', this.refreshFilesList);
    this.$root.$on('set-current-folder', this.setCurrentFolder);
    this.$root.$on('selection-documents-list-updated', this.handleSelectionListUpdate);
    this.$root.$on('reset-selections', this.handleResetSelections);
    this.$root.$on('set-action-loading', this.setActionLoading);
    this.$root.$on('enable-import', (importEnabled) => {
      this.importEnabled=importEnabled;
    });
  },
  beforeDestroy() {
    this.$root.$off('reset-selections', this.handleResetSelections);
    this.$root.$off('set-action-loading', this.setActionLoading);
  },
  destroyed() {
    document.removeEventListener('entity-attachments-updated', this.refreshFilesList);
  },
  methods: {
    setActionLoading(status, action) {
      this.actionLoading = status;
      this.actionLoadingMessage = this.$t(`document.multiple.${action}.action.message`);
      this.action=action;
      if (!this.actionLoading) {
        this.progress='';
      }
    },
    openMultiSelectionMenuAction() {
      if (this.isMobile) {
        this.$root.$emit('open-file-action-menu-for-multi-selection', this.selectedDocuments);
      }
      this.$root.$emit('prevent-action-context-menu');
    },
    handleResetSelections() {
      this.showSelectionsMenu = false;
      this.selectionsLength = 0;
    },
    handleSelectionListUpdate(selectedList) {
      this.showSelectionsMenu = selectedList.length > 0;
      this.selectionsLength = selectedList.length;
    },
    refreshFilesList() {
      this.$root.$emit('documents-refresh-files');
    },
    openAddItemMenu() {
      if (this.isMobile){
        this.displayAddMenuMobile();
      } else {
        this.addMenu = !this.addMenu;
      }
    },
    openDrawer() {
      this.$root.$emit('documents-open-drawer');
      this.hideAddMenuMobile();
    },
    openImportDrawer() {
      if (this.importEnabled){
        this.$root.$emit('open-upload-zip-drawer');
      }
    },
    addFolder() {
      this.$root.$emit('documents-add-folder');
      this.hideAddMenuMobile();
    },
    displayAddMenuMobile() {
      if (this.isMobile){
        this.$root.$emit('open-add-new-mobile');
      }
    }, 
    hideAddMenuMobile() {
      this.$root.$emit('close-add-new-mobile');
    },
    setCurrentFolder(folder){
      this.currentFolder =folder;
    },
    openActionDrawer(){
      if (this.action === 'import'){
        this.openImportDrawer();
      }
    }
  },
};
</script>
