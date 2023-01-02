<template>
  <div>
    <div v-show="isMobile && !showMobileFilter || !isMobile">
      <button
        v-if="!isFolderView"
        :id="isMobile ? 'addItemMenuMobile' : 'addItemMenu'"
        class="btn btn-primary primary px-2 py-0"
        @click="openDrawer()">
        <v-icon
          id="addBtn"
          dark>
          mdi-plus
        </v-icon>
        {{ !isMobile ? $t('documents.button.addNew') : '' }}
      </button>
      <button
        v-if="isFolderView"
        :id="isMobile ? 'addItemMenu mobile' : 'addItemMenu'"
        class="btn btn-primary primary px-2 py-0"
        :key="postKey"
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
        @click="$root.$emit('mobileFilter')">
        fas fa-arrow-left
      </v-icon>
    </div>
    <documents-add-new-menu-mobile ref="documentAddItemMenu" />
  </div>
</template>
<script>
export default {
  props: {
    selectedView: {
      type: String,
      default: '',
    },
  },
  data: () => ({
    showMobileFilter: false,
    addMenu: false,
    waitTimeUntilCloseMenu: 200,
  }),
  computed: {
    isMobile() {
      return this.$vuetify.breakpoint.name === 'xs' || this.$vuetify.breakpoint.name === 'sm';
    },
    isFolderView() {
      return this.selectedView === 'folder';
    },
  },
  created() {
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
  },
};
</script>
