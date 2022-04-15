<template>
  <div class="documents-breadcrumb-wrapper">
    <div v-if="documentsBreadcrumb && documentsBreadcrumb.length <= 4" class="documentss-tree-items d-flex">
      <v-icon
        v-if="showIcon"
        class="text-sub-title pe-2 ps-3"
        size="16"
        @click="openTreeFolderDrawer()">
        fas fa-sitemap
      </v-icon>
      <div
        v-for="(documents, index) in documentsBreadcrumb"
        :key="index"
        :class="documentsBreadcrumb && documentsBreadcrumb.length === 1 && 'single-path-element' || ''"
        class="documentss-tree-item d-flex text-truncate"
        :style="`max-width: ${100 / (documentsBreadcrumb.length)}%`">
        <v-tooltip max-width="300" bottom>
          <template #activator="{ on, attrs }">
            <v-btn
              height="20px"
              min-width="45px"
              class="pa-0"
              :class="documentsBreadcrumb[documentsBreadcrumb.length-1].id === actualFolderId && 'clickable' || ''"
              text
              v-bind="attrs"
              v-on="on"
              @click="openFolder(documents)">
              <a
                class="caption text-truncate"
                :id="move ? 'breadCrumb-link-move' : 'breadCrumb-link'"
                :class="index < documentsBreadcrumb.length-1 && 'path-clickable text-sub-title' || 'text-color not-clickable'">{{ getName(documents.name) }}</a>
            </v-btn>
          </template>
          <span class="caption breadcrumbName">{{ getName(documents.name) }}</span>
        </v-tooltip>
        <v-icon
          v-if="index < documentsBreadcrumb.length-1"
          :size="move ? 12 : 14"
          :class="move ? 'px-1' : 'px3'">
          fa-chevron-right
        </v-icon>
      </div>
    </div>
    <div v-else class="documentss-tree-items documentss-long-path d-flex align-center">
      <div class="documentss-tree-item long-path-first-item d-flex text-truncate">
        <v-tooltip max-width="300" bottom>
          <template #activator="{ on, attrs }">
            <a
              :id="move ? 'breadCrumb-link-move' : 'breadCrumb-link'"
              class="caption text-sub-title text-truncate path-clickable"
              :class="documentsBreadcrumb[documentsBreadcrumb.length-1].id === actualFolderId && 'clickable' || ''"
              v-bind="attrs"
              v-on="on"
              @click="openFolder(documentsBreadcrumb[0])">{{ documentsBreadcrumb && documentsBreadcrumb.length && documentsBreadcrumb[0].name }}</a>
          </template>
          <span class="caption">{{ documentsBreadcrumb && documentsBreadcrumb.length && documentsBreadcrumb[0].name }}</span>
        </v-tooltip>
        <v-icon :size="move ? 12 : 14" :class="move ? 'px-1' : 'px3'">fa-chevron-right</v-icon>
      </div>
      <div class="documentss-tree-item long-path-second-item d-flex">
        <v-tooltip bottom>
          <template #activator="{ on, attrs }">
            <v-icon
              v-bind="attrs"
              v-on="on"
              class="text-sub-title"
              size="24">
              mdi-dots-horizontal
            </v-icon>
          </template>
          <p
            v-for="(documents, index) in documentsBreadcrumb"
            :key="index"
            class="mb-0">
            <span v-if="index > 0 && index < documentsBreadcrumb.length-2" class="caption"><v-icon :size="move ? 12 : 14" :class="move ? 'tooltip-chevron px-1' : 'tooltip-chevron px3'">fa-chevron-right</v-icon> {{ getName(documents.name) }}</span>
          </p>
        </v-tooltip>
        <v-icon :class="move ? 'clickable px-1' : 'clickable px3'" :size="move ? 12 : 14">fa-chevron-right</v-icon>
      </div>
      <div class="documentss-tree-item long-path-third-item d-flex text-truncate">
        <v-tooltip max-width="300" bottom>
          <template #activator="{ on, attrs }">
            <a
              :id="move ? 'breadCrumb-link-move' : 'breadCrumb-link'"
              class="caption text-sub-title text-truncate path-clickable"
              :class="documentsBreadcrumb[documentsBreadcrumb.length-1].id === actualFolderId && 'clickable' || ''"
              v-bind="attrs"
              v-on="on"
              @click="openFolder(documentsBreadcrumb[documentsBreadcrumb.length-2])">{{ documentsBreadcrumb[documentsBreadcrumb.length-2].name }}</a>
          </template>
          <span class="caption">{{ documentsBreadcrumb[documentsBreadcrumb.length-2].name }}</span>
        </v-tooltip>
        <v-icon :size="move ? 12 : 14" :class="move ? 'px-1' : 'px3'">fa-chevron-right</v-icon>
      </div>
      <div class="documentss-tree-item d-flex text-truncate">
        <v-tooltip max-width="300" bottom>
          <template #activator="{ on, attrs }">
            <a
              :id="move ? 'breadCrumb-link-move' : 'breadCrumb-link'"
              class="caption text-truncate"
              :class="documentsBreadcrumb[documentsBreadcrumb.length-1].id === actualFolderId && 'text-color' || 'clickable'"
              v-bind="attrs"
              v-on="on"
              @click="openFolder(documentsBreadcrumb[documentsBreadcrumb.length-1])">{{ documentsBreadcrumb[documentsBreadcrumb.length-1].name }}</a>
          </template>
          <span class="caption">{{ documentsBreadcrumb[documentsBreadcrumb.length-1].name }}</span>
        </v-tooltip>
      </div>
    </div>
  </div>
</template>
<script>
export default {
  props: {
    documentsBreadcrumb: {
      type: Array,
      default: () => null
    },
    showIcon: {
      type: Boolean,
      default: true,
    },
    move: {
      type: Boolean,
      default: false,
    },
  },
  data: () => ({
    actualFolderId: '',
    folderPath: '',
    currentFolderPath: '',
    ownerId: eXo.env.portal.spaceIdentityId || eXo.env.portal.userIdentityId
  }),
  computed: {

  },
  created() {
    this.$root.$on('set-breadcrumb', data => {
      this.folderPath='';
      if (data && data.length>0){
        this.documentsBreadcrumb= data;
        this.actualFolderId = this.documentsBreadcrumb[this.documentsBreadcrumb.length-1].id;
        this.currentFolderPath = this.documentsBreadcrumb[this.documentsBreadcrumb.length-1].path;
        this.$root.$emit('set-current-folder-url', this.currentFolderPath);
      }
    });
    this.$root.$on('update-breadcrumb', this.updateBreadcrumb);
    this.$root.$on('open-folder', this.openFolder);
    this.getDocumentDataFromUrl();
    this.getBreadCrumbs();   
  },
  methods: {
    openFolder(folder) {
      if (folder.name==='Private' || folder.name==='Public'){
        this.$root.$emit('document-open-home');
      } else if (folder.id !== this.actualFolderId ) {
        this.folderPath='';
        this.actualFolderId=folder.id;  
        this.getBreadCrumbs();
        this.$root.$emit('document-open-folder', folder);
      }
    },
    openTreeFolderDrawer(){
      this.$root.$emit('openTreeFolderDrawer');
    },
    getName(name){
      if (name==='Private' || name==='Public'){
        return this.$t('documents.label.userHomeDocuments');
      }
      return name;
    },

    getDocumentDataFromUrl() {
      const currentUrlSearchParams = window.location.search;
      const queryParams = new URLSearchParams(currentUrlSearchParams);
      if (queryParams.has('folderId')) {
        this.parentFolderId = queryParams.get('folderId');
        this.selectedView = 'folder';
      } else  if (queryParams.has('path')) {
        let nodePath = queryParams.get('path');
        const lastpart = nodePath.substring(nodePath.lastIndexOf('/')+1,nodePath.length);
        if (lastpart.includes('.')){
          this.fileName = lastpart;
          nodePath = nodePath.substring(0,nodePath.lastIndexOf('/'));
        }
        this.getFolderPath(nodePath);
        this.selectedView = 'folder';
      } else {
        this.getFolderPath();
      }
    },

    getFolderPath(path){
      if (!path){
        path = window.location.pathname;
      }
      this.actualFolderId= '';
      this.folderPath= '';
      this.currentFolderPath= '';
      if (eXo.env.portal.spaceName){
        const pathParts  = path.split( `${eXo.env.portal.selectedNodeUri.toLowerCase()}/`);
        if (pathParts.length>1){
          this.folderPath = pathParts[1];
        }
      } else {
        if (path.includes('/Private')){
          const pathParts  = path.split('/Private');
          if (pathParts.length>1){
            this.folderPath = `Private${pathParts[1]}`;
          }
        }
        if (path.includes('/Public')){
          const pathParts  = path.split('/Public');
          if (pathParts.length>1){
            this.folderPath = `Public${pathParts[1]}`;
          }
        }
      }
    },

    getBreadCrumbs() {
      return this.$documentFileService
        .getBreadCrumbs(this.actualFolderId,this.ownerId,this.folderPath)
        .then(breadCrumbs => {this.documentsBreadcrumb = breadCrumbs;
          this.actualFolderId = this.documentsBreadcrumb[this.documentsBreadcrumb.length-1].id;
          this.currentFolderPath = this.documentsBreadcrumb[this.documentsBreadcrumb.length-1].path;
          this.$root.$emit('set-current-folder-url', this.currentFolderPath);
        })
        .finally(() => this.loading = false);
    },

    updateBreadcrumb() {
      this.getDocumentDataFromUrl();
      this.getBreadCrumbs();  
    },
  }
};
</script>