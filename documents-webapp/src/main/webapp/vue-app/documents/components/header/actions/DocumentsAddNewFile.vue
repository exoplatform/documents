<template>
  <div>
    <div v-show="isMobile && !showFilter || !isMobile">
      <v-menu
        v-if="showSelectionsMenu"
        v-model="selectionsMenu"
        class="add-menu-btn width-full"
        close-on-click
        offset-y>
        <template #activator="{ on, attrs }">
          <v-btn
            class="btn btn-primary primary"
            v-bind="attrs"
            v-on="!isMobile && on"
            @click="openMultiSelectionMenuAction">
            <v-icon
              class="me-1 dark-grey-color"
              size="13"
              dark>
              fas fa-ellipsis-v
            </v-icon>
            <span
              v-if="selectionsLength"
              class="font-weight-regular me-1">
              {{ selectionsLength }}
            </span>
            <span
              v-if="!isMobile"
              class="font-weight-regular">
              {{ $t('document.multiSelection.selected.elements.label') }}
            </span>
          </v-btn>
        </template>
        <div class="documentActionMenu">
          <documents-actions-menu
            :selected-documents="selectedDocuments"
            :file="selectedDocuments[0]"
            :is-multi-selection="true"
            :is-mobile="isMobile" />
        </div>
      </v-menu>
      <button
        v-else
        :id="isMobile ? 'addItemMenu mobile' : 'addItemMenu'"
        class="btn btn-primary primary px-2 py-0"
        :key="postKey"
        :disabled="disableButton"
        @click="openAddItemMenu()">
        <v-icon
          size="13"
          id="addBtn dark-grey-color"
          dark>
          mdi-plus
        </v-icon>
        {{ !isMobile ? $t('documents.button.addNew') : '' }}
      </button> 
      <v-menu
        v-model="addMenu"
        :attach="'#addItemMenu'"
        transition="scroll-y-transition"
        content-class="add-menu-btn width-full"
        offset-y
        down>
        <v-list-item
          v-if="isFolderView"
          @click="addFolder()"
          class="px-2 add-menu-list-item">
          <v-icon
            size="13"
            class="clickable dark-grey-color pr-2">
            fa-folder
          </v-icon>
          <span v-if="!isMobile" class="body-2 text-color menu-text dark-grey-color ps-1">{{ $t('documents.button.addNewFolder') }}</span>
        </v-list-item>
        <v-list-item
          @click="openDrawer()"
          class="px-2 add-menu-list-item">
          <v-icon
            size="13"
            class="clickable dark-grey-color pr-2">
            fa-file-alt
          </v-icon>
          <span v-if="!isMobile" class="body-2 text-color menu-text dark-grey-color ps-1">{{ $t('documents.button.addNewFile') }}</span>
        </v-list-item>

        <v-list-item
          @click="openImportDrawer()"
          class="px-2 add-menu-list-item">
          <v-tooltip bottom>
            <template #activator="{ on }">
              <span v-on="on">
                <v-icon
                  size="13"
                  class="pr-2"
                  :class="importBtnColorClass">
                  fas fa-upload
                </v-icon>
                <span
                  v-if="!isMobile"
                  class="body-2 menu-text dark-grey-color ps-1"
                  :class="importBtnColorClass">{{ $t('documents.label.zip.upload') }}</span>
              </span>
            </template>
            <span>{{ importTooltipText }}</span>
          </v-tooltip>
        </v-list-item>
      </v-menu>
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
      <documents-add-new-menu-mobile
        ref="documentAddItemMenu"
        :is-mobile="isMobile" />
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
    addMenu: false,
    waitTimeUntilCloseMenu: 200,
    currentFolder: null,
    showSelectionsMenu: false,
    selectionsMenu: false,
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
    $(document).on('mousedown', () => {
      if (this.addMenu || this.selectionsMenu) {
        window.setTimeout(() => {
          this.addMenu = false;
          this.selectionsMenu = false;
        }, this.waitTimeUntilCloseMenu);
      }
    });
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
      this.showSelectionsMenu = selectedList.length > 1;
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
        this.$refs.documentAddItemMenu.open();
      }
    }, 
    hideAddMenuMobile() {
      this.$refs.documentAddItemMenu.close();
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
