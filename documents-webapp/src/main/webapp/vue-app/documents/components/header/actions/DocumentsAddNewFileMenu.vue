<!--
* Copyright (C) 2024 eXo Platform SAS
*
*  This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU Affero General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU Affero General Public License
* along with this program.  If not, see <gnu.org/licenses>.
-->

<template>
  <div>
    <v-menu
      v-model="addMenu"
      transition="scroll-y-transition"
      class="add-menu-btn width-full"
      offset-y
      down>
      <template #activator="{ on, attrs }">
        <v-tooltip bottom>
          <template #activator="{ on: tooltipOn, attrs: tooltipAttrs }">
            <span v-bind="tooltipAttrs" v-on="disableButton ? tooltipOn : {}">
              <v-btn
                :id="isMobile ? 'addItemMenu mobile' : 'addItemMenu'"
                class="btn btn-primary"
                :key="postKey"
                :disabled="disableButton"
                :small="isMobile"
                v-bind="attrs"
                v-on="!isMobile && on"
                @click="openAddItemMenu()">
                <v-icon
                  size="18"
                  dark>
                  fa-plus
                </v-icon>
                <span class="ps-1">{{ !isMobile ? $t('documents.button.addNew') : '' }}</span>
              </v-btn>
            </span>
          </template>
          <span>{{ disableButton ? $t('documents.tooltip.selectDrive') : '' }}</span>
        </v-tooltip>
      </template>
      <v-list class="pa-0" dense>
        <v-list-item
          v-if="isFolderView"
          class="text-body menu-text-color"
          @click="addFolder()">
          <v-list-item-icon class="me-1">
            <v-icon
              size="16"
              class="pe-1">
              fa-folder
            </v-icon>
          </v-list-item-icon>
          <v-list-item-title
            class="text-body menu-text-color">
            <span v-if="!isMobile" class="ps-1">{{ $t('documents.button.addNewFolder') }}</span>
          </v-list-item-title>
        </v-list-item>
        <v-list-item
          class="text-body menu-text-color"
          @click="openCreateDocumentDrawer()">
          <v-list-item-icon class="me-1">
            <v-icon size="16" class="pe-1">fa-file-alt</v-icon>
          </v-list-item-icon>
          <v-list-item-title
            class="text-body menu-text-color">
            <span v-if="!isMobile" class="ps-1">{{ $t('documents.button.addNewFile') }}</span>
          </v-list-item-title>
        </v-list-item>
        <v-list-item
          class="text-body menu-text-color"
          @click="uploadFromDevice()">
          <v-list-item-icon class="me-1">
            <v-icon size="16" class="pe-1">fas fa-upload</v-icon>
          </v-list-item-icon>
          <v-list-item-title
            class="text-body menu-text-color">
            <span v-if="!isMobile" class="ps-1">{{ $t('documents.button.upload') }}</span>
          </v-list-item-title>
        </v-list-item>
        <v-list-item
          v-on="on"
          :disabled="!importEnabled"
          class="text-body menu-text-color"
          @click="openImportDrawer()">
          <v-list-item-icon class="me-1">
            <v-icon size="16" class="pe-1">fas fa-upload</v-icon>
          </v-list-item-icon>
          <v-list-item-title
            class="text-body menu-text-color">
            <span v-if="!isMobile" class="ps-1">{{ $t('documents.label.zip.upload') }}</span>
          </v-list-item-title>
        </v-list-item>
      </v-list>
    </v-menu>
    <input
      ref="deviceUploadInput"
      type="file"
      multiple
      class="d-none"
      @change="onDeviceFilesSelected">
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
      return  this.$root.driveView || (this.currentFolder && this.currentFolder.accessList && this.currentFolder.accessList.canEdit === false) ;
    },
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
    this.$root.$on('set-action-loading', this.setActionLoading);
    this.$root.$on('enable-import', (importEnabled) => {
      this.importEnabled=importEnabled;
    });
  },
  beforeDestroy() {
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
    openCreateDocumentDrawer() {
      this.$root.$emit('documents-open-create-document-drawer');
      this.hideAddMenuMobile();
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
