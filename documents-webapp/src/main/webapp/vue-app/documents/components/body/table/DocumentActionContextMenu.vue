<template>
  <div
    id="action-context-menu"
    ref="actionContextMenu"
    class="elevation-2">
    <documents-actions-menu
      v-if="show"
      :file="file"
      :selected-documents="selectedDocuments"
      :is-multi-selection="isMultiSelection"
      :is-mobile="false" />
  </div>
</template>

<script>
export default {
  data: () => ({
    file: {},
    show: false,
    selectedDocuments: [],
    isMultiSelection: false,
  }),
  created() {
    document.addEventListener('click', this.handleMenuClose);
    document.addEventListener('contextmenu', this.handleMenuClose);
    this.$root.$on('open-action-context-menu', this.openMenu);
    this.$root.$on('prevent-action-context-menu', this.handlePreventMenu);
    this.$root.$on('selection-documents-list-updated', this.handleUpdateSelected);
  },
  beforeDestroy() {
    this.$root.$off('open-action-context-menu', this.openMenu);
    this.$root.$off('selection-documents-list-updated', this.handleUpdateSelected);
    this.$root.$off('prevent-action-context-menu', this.handlePreventMenu);
  },
  computed: {
    isSelected() {
      return this.selectedDocuments.findIndex(file => file.id === this.file.id) !== -1;
    },
  },
  methods: {
    handlePreventMenu() {
      this.show = false;
    },
    handleMenuClose(event, file) {
      if (event.which === 1) {
        this.show = false;
      }
      if (!file) {
        this.show = false;
      }
    },
    handleUpdateSelected() {
      this.show = false;
    },
    openMenu(event, file, selectedDocuments) {
      this.file = file;
      this.selectedDocuments = selectedDocuments;
      this.isMultiSelection = this.selectedDocuments?.length > 1 && this.isSelected ;
      this.$refs.actionContextMenu.style.setProperty('--mouse-x', `${event.clientX  }px`);
      this.$refs.actionContextMenu.style.setProperty('--mouse-y', `${event.clientY  }px`);
      this.$refs.actionContextMenu.style.display = 'block';
      this.show = false;
      setTimeout(() => this.show = true, 100);
    },
  }
};
</script>
