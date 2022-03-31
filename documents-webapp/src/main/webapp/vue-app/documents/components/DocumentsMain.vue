<template>
  <v-app
    class="documents-application singlePageApplication border-box-sizing"
    :class="isMobile ? 'mobile' : ''"
    role="main"
    flat>
    <div class="pa-3 white">
      <div v-if="searchResult">
        <documents-header
          :files-size="files.length" 
          class="py-2" />
        <documents-no-result-body
          :is-mobile="isMobile" />
      </div>
      <div v-else-if="!filesLoad && !loadingFiles && selectedView == 'folder' ">
        <documents-header
          :files-size="files.length" 
          class="py-2" />
        <documents-no-body-folder
          :is-mobile="isMobile" />
      </div>
      <div v-else-if="!filesLoad && !loadingFiles">
        <documents-header
          :files-size="files.length" 
          class="py-2" />
        <documents-no-body
          :is-mobile="isMobile" />
      </div>
      <div v-else>
        <documents-header
          :files-size="files.length" 
          class="py-2" />
        <documents-body
          :view-extension="selectedViewExtension"
          :files="files"
          :groups-sizes="groupsSizes"
          :page-size="pageSize"
          :offset="offset"
          :limit="limit"
          :has-more="hasMore"
          :sort-field="sortField"
          :ascending="ascending"
          :loading="loading"
          :query="query"
          :primary-filter="primaryFilter" />
        <exo-document-notification-alerts />
      </div>
    </div>
    <v-alert
      v-model="alert"
      :type="alertType"
      dismissible>
      {{ message }}
    </v-alert>
    <folder-treeview-drawer
      ref="folderTreeDrawer" />
  </v-app>
</template>
<script>
export default {
  data: () => ({
    extensionApp: 'Documents',
    extensionType: 'views',
    query: null,
    fileName: null,
    userId: null,
    sortField: 'lastUpdated',
    isFavorits: false,
    ascending: false,
    parentFolderId: null,
    pageSize: 50,
    files: [],
    offset: 0,
    limit: 0,
    loading: false,
    hasMore: false,
    viewExtensions: {},
    currentFolderPath: '',
    groupsSizes: {
      'thisDay': 0,
      'thisWeek': 0,
      'thisMonth': 0,
      'thisYear': 0,
      'beforeThisYear': 0,
    },
    selectedView: 'timeline',
    previewMode: false,
    primaryFilter: 'all',
    alert: false,
    alertType: '',
    message: '',
    ownerId: eXo.env.portal.spaceIdentityId || eXo.env.portal.userIdentityId
  }),
  computed: {
    filesLoad(){
      return this.files && this.files.length;
    },
    selectedViewExtension() {
      if (this.selectedView) {
        return Object.values(this.viewExtensions).find(viewExtension => viewExtension.id === this.selectedView);
      } else if (Object.keys(this.viewExtensions).length) {
        const sortedViewExtensions = Object.values(this.viewExtensions).sort((ext1, ext2) => ext1.rank - ext2.rank);
        return sortedViewExtensions[0];
      }
      return null;
    },
    isMobile() {
      return this.$vuetify.breakpoint.name === 'xs' || this.$vuetify.breakpoint.name === 'sm';
    },
    searchResult(){
      return ((this.query && this.query.length) || this.isFavorits) && !this.files.length;
    },
    loadingFiles(){
      return this.loading;
    }
  },
  created() {
    document.addEventListener(`extension-${this.extensionApp}-${this.extensionType}-updated`, this.refreshViewExtensions);

    window.addEventListener('popstate', e => {this.onBrowserNavChange(e);});

    this.refreshViewExtensions();

    this.$root.$on('documents-refresh-files', this.refreshFilesEvent);
    this.$root.$on('openTreeFolderDrawer', this.folderTreeDrawer);

    this.$root.$on('document-load-more', this.loadMore);
    this.$root.$on('document-change-view', this.changeView);
    this.$root.$on('document-open-folder', this.openFolder);
    this.$root.$on('document-open-home', this.openHome);
    this.$root.$on('documents-add-folder', this.addFolder);
    this.$root.$on('duplicate-document', this.duplicateDocument);
    this.$root.$on('documents-create-folder', this.createFolder);
    this.$root.$on('documents-rename', this.renameDocument);
    this.$root.$on('confirm-document-deletion', this.deleteDocument);
    this.$root.$on('undo-delete-document', this.undoDeleteDocument);
    this.$root.$on('documents-open-drawer', this.openDrawer);
    this.$root.$on('set-current-folder-url', this.setFolderUrl);
    this.$root.$on('cancel-add-folder', this.cancelAddFolder);
    this.$root.$on('document-search', this.search);
    this.$root.$on('documents-sort', this.sort);
    this.$root.$on('documents-filter', filter => {
      this.primaryFilter = filter;
      this.refreshFiles(this.primaryFilter);
    });
    this.$root.$on('show-alert', message => {
      this.displayMessage(message);
    });
    this.getDocumentDataFromUrl();
    this.refreshFiles().then(() => {
      this.watchDocumentPreview();
    }).finally(() => this.$root.$applicationLoaded());
  },
  destroyed() {
    document.removeEventListener(`extension-${this.extensionApp}-${this.extensionType}-updated`, this.refreshViewExtensions);
  },
  methods: {
    folderTreeDrawer(){
      if (this.$refs.folderTreeDrawer){
        this.$refs.folderTreeDrawer.open();
      }
    },
    sort(sortField, ascending) {
      this.sortField = sortField;
      this.ascending = ascending;

      this.refreshFiles();
    },
    search(query) {
      this.query = query;

      this.refreshFiles();
    },
    getFolderPath(path){
      if (!path){
        path = window.location.pathname;
      }
      if (eXo.env.portal.spaceName){
        const pathParts  = path.toLowerCase().split( `${eXo.env.portal.selectedNodeUri.toLowerCase()}/`);
        if (pathParts.length>1){
          this.folderPath = pathParts[1];
          this.selectedView = 'folder';
        }
      } else {
        if (path.includes('/Private')){
          const pathParts  = path.split('/Private');
          if (pathParts.length>1){
            this.folderPath = `Private${pathParts[1]}`;
            this.selectedView = 'folder';
          }
        }
        if (path.includes('/Public')){
          const pathParts  = path.split('/Public');
          if (pathParts.length>1){
            this.folderPath = `Public${pathParts[1]}`;
            this.selectedView = 'folder';
          }
        } 
      }
    },
    duplicateDocument(documents){
      this.parentFolderId = documents.id;
      return this.$documentFileService
        .duplicateDocument(this.parentFolderId,this.ownerId)
        .then( () => {
          this.parentFolderId=null;
          this.getFolderPath();
          this.refreshFiles();
          if (documents.folder){
            this.$root.$emit('show-alert', {type: 'success',message: this.$t('documents.alert.success.label.duplicateFolder')});
          } else {
            this.$root.$emit('show-alert', {type: 'success',message: this.$t('documents.alert.success.label.duplicateDocument')});
          }

        }).catch(e => console.error(e));
    },
    deleteDocument(file){
      const redirectionTime = 500;
      setTimeout(() => {
        const deletedDocument = localStorage.getItem('deletedDocument');
        if (deletedDocument != null) {
          this.refreshFiles(null, true, file.id);
        }
      }, redirectionTime);
    },
    undoDeleteDocument(){
      const deletedDocument = localStorage.getItem('deletedDocument');
      if (deletedDocument != null) {
        this.refreshFiles();
      }
    },
    openFolder(parentFolder) {
      this.folderPath='';
      this.fileName=null;
      this.parentFolderId = parentFolder.id;
      this.refreshFiles();
      this.$root.$emit('set-breadcrumb', parentFolder.breadcrumb);
      let folderPath ='';
      if (eXo.env.portal.spaceName){
        let newParentPath = parentFolder.path;
        newParentPath = newParentPath.replace(`/spaces/${eXo.env.portal.spaceGroup}`, `/spaces/${eXo.env.portal.spaceGroup}/${eXo.env.portal.spaceName}`);
        let pathParts = newParentPath.toLowerCase().split(eXo.env.portal.selectedNodeUri.toLowerCase());
        if (pathParts.length>1){
          folderPath = pathParts[1];
        }
        pathParts = window.location.pathname.toLowerCase().split(eXo.env.portal.selectedNodeUri.toLowerCase());
        window.history.pushState(parentFolder.name, parentFolder.title, `${pathParts[0]}${eXo.env.portal.selectedNodeUri}${folderPath}?view=folder`);
      } else {
        const userName = eXo.env.portal.userName;
        const userPrivatePathPrefix = `${userName}/Private`;
        const userPublicPathPrefix = `${userName}/Public`;
        if (parentFolder.path.includes(userPrivatePathPrefix)){
          const pathParts = parentFolder.path.split(userPrivatePathPrefix);
          if (pathParts.length>1){
            folderPath = pathParts[1];
          }
          
          window.history.pushState(parentFolder.name, parentFolder.title, `${window.location.pathname.split('/Private')[0]}/Private${folderPath}?view=folder`);
        }
        if (parentFolder.path.includes(userPublicPathPrefix)){
          const pathParts = parentFolder.path.split(userPublicPathPrefix);
          if (pathParts.length>1){
            folderPath = pathParts[1];
          }
          window.history.pushState(parentFolder.name, parentFolder.title, `${window.location.pathname.split('/Public')[0]}/Public${folderPath}?view=folder`);
        }
      
      }
    },
    loadMore() {
      this.limit += this.pageSize;
      this.refreshFiles(this.primaryFilter);
    },
    changeView(view) {
      this.selectedView=view;
      if (view.toLowerCase() !== 'folder'){
        this.parentFolderId=null;
      }
      this.refreshFiles(this.primaryFilter);
    },
    openHome() {
      this.parentFolderId=null;  
      this.folderPath='';
      this.fileName=null;
      this.refreshFiles(this.primaryFilter);
      this.$root.$emit('set-breadcrumb', null);
      if (window.location.pathname.includes('/Private')){
        window.history.pushState('Documents', 'Personal Documents', `${window.location.pathname.split('/Private')[0]}?view=${this.selectedView}`);
      } else if (window.location.pathname.includes('/Public')){
        window.history.pushState('Documents', 'Personal Documents', `${window.location.pathname.split('/Public')[0]}?view=${this.selectedView}`);
      } else {
        window.history.pushState('Documents', 'Personal Documents', `${window.location.pathname}?view=${this.selectedView}`);
      }
    },
    refreshFilesEvent() {
      this.refreshFiles();
    },
    refreshFiles(filterPrimary, deleted, documentId) {
      if (!this.selectedViewExtension) {
        return Promise.resolve(null);
      }
      const filter = {
        ownerId: eXo.env.portal.spaceIdentityId || eXo.env.portal.userIdentityId,
        listingType: this.selectedViewExtension.listingType,
      };
      if (this.parentFolderId) {
        filter.parentFolderId = this.parentFolderId;
      }
      if (this.folderPath) {
        filter.folderPath = this.folderPath;
      }
      if (this.query) {
        filter.query = this.query;
      }
      if (this.sortField) {
        filter.sortField = this.sortField;
      }
      if (this.userId) {
        filter.userId = this.userId;
      }
      if (this.ascending) {
        filter.ascending = this.sortField === 'favorite' ? false : true;
      }
      if (filterPrimary && filterPrimary==='favorites') {
        this.isFavorits = true;
      }
      if (filterPrimary && filterPrimary==='all') {
        this.isFavorits  =  false;
      }
      filter.favorites = this.isFavorits;
      const expand = this.selectedViewExtension.filePropertiesExpand || 'modifier,creator,owner,metadatas';
      this.limit = this.limit || this.pageSize;
      this.loading = true;
      return this.$documentFileService
        .getDocumentItems(filter, this.offset, this.limit + 1, expand)
        .then(files => {
          this.files = this.sortField === 'favorite' ? files && files.slice(this.offset, this.limit).sort((file1, file2) => {
            if (file1.favorite === false && file2.favorite === true) {
              return this.ascending ? 1 : -1;
            }
            if (file1.favorite === true && file2.favorite === false) {
              return this.ascending ? -1 : 1;
            }
            return 0;
          }) || [] : files && files.slice(this.offset, this.limit) || [];
          this.files = deleted ? this.files.filter(doc => doc.id !== documentId) : this.files;
          this.hasMore = files && files.length > this.limit;
          if (this.fileName){
            const result = files.filter(file => file.name===this.fileName);
            if (result.length>0){
              this.showPreview(result[0].id);
            }
          }

        })
        .finally(() => this.loading = false);
    },
    refreshViewExtensions() {
      const extensions = extensionRegistry.loadExtensions(this.extensionApp, this.extensionType);
      let changed = false;
      extensions.forEach(extension => {
        if (extension.id && (!this.viewExtensions[extension.id] || this.viewExtensions[extension.id] !== extension)) {
          this.viewExtensions[extension.id] = extension;
          changed = true;
        }
      });
      // force update of attribute to re-render switch new extension id
      if (changed) {
        this.viewExtensions = Object.assign({}, this.viewExtensions);
      }
    },
    watchDocumentPreview() {
      const self = this;
      const bodyElement = document.body;

      const config = {childList: true, subtree: true};

      const callback = function () {
        const documentPreviewContainer = document.getElementById('documentPreviewContainer');
        if (!documentPreviewContainer && self.previewMode) {
          // Quit preview mode
          self.previewMode = false;
          window.history.pushState('', '', eXo.env.server.portalBaseURL);
        } else if (documentPreviewContainer && !self.previewMode) {
          // Enter preview mode
          self.previewMode = true;
        }
      };

      // Create an observer instance linked to the callback function
      const observer = new MutationObserver(callback);

      // Start observing the target node for configured mutations
      observer.observe(bodyElement, config);
    },
    addFolder(){
      const newFolder={
        'id': -1,
        'name': 'new folder',
        'folder': true
      };
      this.files.unshift(newFolder);
    },
    cancelAddFolder(folder){
      this.files.splice(this.files.indexOf(folder), 1);
    },
    createFolder(name){
      const ownerId = eXo.env.portal.spaceIdentityId || eXo.env.portal.userIdentityId;
      this.$documentFileService.createFolder(ownerId,this.parentFolderId,this.folderPath,name)
        .then( () => {
          this.refreshFiles();
        })
        .catch(e => console.error(e))
        .finally(() => this.loading = false);
    },
    renameDocument(file,name){
      const ownerId = eXo.env.portal.spaceIdentityId || eXo.env.portal.userIdentityId;
      this.$documentFileService.renameDocument(ownerId,file.id,name)
        .then( () => {
          this.refreshFiles();
        })
        .catch(e => console.error(e))
        .finally(() => this.loading = false);
    },
    openDrawer() {

      let attachmentAppConfiguration = {
        'sourceApp': 'NEW.APP'
      };
      if (eXo.env.portal.spaceName){
        const pathparts = window.location.pathname.toLowerCase().split(`${eXo.env.portal.selectedNodeUri.toLowerCase()}/`);
        if (pathparts.length>1){
          attachmentAppConfiguration= {
            'sourceApp': 'NEW.APP',
            'defaultFolder': decodeURI(pathparts[1]),
            'defaultDrive': {
              isSelected: true,
              name: `.spaces.${eXo.env.portal.spaceGroup}`,
              title: eXo.env.portal.spaceDisplayName,
            }
          };
        }
      } else {
        let pathparts = window.location.pathname.split(`${eXo.env.portal.selectedNodeUri}/`);
        if (pathparts.length>1 && pathparts[1].startsWith('Private/')){
          pathparts = pathparts[1].split('Private/');
        }
        if (pathparts.length>1){
          attachmentAppConfiguration= {
            'sourceApp': 'NEW.APP',
            'defaultFolder': decodeURI(pathparts[1]),
            'defaultDrive': {
              isSelected: true,
              name: 'Personal Documents',
              title: 'Personal Documents'
            }
          };
        }
      }
      document.dispatchEvent(new CustomEvent('open-attachments-app-drawer', {detail: attachmentAppConfiguration}));
    },
    setFolderUrl(url) {
      this.currentFolderPath=url;
    },
    getDocumentDataFromUrl() {
      const currentUrlSearchParams = window.location.search;
      const queryParams = new URLSearchParams(currentUrlSearchParams);
      if (queryParams.has('documentPreviewId')) {
        this.loading = true;
        this.previewMode = true;
        const documentPreviewId = queryParams.get('documentPreviewId');
        this.showPreview(documentPreviewId);
      } else  if (queryParams.has('folderId')) {
        this.parentFolderId = queryParams.get('folderId');
        this.selectedView = 'folder';
      } else  if (queryParams.has('path')) {
        this.selectedView = 'folder';
        let nodePath = queryParams.get('path');
        const lastpart = nodePath.substring(nodePath.lastIndexOf('/')+1,nodePath.length);
        if (lastpart.includes('.')){
          this.fileName = lastpart;
          nodePath = nodePath.substring(0,nodePath.lastIndexOf('/'));
        }
        this.getFolderPath(nodePath);
      } else {
        this.parentFolderId=null;
        this.fileName=null;
        this.folderPath='';
        this.selectedView = 'timeline';
        this.getFolderPath();
      }
      if (queryParams.has('view')) {
        const view = queryParams.get('view');
        if (view.toLowerCase() === 'folder'){
          this.selectedView='folder';
        } else {
          this.parentFolderId=null;
          this.selectedView='timeline';
          const pathParts = window.location.pathname.toLowerCase().split(eXo.env.portal.selectedNodeUri.toLowerCase());
          window.history.pushState('documents', 'Documents', `${pathParts[0]}${eXo.env.portal.selectedNodeUri}?view=timeline`);
        }
      }
      if (!eXo.env.portal.spaceName && queryParams.has('userId')) {
        const userId = queryParams.get('userId');
        this.userId=userId;
      } else {
        this.userId=null;
      }
    },
    onBrowserNavChange() {
      this.getDocumentDataFromUrl();
      this.refreshFiles();
      this.$root.$emit('update-breadcrumb');
    },
    displayMessage(message) {
      this.message = message.message;
      this.alertType = message.type;
      this.alert = true;
      window.setTimeout(() => this.alert = false, 5000);
    },
    showPreview(documentPreviewId) {
      this.$attachmentService.getAttachmentById(documentPreviewId)
        .then(attachment => {
          documentPreview.init({
            doc: {
              id: documentPreviewId,
              repository: 'repository',
              workspace: 'collaboration',
              path: attachment.path,
              title: attachment.title,
              openUrl: attachment.openUrl,
              breadCrumb: attachment.previewBreadcrumb,
              size: attachment.size,
              downloadUrl: attachment.downloadUrl,
            },
            author: attachment.updater,
            version: {
              number: attachment.version
            },
            showComments: false,
            showOpenInFolderButton: false,
          });
        })
        .catch(e => console.error(e))
        .finally(() => this.loading = false);
    },
  },
};
</script>