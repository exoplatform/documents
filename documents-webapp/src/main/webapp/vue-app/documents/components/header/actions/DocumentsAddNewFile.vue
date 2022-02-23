<template>
  <div>
    <div v-show="isMobile && !showMobileFilter || !isMobile">
      <button v-if="!isFolderView" id="addItemMenu" class="btn btn-primary primary px-2 py-0" @click="openDrawer">
        <v-icon
          id="addBtn"
          dark
          >
          mdi-plus
        </v-icon>
        {{ !isMobile ? $t('documents.button.addNew') : '' }}
      </button>
      <button v-if="isFolderView"
        id="addItemMenu"
        class="btn btn-primary primary px-2 py-0"
        :key="postKey"
        @click.once="openAddItemMenu">
        <v-icon
          id="addBtn"
          dark
          >
          mdi-plus
        </v-icon>
        {{ !isMobile ? $t('documents.button.addNew') : '' }}
      </button> 
      <v-menu v-if="isFolderView"
        v-model="addMenu"
        :attach="'#addItemMenu'"
        transition="scroll-y-transition"
        content-class="add-menu-btn width-full"
        offset-y
        down>
        <v-list-item
          @click="addFolder"
          class="px-2">
          <v-icon
            size="19"
            class="clickable pr-2">
            fa-folder
          </v-icon>
          <span v-if="!isMobile" class="body-2 text-color">{{ $t('documents.button.addNewFolder') }}</span>
        </v-list-item>
        <v-list-item
          @click="openDrawer"
          class="px-2">
          <v-icon
            size="19"
            class="clickable pr-2">
            fa-file-alt
          </v-icon>
          <span v-if="!isMobile" class="body-2 text-color">{{ $t('documents.button.addNewFile') }}</span>
        </v-list-item>
      </v-menu>
    </div>

    <div v-show="isMobile && showMobileFilter || !isMobile">
      <v-icon
        size="20"
        class="inputDocumentsFilter text-sub-title pa-1 my-auto "
        v-show="isMobile && showMobileFilter"
        @click="$root.$emit('resetSearch')">
        fas fa-arrow-left
      </v-icon>
    </div>
  </div>
</template>
<script>
export default {
  data: () => ({
    showMobileFilter: false,
    addMenu: false,
    waitTimeUntilCloseMenu: 200,
    isFolderView: false,
  }),
  computed: {
    isMobile() {
      return this.$vuetify.breakpoint.name === 'xs' || this.$vuetify.breakpoint.name === 'sm';
    },
  },
  created() {
    this.$root.$on('document-change-view', this.changeView);
    const currentUrlSearchParams = window.location.search;
    const queryParams = new URLSearchParams(currentUrlSearchParams);
    if (queryParams.has('folderId')) {
      this.isFolderView = eXo.env.portal.folderViewEnabled;
    } else {
      const pathParts  = eXo.env.server.portalBaseURL.toLowerCase().split(eXo.env.portal.selectedNodeUri.toLowerCase());
      if (pathParts.length>1 && pathParts[1]){
        this.isFolderView = eXo.env.portal.folderViewEnabled;
      }
    }
    $(document).on('mousedown', () => {
      if (this.addMenu) {
        window.setTimeout(() => {
          this.addMenu = false;
        }, this.waitTimeUntilCloseMenu);
      }
    });
    this.$root.$on('show-mobile-filter', data => {
      this.showMobileFilter= data;
    });
    document.addEventListener('entity-attachments-updated', this.refreshFilesList);
  },
  destroyed() {
    document.removeEventListener('entity-attachments-updated', this.refreshFilesList);
  },
  methods: {
    refreshFilesList() {
      this.$root.$emit('documents-refresh-files');
    },
    openAddItemMenu(event) {
      this.addMenu = !this.addMenu;
      if (event) {
        event.preventDefault();
        event.stopPropagation();
      }
    },
    openDrawer() {
      this.$root.$emit('documents-open-drawer');
    },
    addFolder() {
      this.$root.$emit('documents-add-folder');
    },
    changeView(view) {
      this.isFolderView = view==='folder' && eXo.env.portal.folderViewEnabled;
    },
  },
};
</script>
