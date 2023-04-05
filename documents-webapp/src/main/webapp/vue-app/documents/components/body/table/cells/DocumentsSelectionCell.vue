<template>
  <v-tooltip
    v-model="canSelectRange"
    :disabled="!canSelectRange"
    open-on-hover
    bottom>
    <template #activator="{ on, attrs }">
      <v-simple-checkbox
        v-model="checked"
        v-bind="attrs"
        v-on="on"
        color="primary"
        class="mt-auto"
        :class="show? 'visible': 'invisible'"
        @click="selectDocument($event)"
        @mouseover="checkRangeSelect"
        @mouseleave="resetRangeSelect" />
    </template>
    {{ $t('document.multiSelection.select.range.message') }}
  </v-tooltip>
</template>

<script>
export default {
  data: () => ({
    checked: false,
    show: false,
    canSelectRange: false,
    rangeSelectTimer: null
  }),
  props: {
    file: {
      type: Object,
      default: null,
    },
    selectedDocuments: {
      type: Array,
      default: () => [],
    },
    selectAllChecked: {
      type: Boolean,
      default: false,
    },
    files: {
      type: Array,
      default: () => []
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
    checkRangeSelect() {
      this.resetRangeSelect();
      this.rangeSelectTimer = setInterval(() => {
        this.canSelectRange = !this.isFileSelected(this.file) && this.selectedDocuments.length > 0;
      },1500);
    },
    resetRangeSelect() {
      clearInterval(this.rangeSelectTimer);
      this.canSelectRange = false;
    },
    isFileSelected(file) {
      return this.selectedDocuments.findIndex(object => object.id === file?.id) !== -1;
    },
    handleSelectTargetDocument(file) {
      if (file.id === this.file.id) {
        this.checked = true;
        this.selectDocument();
      }
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
    handleResetSelections() {
      this.checked = false;
      this.show = false;
    },
    selectRangeDocuments(start, end) {
      this.files.slice(start, end + 1).forEach(file => {
        this.$root.$emit('select-target-document', file);
      });
    },
    selectDocument(event) {
      if (event && event.shiftKey && this.selectedDocuments.length) {
        const currentPosition = this.files.findIndex(file => file.id === this.file.id);
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
        this.$root.$emit('update-selection-documents-list', this.checked, this.file);
        if (this.checked) {
          this.$emit('document-selected', this.file);
        } else {
          this.$emit('document-unselected', this.file);
        }
      }
    },
    initSelected() {
      if (this.selectAllChecked) {
        this.checked = true;
        this.selectDocument();
      } else {
        this.checked = this.selectedDocuments.findIndex(file => file.id === this.file.id) !== -1;
      }
      this.show = this.selectedDocuments.length > 0;
    },
    handleSelectAllDocuments(selected) {
      this.selectAllChecked = true;
      this.checked = selected;
      this.selectDocument();
    },
    handleShowSelectionInput(file, check) {
      if (this.file.id === file.id) {
        this.show = true;
        if (check) {
          this.checked = !this.checked;
          this.selectDocument();
        }
      }
    },
    handleHideSelectionInput(file) {
      if (this.file.id === file.id && !this.checked && !this.selectedDocuments.length) {
        this.show = false;
      }
    },
    handleUpdateSelectionList() {
      this.show = this.selectedDocuments.length > 0;
    }
  }
};
</script>