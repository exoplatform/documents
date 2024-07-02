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
        <button
          :id="isMobile ? 'addItemMenu mobile' : 'addItemMenu'"
          class="btn btn-primary"
          :key="postKey"
          :disabled="disableButton"
          v-bind="attrs"
          v-on="!isMobile && on"
          @click="openAddItemMenu()">
          <v-icon
            size="18"
            dark>
            fa-plus
          </v-icon>
          {{ !isMobile ? $t('documents.button.addNew') : '' }}
        </button>
      </template>
      <v-list dense class="pa-0">
        <v-list-item
          v-if="isFolderView"
          @click="addFolder()"
          class="px-2 py-0 add-menu-list-item">
          <v-icon
            size="13"
            class="clickable dark-grey-color pr-2">
            fa-folder
          </v-icon>
          <span v-if="!isMobile">{{ $t('documents.button.addNewFolder') }}</span>
        </v-list-item>
        <v-list-item
          @click="openDrawer()"
          class="px-2 py-0 add-menu-list-item">
          <v-list-item-icon class="me-1">
            <v-icon size="18" class="icon-default-color">fa-file-alt</v-icon>
          </v-list-item-icon>
          <v-list-item-content v-if="!isMobile">
            <v-list-item-title>
              {{ $t('documents.button.addNewFile') }}
            </v-list-item-title>
          </v-list-item-content>
        </v-list-item>
        <v-tooltip bottom>
          <template #activator="{ on }">
            <v-list-item
              v-on="on"
              :disabled="!importEnabled"
              class="px-2 py-0 add-menu-list-item"
              @click="openImportDrawer()">
              <v-list-item-icon class="me-1">
                <v-icon size="18" class="icon-default-color">fas fa-upload</v-icon>
              </v-list-item-icon>
              <v-list-item-content v-if="!isMobile">
                <v-list-item-title>
                  {{ $t('documents.label.zip.upload') }}
                </v-list-item-title>
              </v-list-item-content>
            </v-list-item>
          </template>
          <span>{{ importTooltipText }}</span>
        </v-tooltip>
      </v-list>
    </v-menu>
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
