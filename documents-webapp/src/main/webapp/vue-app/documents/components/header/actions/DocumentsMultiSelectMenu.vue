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
  </div>
</template>
<script>
export default {
  props: {
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
    waitTimeUntilCloseMenu: 200,
    currentFolder: null,
    showSelectionsMenu: false,
    selectionsMenu: false,
    selectionsLength: 0,
    actionLoading: false,
    actionLoadingMessage: null,
    action: '',
  }),

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
      this.showSelectionsMenu = selectedList.length > 0;
      this.selectionsLength = selectedList.length;
    },
    refreshFilesList() {
      this.$root.$emit('documents-refresh-files');
    },
    hideAddMenuMobile() {
      this.$root.$emit('close-add-new-mobile');
    },
    setCurrentFolder(folder){
      this.currentFolder =folder;
    },
  },
};
</script>
