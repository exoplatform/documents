<!--
 Copyright (C) 2025 eXo Platform SAS.

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU Affero General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU Affero General Public License for more details.

 You should have received a copy of the GNU Affero General Public License
 along with this program. If not, see <http://www.gnu.org/licenses/>.
-->
<template>
  <v-hover v-slot="{hover}">
    <v-card
      :elevation="hover ? 4 : 0"
      :class="{ 'border-color': !hover }"
      width="215px"
      @click="openFolder">
      <div class="d-flex flex-no-wrap justify-space-between">
        <v-card
          class="d-flex flex-no-wrap"
          :width="hover || selectedDocuments.length ? '70%' : '80%'"
          flat>
          <v-avatar
            class="ma-3"
            size="60"
            tile>
            <img
              v-if="isDrive"
              :src="avatarUrl"
              :alt="name"
              width="24"
              height="24">
            <v-icon
              v-else
              size="60"
              class="primary--text">
              fas fa-folder
            </v-icon>
          </v-avatar>
          <div class="align-self-center text-subtitle-2 text-truncate">{{ name }}</div>
        </v-card>
        <v-card-actions>
          <v-simple-checkbox
            v-show="hover || selectedDocuments.length"
            v-model="checked"
            @click="selectDocument($event)"
            @mouseover="checkRangeSelect"
            @mouseleave="resetRangeSelect" />
          <v-btn
            id="attachment-info"
            :title="$t('attachments.label.details')"
            small
            icon
            class="my-auto mx-0"
            @click="showInfo">
            <v-icon size="20">fa-info-circle</v-icon>
          </v-btn>
        </v-card-actions>
      </div>
    </v-card>
  </v-hover>
</template>

<script>
export default {
  props: {
    folder: {
      type: Object,
      default: null,
    },
    files: {
      type: Array,
      default: () => []
    },
    selectedDocuments: {
      type: Array,
      default: () => [],
    },
    selectAllChecked: {
      type: Boolean,
      default: false,
    },
  },
  data: () => ({
    checked: false,
    show: false,
    canSelectRange: false,
    rangeSelectTimer: null
  }),
  computed: {
    folderId() {
      return this.folder?.id;
    },
    name() {
      return this.folder?.name;
    },
    isDrive() {
      return this.folder?.drive;
    },
    avatarUrl() {
      return this.folder?.avatarUrl;
    }
  },
  created() {
    this.$root.$on('update-selection-documents-list', this.handleUpdateSelectionList);
    this.$root.$on('show-selection-input', this.handleShowSelectionInput);
    this.$root.$on('hide-selection-input', this.handleHideSelectionInput);
    this.$root.$on('select-all-documents', this.handleSelectAllDocuments);
    this.$root.$on('select-target-document', this.handleSelectTargetDocument);
    this.$root.$on('reset-selections', this.handleResetSelections);
    this.initSelected();
  },
  beforeDestroy() {
    this.$root.$off('update-selection-documents-list', this.handleUpdateSelectionList);
    this.$root.$off('show-selection-input', this.handleShowSelectionInput);
    this.$root.$off('hide-selection-input', this.handleHideSelectionInput);
    this.$root.$off('select-all-documents', this.handleSelectAllDocuments);
    this.$root.$off('select-target-document', this.handleSelectTargetDocument);
    this.$root.$off('reset-selections', this.handleResetSelections);
  },
  methods: {
    showInfo(event) {
      if (event) {
        event.preventDefault();
        event.stopPropagation();
      }
      document.dispatchEvent(new CustomEvent('open-document-info-drawer', {detail: this.folderId}));
    },
    openFolder() {
      this.$root.$emit('document-open-folder', this.folder);
    },
    isFileSelected(folder) {
      return this.selectedDocuments.findIndex(object => object.id === folder?.id) !== -1;
    },
    selectRangeDocuments(start, end) {
      this.files.slice(start, end + 1).forEach(file => {
        this.$root.$emit('select-target-document', file);
      });
    },
    selectDocument(event) {
      if (event && event.shiftKey && this.selectedDocuments.length) {
        const currentPosition = this.files.findIndex(file => file.id === this.folderId);
        const startPosition  = this.getFirstSelectedAbovePosition(currentPosition, this.files);
        const endPosition  = this.getFirstSelectedBelowPosition(currentPosition, this.files);
        if (startPosition !== -1 && endPosition !== -1) {
          this.selectRangeDocuments(startPosition, endPosition);
        } else if (startPosition !== -1) {
          this.selectRangeDocuments(startPosition, currentPosition);
        } else if (endPosition !== -1) {
          this.selectRangeDocuments(currentPosition, endPosition);
        }
      } else {
        this.$root.$emit('update-selection-documents-list', this.checked, this.folder);
        if (this.checked) {
          this.$emit('document-selected', this.file);
        } else {
          this.$emit('document-unselected', this.file);
        }
      }
    },
    checkRangeSelect() {
      this.resetRangeSelect();
      this.rangeSelectTimer = setInterval(() => {
        this.canSelectRange = !this.isFileSelected(this.folder) && this.selectedDocuments.length > 0;
      },500);
    },
    resetRangeSelect() {
      clearInterval(this.rangeSelectTimer);
      this.canSelectRange = false;
    },
    getFirstSelectedAbovePosition(currentPosition, files) {
      let position = -1;
      for (let index = currentPosition; index >= 0; index--) {
        if (this.isFileSelected(files[index])) {
          position = index;
          break;
        }
      }
      return position;
    },
    getFirstSelectedBelowPosition(currentPosition, files) {
      let position = -1;
      for (let index = currentPosition;  index < files.length; index ++)  {
        if (this.isFileSelected(files[index])) {
          position = index;
          break;
        }
      }
      return position;
    },
    initSelected() {
      if (this.selectAllChecked) {
        this.checked = true;
        this.selectDocument();
      } else {
        this.checked = this.selectedDocuments.findIndex(folder => folder.id === this.folderId) !== -1;
      }
      this.show = this.selectedDocuments.length > 0;
    },
    handleSelectAllDocuments() {
      this.selectAllChecked = true;
      this.checked = true;
      this.selectDocument();
    },
    handleShowSelectionInput(folder, check) {
      if (this.folderId === folder.id) {
        this.show = true;
        if (check) {
          this.checked = !this.checked;
          this.selectDocument();
        }
      }
    },
    handleHideSelectionInput(folder) {
      if (this.folderId === folder.id && !this.checked && !this.selectedDocuments.length) {
        this.show = false;
      }
    },
    handleUpdateSelectionList() {
      this.show = this.selectedDocuments.length > 0;
    }
  }
};
</script>