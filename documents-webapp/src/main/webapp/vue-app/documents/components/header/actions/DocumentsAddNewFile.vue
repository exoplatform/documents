<template>
  <div>
    <div v-show="isMobile && !showMobileFilter || !isMobile">
      <v-menu
        v-if="showSelectionsMenu && documentMultiSelectionActive"
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
              class="me-1"
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
        <documents-actions-menu
          :selected-documents="selectedDocuments"
          :is-multi-selection="true"
          :is-mobile="isMobile" />
      </v-menu>
      <button
        v-else-if="!isFolderView"
        :id="isMobile ? 'addItemMenu mobile' : 'addItemMenu'"
        class="btn btn-primary primary px-2 py-0"
        @click="openDrawer()">
        <v-icon
          size="13"
          id="addBtn"
          dark>
          mdi-plus
        </v-icon>
        {{ !isMobile ? $t('documents.button.addNew') : '' }}
      </button>
      <button
        v-else
        :id="isMobile ? 'addItemMenu mobile' : 'addItemMenu'"
        class="btn btn-primary primary px-2 py-0"
        :key="postKey"
        :disabled="disableButton"
        @click="openAddItemMenu()">
        <v-icon
          size="13"
          id="addBtn"
          dark>
          mdi-plus
        </v-icon>
        {{ !isMobile ? $t('documents.button.addNew') : '' }}
      </button> 
      <v-menu
        v-if="isFolderView"
        v-model="addMenu"
        :attach="'#addItemMenu'"
        transition="scroll-y-transition"
        content-class="add-menu-btn width-full"
        offset-y
        down>
        <v-list-item
          @click="addFolder()"
          class="px-2 add-menu-list-item">
          <v-icon
            size="13"
            class="clickable pr-2">
            fa-folder
          </v-icon>
          <span v-if="!isMobile" class="body-2 text-color menu-text ps-1">{{ $t('documents.button.addNewFolder') }}</span>
        </v-list-item>
        <v-list-item
          @click="openDrawer()"
          class="px-2 add-menu-list-item">
          <v-icon
            size="13"
            class="clickable pr-2">
            fa-file-alt
          </v-icon>
          <span v-if="!isMobile" class="body-2 text-color menu-text ps-1">{{ $t('documents.button.addNewFile') }}</span>
        </v-list-item>
      </v-menu>
    </div>

    <div v-show="isMobile && showMobileFilter || !isMobile">
      <v-icon
        size="20"
        class="inputDocumentsFilter text-sub-title pa-1 my-auto "
        v-show="isMobile && showMobileFilter"
        @click="$root.$emit('mobile-filter')">
        fas fa-arrow-left
      </v-icon>
    </div>
    <documents-add-new-menu-mobile
      ref="documentAddItemMenu"
      :is-mobile="isMobile" />
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
    showMobileFilter: false,
    addMenu: false,
    waitTimeUntilCloseMenu: 200,
    currentFolder: null,
    showSelectionsMenu: false,
    selectionsMenu: false,
    selectionsLength: 0,
  }),
  computed: {
    documentMultiSelectionActive() {
      return eXo?.env?.portal?.documentMultiSelection;
    },
    isFolderView() {
      return this.selectedView === 'folder';
    },
    disableButton(){
      return this.currentFolder && this.currentFolder.accessList && this.currentFolder.accessList.canEdit === false ;
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
      this.showMobileFilter= data;
    });
    document.addEventListener('entity-attachments-updated', this.refreshFilesList);
    this.$root.$on('set-current-folder', this.setCurrentFolder);
    this.$root.$on('selection-documents-list-updated', this.handleSelectionListUpdate);
    this.$root.$on('reset-selections', this.handleResetSelections);
  },
  beforeDestroy() {
    this.$root.$off('reset-selections', this.handleResetSelections);
  },
  destroyed() {
    document.removeEventListener('entity-attachments-updated', this.refreshFilesList);
  },
  methods: {
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
    }
  },
};
</script>
