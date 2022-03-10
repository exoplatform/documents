<template>
  <div class="documents-breadcrumb-wrapper">
    <div v-if="documentsBreadcrumb && documentsBreadcrumb.length <= 4" class="documentss-tree-items d-flex">
      <v-icon class="text-sub-title pe-2" size="16">fas fa-sitemap</v-icon>
      <div
        v-for="(documents, index) in documentsBreadcrumb"
        :key="index"
        :class="documentsBreadcrumb && documentsBreadcrumb.length === 1 && 'single-path-element' || ''"
        class="documentss-tree-item d-flex text-truncate"
        :style="`max-width: ${100 / (documentsBreadcrumb.length)}%`">
        <v-tooltip max-width="300" bottom>
          <template v-slot:activator="{ on, attrs }">
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
                class="caption text-truncate breadCrumb-link"
                :class="index < documentsBreadcrumb.length-1 && 'path-clickable text-sub-title' || 'text-color not-clickable'">{{ getName(documents.name)}}</a>
            </v-btn>
          </template>
          <span class="caption breadcrumbName">{{ getName(documents.name) }}</span>
        </v-tooltip>
        <v-icon v-if="index < documentsBreadcrumb.length-1" size="18">mdi-chevron-right</v-icon>
      </div>
    </div>
    <div v-else class="documentss-tree-items documentss-long-path d-flex align-center">
      <div class="documentss-tree-item long-path-first-item d-flex text-truncate">
        <v-tooltip max-width="300" bottom>
          <template v-slot:activator="{ on, attrs }">
            <a
              class="caption text-sub-title text-truncate path-clickable breadCrumb-link"
              :class="documentsBreadcrumb[documentsBreadcrumb.length-1].id === actualFolderId && 'clickable' || ''"
              v-bind="attrs"
              v-on="on"
              @click="openFolder(documentsBreadcrumb[0])">{{ documentsBreadcrumb && documentsBreadcrumb.length && documentsBreadcrumb[0].name }}</a>
          </template>
          <span class="caption">{{ documentsBreadcrumb && documentsBreadcrumb.length && documentsBreadcrumb[0].name }}</span>
        </v-tooltip>
        <v-icon size="18">mdi-chevron-right</v-icon>
      </div>
      <div class="documentss-tree-item long-path-second-item d-flex">
        <v-tooltip bottom>
          <template v-slot:activator="{ on, attrs }">
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
            <span v-if="index > 0 && index < documentsBreadcrumb.length-2" class="caption"><v-icon size="18" class="tooltip-chevron">mdi-chevron-right</v-icon> {{ getName(documents.name) }}</span>
          </p>
        </v-tooltip>
        <v-icon class="clickable" size="18">mdi-chevron-right</v-icon>
      </div>
      <div class="documentss-tree-item long-path-third-item d-flex text-truncate">
        <v-tooltip max-width="300" bottom>
          <template v-slot:activator="{ on, attrs }">
            <a
              class="caption text-sub-title text-truncate path-clickable breadCrumb-link"
              :class="documentsBreadcrumb[documentsBreadcrumb.length-1].id === actualFolderId && 'clickable' || ''"
              v-bind="attrs"
              v-on="on"
              @click="openFolder(documentsBreadcrumb[documentsBreadcrumb.length-2])">{{ documentsBreadcrumb[documentsBreadcrumb.length-2].name }}</a>
          </template>
          <span class="caption">{{ documentsBreadcrumb[documentsBreadcrumb.length-2].name }}</span>
        </v-tooltip>
        <v-icon size="18">mdi-chevron-right</v-icon>
      </div>
      <div class="documentss-tree-item d-flex text-truncate">
        <v-tooltip max-width="300" bottom>
          <template v-slot:activator="{ on, attrs }">
            <a
              class="caption text-truncate breadCrumb-link"
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

  data: () => ({
    documentsBreadcrumb: [],
    actualFolderId: '',
    folderPath: '',
    currentFolderPath: '',
    ownerId: eXo.env.portal.spaceIdentityId || eXo.env.portal.userIdentityId
  }),
  created() {
    this.$root.$on('set-breadcrumb', data => {
      this.folderPath='';
      if (data && data.length>0){
        this.documentsBreadcrumb= data;
        this.actualFolderId = this.documentsBreadcrumb[this.documentsBreadcrumb.length-1].id;
        this.currentFolderPath = this.documentsBreadcrumb[this.documentsBreadcrumb.length-1].path;
        this.$root.$emit('set-current-folder-url', this.currentFolderPath);
      } else {
        this.actualFolderId= '';
        this.folderPath= '';
        this.currentFolderPath= '';
        this.getBreadCrumbs();
      }
    });
    this.$root.$on('update-breadcrumb', this.updateBreadcrumb);
    this.getFolderPath();
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

    getName(name){
      if (name==='Private' || name==='Public'){
        return this.$t('documents.label.userHomeDocuments');
      }
      return name;
    },

    getFolderPath(){
      this.actualFolderId= '';
      this.folderPath= '';
      this.currentFolderPath= '';
      if (eXo.env.portal.spaceName){
        const pathParts  = window.location.pathname.toLowerCase().split( `${eXo.env.portal.selectedNodeUri.toLowerCase()}/`);
        if (pathParts.length>1){
          this.folderPath = pathParts[1];
        }
      } else {
        if (window.location.pathname.includes('/Private')){
          const pathParts  = window.location.pathname.split('/Private');
          if (pathParts.length>1){
            this.folderPath = `Private${pathParts[1]}`;
          }
        }
        if (window.location.pathname.includes('/Public')){
          const pathParts  = window.location.pathname.split('/Public');
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
      this.getFolderPath();
      this.getBreadCrumbs();  
    },
  }
};
</script>