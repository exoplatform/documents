<template>
  <div v-if="documentsBreadcrumbToDisplay.length" class="documents-breadcrumb-wrapper">
    <div class="documents-tree-items d-flex align-center">
      <v-btn
        icon
        small
        class="me-2"
        :disabled="disabledIconTree"
        @click="openTreeFolderDrawer()">
        <v-icon class="text-sub-title" size="16">
          fas fa-sitemap
        </v-icon>
      </v-btn>
      <div
        id="breadcrumb-list-items"
        data-isfolder="true"
        :data-fileId="documentsBreadcrumbToDisplay[0].id"
        :data-canEdit="canEditFile(documentsBreadcrumbToDisplay[0])? 'true': 'false'"
        class="pa-1 d-flex width-fit-content">
        <div
          v-for="(documents, index) in documentsBreadcrumbToDisplay"
          :key="index"
          :data-fileId="documents.id"
          :class="documentsBreadcrumbToDisplay.length === 1 && 'single-path-element' || ''"
          class="documents-tree-item d-flex text-truncate">
          <v-tooltip max-width="300" bottom>
            <template #activator="{ on, attrs }">
              <v-btn
                height="20px"
                min-width="45px"
                class="pa-0 flex-shrink-1 text-truncate documents-breadcrumb-element"
                :class="documentsBreadcrumbToDisplay[documentsBreadcrumbToDisplay.length-1].id === actualFolderId && 'clickable' || ''"
                text
                v-bind="attrs"
                v-on="on"
                :disabled="disabledIconTree"
                @click="openFolder(documents)">
                <a
                  class="caption text-truncate"
                  :id="move ? 'breadCrumb-link-move' : 'breadCrumb-link'"
                  :class="index < documentsBreadcrumbToDisplay.length-1 && 'path-clickable text-sub-title' || 'text-color not-clickable'">{{ getName(documents.name) }}</a>
                <v-icon
                  v-if="documents.symlink"
                  size="10"
                  class="pe-1 iconStyle">
                  mdi-link-variant
                </v-icon>
              </v-btn>
            </template>
            <span class="caption breadcrumbName">
              {{ getName(documents.name) }}
              <v-icon
                v-if="documents.symlink"
                size="10"
                class="pe-1 iconStyle">
                mdi-link-variant
              </v-icon>
            </span>
          </v-tooltip>
          <v-icon
            v-if="index < documentsBreadcrumbToDisplay.length-1"
            :size="move ? 12 : 14"
            :class="move ? 'px-1' : 'px-3'">
            fa-chevron-right
          </v-icon>
        </div>
      </div>
    </div>
  </div>
</template>
<script>
export default {
  props: {
    documentsBreadcrumb: {
      type: Array,
      default: () => [],
    },
    showIcon: {
      type: Boolean,
      default: true,
    },
    move: {
      type: Boolean,
      default: false,
    },
    disabledIconTree: {
      type: Boolean,
      default: false,
    },
  },
  data: () => ({
    id: Math.random().toString(16),
    actualFolderId: '',
    folderPath: '',
    currentFolderPath: '',
    ownerId: eXo.env.portal.spaceIdentityId || eXo.env.portal.userIdentityId
  }),
  computed: {
    documentsBreadcrumbToDisplay() {
      this.$root.$emit('documentsBreadcrumb',this.documentsBreadcrumb);
      if (!this.documentsBreadcrumb || this.documentsBreadcrumb.length <= 4) {
        return this.documentsBreadcrumb || [];
      } else {
        const length = this.documentsBreadcrumb.length;
        const documentsBreadcrumbToDisplay = [this.documentsBreadcrumb[0], ... this.documentsBreadcrumb.slice(length - 3, length)];
        documentsBreadcrumbToDisplay[1] = Object.assign({}, documentsBreadcrumbToDisplay[1], {
          name: '...',
        });
        return documentsBreadcrumbToDisplay;
      }
    },
  },
  created() {
    this.$root.$on('set-breadcrumb', this.setBreadcrumb);
    this.$root.$on('update-breadcrumb', this.updateBreadcrumb);
    this.$root.$on('open-folder', this.openFolder);
    document.addEventListener('document-open-previous-folder-to-drop', this.handleOpenRootFolder);
    document.addEventListener('move-dropped-documents-on-breadcrumb', this.handleMoveDroppedOnBreadcrumb);
  },
  beforeDestroy() {
    this.$root.$off('set-breadcrumb', this.setBreadcrumb);
    this.$root.$off('update-breadcrumb', this.updateBreadcrumb);
    this.$root.$off('open-folder', this.openFolder);
    document.removeEventListener('document-open-previous-folder-to-drop', this.handleOpenRootFolder);
    document.removeEventListener('move-dropped-documents-on-breadcrumb', this.handleMoveDroppedOnBreadcrumb);
  },
  methods: {
    handleMoveDroppedOnBreadcrumb(event) {
      event.detail.currentOpenedFolder =  this.documentsBreadcrumbToDisplay[0];
      document.dispatchEvent(new CustomEvent('move-dropped-documents', event));
    },
    handleOpenRootFolder() {
      if (this.documentsBreadcrumbToDisplay.length > 1) {
        this.$root.$emit('document-open-folder', this.documentsBreadcrumbToDisplay[this.documentsBreadcrumbToDisplay.length - 2]);
      }
    },
    canEditFile(file) {
      return file?.accessList?.canEdit;
    },
    setBreadcrumb(folder) {
      this.folderPath = '';
      if (folder) {
        this.actualFolderId = folder.id;
        this.getBreadCrumbs(); 
      }
      this.$root.$emit('breadcrumb-updated');
    },
    openFolder(folder) {
      if (folder.name === 'Private'){
        this.$root.$emit('document-open-home');
        this.folderPath='';
        this.actualFolderId ='';
        this.getBreadCrumbs();
      } else if (folder.id !== this.actualFolderId ) {
        this.folderPath='';
        this.actualFolderId=folder.id;
        this.getBreadCrumbs();
        this.$root.$emit('document-open-folder', folder);
      }
      this.$root.$emit('breadcrumb-updated');
    },
    openTreeFolderDrawer(){
      this.$root.$emit('documentsBreadcrumb',this.documentsBreadcrumb);
      this.$root.$emit('openTreeFolderDrawer');
    },
    getName(name){
      if (name==='Private'){
        return this.$t('documents.label.userHomeDocuments');
      } else if (name==='Documents'){
        return this.$t('documents.label.spaceHomeDocuments');
      }
      return name;
    },
    getDocumentDataFromUrl() {
      const currentUrlSearchParams = window.location.search;
      const queryParams = new URLSearchParams(currentUrlSearchParams);
      if (queryParams.has('path')) {
        const parentDriveFolder = eXo.env.portal.spaceName && '/Documents/' || '/Private/';
        const path = queryParams.get('path') || '';
        const nodePath = path.substring(path.indexOf(parentDriveFolder) + parentDriveFolder.length);
        const nodePathParts = nodePath.split('/');
        this.getFolderPath(nodePathParts.join('/'));
      } else if (queryParams.has('folderId')) {
        this.actualFolderId = queryParams.get('folderId');
        this.getBreadCrumbs();
      } else {
        this.getFolderPath();
      }
    },
    getFolderPath(path){
      if (path) {
        this.folderPath = path;
      } else {
        path = window.location.pathname;
        const pathParts  = path.toLowerCase().includes('/drives') || path.toLowerCase().includes('/documents')? path.split( `${eXo.env.portal.selectedNodeUri.toLowerCase()}/`) : [path];
        if (pathParts.length > 1) {
          this.folderPath = pathParts[1];
        } else {
          this.folderPath = '';
        }
        if (!eXo.env.portal.spaceName) {
          if (path.includes('/Private/')){
            const pathParts  = path.split('/Private');
            if (pathParts.length > 1){
              this.folderPath = `Private${pathParts[1]}`;
            }
          }
          if (path.includes('/Public')) {
            const pathParts  = path.split('/Public');
            if (pathParts.length > 1) {
              this.folderPath = `Public${pathParts[1]}`;
            }
          }
        }
      }
      this.actualFolderId = '';
      this.currentFolderPath = '';
      this.getBreadCrumbs();
    },

    getBreadCrumbs() {
      return this.$documentFileService
        .getBreadCrumbs(this.actualFolderId,this.ownerId,this.folderPath)
        .then(breadCrumbs => {
          this.documentsBreadcrumb = breadCrumbs;
          this.actualFolderId = this.documentsBreadcrumb[this.documentsBreadcrumb.length - 1].id;
          this.currentFolderPath = this.documentsBreadcrumb[this.documentsBreadcrumb.length - 1].path;
          this.$root.$emit('set-current-folder', this.documentsBreadcrumb[this.documentsBreadcrumb.length - 1]);
        })
        .finally(() => this.loading = false);
    },
    updateBreadcrumb(folderPath) {
      if (folderPath) {
        this.getFolderPath(folderPath);
      } else {
        this.getDocumentDataFromUrl();
      }
    },
  }
};
</script>
