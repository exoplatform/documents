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
          <template #activator="{ on: tooltipOn }">
            <span v-on="disableButton ? tooltipOn : {}">
              <v-btn
                :id="isMobile ? 'addItemMenu mobile' : 'addItemMenu'"
                ref="addMenuButton"
                class="btn btn-primary"
                :key="postKey"
                :disabled="disableButton"
                :small="isMobile"
                :aria-label="$t('documents.button.addNew')"
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
      <v-list
        ref="addMenuList"
        :aria-label="$t('documents.button.addNew')"
        role="menu"
        class="pa-0"
        dense
        @keydown.native.capture="onAddMenuEntryActivation"
        @keydown.native="onAddMenuKeydown">
        <v-list-item
          v-if="isFolderView"
          :aria-label="$t('documents.button.addNewFolder')"
          role="menuitem"
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
          :aria-label="$t('documents.button.addNewFile')"
          role="menuitem"
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
          :aria-label="$t('documents.button.upload')"
          role="menuitem"
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
          :disabled="!importEnabled"
          :aria-label="$t('documents.label.zip.upload')"
          role="menuitem"
          class="text-body menu-text-color"
          @click="openImportDrawer()">
          <v-list-item-icon class="me-1">
            <v-icon size="16" class="pe-1">far fa-file-archive</v-icon>
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
  watch: {
    addMenu(opened) {
      if (opened && !this.isMobile) {
        // Vuetify's menu keeps the focus on its button and only highlights an entry
        this.$nextTick(() => window.setTimeout(() => this.addMenuEntries()[0]?.focus(), 100));
      }
    },
  },
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
    addMenuEntries() {
      const list = this.$refs.addMenuList?.$el;
      return [...(list?.querySelectorAll('[role="menuitem"]') || [])]
        .filter(entry => entry.getClientRects().length && entry.getAttribute('aria-disabled') !== 'true');
    },
    // An entry opens a drawer or the file picker: the menu closes first and gives the focus back to its button,
    // which the drawer then returns the focus to when it closes. A list item handles Enter itself, so this
    // runs before it, in the capture phase
    onAddMenuEntryActivation(event) {
      if (event.key === 'Enter' && event.target.closest('[role="menuitem"]')) {
        // cancelled, else the keypress that follows would press the button and open the menu again
        event.preventDefault();
        this.addMenu = false;
        this.$refs.addMenuButton?.$el?.focus();
      }
    },
    onAddMenuKeydown(event) {
      const entries = this.addMenuEntries();
      const index = entries.indexOf(document.activeElement);
      let next = null;
      if (event.key === 'ArrowDown') {
        next = entries[(index + 1) % entries.length];
      } else if (event.key === 'ArrowUp') {
        next = entries[(index - 1 + entries.length) % entries.length];
      } else if (event.key === 'Home') {
        next = entries[0];
      } else if (event.key === 'End') {
        next = entries[entries.length - 1];
      } else if (event.key === 'Escape' || event.key === 'Tab') {
        event.preventDefault();
        event.stopPropagation();
        this.addMenu = false;
        this.$nextTick(() => this.$refs.addMenuButton?.$el?.focus());
        return;
      } else {
        return;
      }
      // stopped here, else Vuetify's own handler would also move its highlight
      event.preventDefault();
      event.stopPropagation();
      next?.focus();
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
