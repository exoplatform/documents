<template>
  <v-simple-checkbox
    color="primary"
    class="mt-auto"
    :class="show? 'visible': 'invisible'"
    v-model="checked"
    @click="selectDocument" />
</template>

<script>
export default {
  data: () => ({
    checked: false,
    show: false,
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
  },
  created() {
    this.$root.$on('update-selection-documents-list', this.handleUpdateSelectionList);
    this.$root.$on('show-selection-input', this.handleShowSelectionInput);
    this.$root.$on('hide-selection-input', this.handleHideSelectionInput);
    this.$root.$on('select-all-documents', this.handleSelectAllDocuments);
    this.$root.$on('reset-selections', this.handleResetSelections);
    this.initSelected();
  },
  beforeDestroy() {
    this.$root.$off('update-selection-documents-list', this.handleUpdateSelectionList);
    this.$root.$off('show-selection-input', this.handleShowSelectionInput);
    this.$root.$off('hide-selection-input', this.handleHideSelectionInput);
    this.$root.$off('select-all-documents', this.handleSelectAllDocuments);
    this.$root.$off('reset-selections', this.handleResetSelections);
  },
  methods: {
    handleResetSelections() {
      this.checked = false;
      this.show = false;
    },
    selectDocument() {
      this.$root.$emit('update-selection-documents-list', this.checked, this.file);
      if (this.checked) {
        this.$emit('document-selected', this.file);
      } else {
        this.$emit('document-unselected', this.file);
      }
    },
    initSelected() {
      if (this.selectAllChecked) {
        this.checked = true;
        this.selectDocument();
      } else {
        this.checked = this.selectedDocuments.findIndex(file => file.id === this.file.id) !== -1;
      }
      this.show = this.selectedDocuments.length;
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