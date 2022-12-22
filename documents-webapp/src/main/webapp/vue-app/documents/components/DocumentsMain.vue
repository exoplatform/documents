<template>
  <v-app
    class="documents-application singlePageApplication border-box-sizing"
    :class="isMobile ? 'mobile' : ''"
    role="main"
    flat>
    <div
      class="pa-3 white"
      @dragover.prevent
      @drop.prevent
      @dragstart.prevent>
      <div v-if="searchResult">
        <documents-header
          :files-size="files.length" 
          :selected-view="selectedView"
          :can-add="canAdd"
          class="py-2" />
        <documents-no-result-body
          :is-mobile="isMobile" />
      </div>
      <div
        v-else-if="!filesLoad && !loading && selectedView == 'folder' "
        @drop="dragFile"
        @dragover="startDrag">
        <documents-header
          :files-size="files.length" 
          :selected-view="selectedView"
          :can-add="canAdd"
          class="py-2" />
        <documents-no-body-folder
          :is-mobile="isMobile" />
      </div>
      <div v-else-if="!filesLoad && !loading">
        <documents-header
          :files-size="files.length" 
          :selected-view="selectedView"
          :can-add="canAdd"
          class="py-2" />
        <documents-no-body
          :is-mobile="isMobile" />
      </div>
      <div
        v-else
        @drop="dragFile"
        @dragover="startDrag">
        <documents-header
          :files-size="files.length" 
          :selected-view="selectedView"
          :can-add="canAdd"
          class="py-2" />
        <documents-body
          v-if="optionsLoaded"
          :view-extension="selectedViewExtension"
          :files="files"
          :groups-sizes="groupsSizes"
          :page-size="pageSize"
          :offset="offset"
          :limit="limit"
          :has-more="hasMore"
          :sort-field="sortField"
          :ascending="ascending"
          :initialized="initialized"
          :loading="loading"
          :query="query"
          :extended-search="extendedSearch"
          :primary-filter="primaryFilter" />
        <exo-document-notification-alerts />
      </div>
    </div>
    <documents-visibility-drawer />
    <document-tree-selector-drawer />
    <documents-info-drawer
      :selected-view="selectedView" />
    <v-alert
      v-model="alert"
      :icon="false"
      :colored-border="isMobile"
      :border="isMobile? 'top' : ''"
      :color="alertType"
      :type="!isMobile? alertType: ''"
      :class="isMobile? 'documents-alert-mobile': ''"
      :dismissible="!isMobile">
      {{ message }}
    </v-alert>
    <folder-treeview-drawer
      ref="folderTreeDrawer" />
    <documents-app-reminder />
    <documents-actions-menu-mobile />
    <documents-filter-menu-mobile :primaryFilter="this.primaryFilter"/>
    <version-history-drawer
      :can-manage="canManageVersions"
      :enable-edit-description="true"
      :versions="versions"
      :is-loading="isLoadingVersions"
      :show-load-more="showLoadMoreVersions"
      @drawer-closed="versionsDrawerClosed"
      @open-version="showVersionPreview"
      @restore-version="restoreVersion"
      @version-update-description="updateVersionSummary"
      @load-more="loadMoreVersions"
      ref="documentVersionHistory" />
  </v-app>
</template>
<script>

export default {
  data: () => ({
    canAdd: false,
    versions: [],
    versionableFile: {},
    allVersions: [],
    versionsPageSize: Math.round((window.innerHeight-79)/95),
    isLoadingVersions: false,
    canManageVersions: false,
    extensionApp: 'Documents',
    extensionType: 'views',
    query: null,
    extendedSearch: false,
    fileName: null,
    userId: null,
    sortField: 'lastUpdated',
    isFavorites: false,
    ascending: false,
    parentFolderId: null,
    pageSize: 50,
    files: [],
    offset: 0,
    limit: 0,
    optionsLoaded: false,
    initialized: false,
    loading: false,
    hasMore: false,
    canSendSearchStat: true,
    viewExtensions: {},
    currentFolderPath: '',
    currentFolder: null,
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
    showLoadMoreVersions() {
      return this.versions.length < this.allVersions.length;
    },
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
      return ((this.query && this.query.length) || this.isFavorites) && !this.files.length;
    },
    prefixClone(){
      return this.$t('documents.label.prefix.clone');
    },
  },
  created() {
    // Ensure that localStorage doesn't contain a deleted document
    window.setTimeout(() => {
      localStorage.removeItem('deletedDocument');
    }, 10000);

    document.addEventListener(`extension-${this.extensionApp}-${this.extensionType}-updated`, this.refreshViewExtensions);

    window.addEventListener('popstate', e => {this.onBrowserNavChange(e);});

    this.refreshViewExtensions();
    this.canAddDocument();

    this.$root.$on('documents-refresh-files', this.refreshFiles);
    this.$root.$on('openTreeFolderDrawer', this.folderTreeDrawer);

    this.$root.$on('document-load-more', this.loadMore);
    this.$root.$on('document-change-view', this.changeView);
    this.$root.$on('document-open-folder', this.openFolder);
    this.$root.$on('document-open-home', this.openHome);
    this.$root.$on('documents-add-folder', this.addFolder);
    this.$root.$on('duplicate-document', this.duplicateDocument);
    this.$root.$on('documents-create-folder', this.createFolder);
    this.$root.$on('documents-rename', this.renameDocument);
    this.$root.$on('documents-move', this.moveDocument);
    this.$root.$on('confirm-document-deletion', this.deleteDocument);
    this.$root.$on('undo-delete-document', this.undoDeleteDocument);
    this.$root.$on('documents-open-drawer', this.openDrawer);
    this.$root.$on('set-current-folder', this.setCurrentFolder);
    this.$root.$on('cancel-add-folder', this.cancelAddFolder);
    this.$root.$on('document-search', this.search);
    this.$root.$on('document-extended-search', this.extendSearch);
    this.$root.$on('save-visibility', this.saveVisibility);
    this.$root.$on('documents-sort', this.sort);
    this.$root.$on('documents-open-attachments-drawer', this.openDrawer);
    this.$root.$on('set-loading', this.setLoading);
    this.$root.$on('documents-filter', filter => {
      this.primaryFilter = filter;
      this.refreshFiles(this.primaryFilter);
    });
    this.$root.$on('show-alert', message => {
      this.displayMessage(message);
    });
    this.getDocumentDataFromUrl()
      .finally(() => {
        this.checkDefaultViewOptions();
        this.optionsLoaded = true;
        this.refreshFiles()
          .then(() => {
            this.watchDocumentPreview();
            if (this.selectedView === 'folder') {
              this.$nextTick().then(() => this.$root.$emit('update-breadcrumb', this.folderPath));
            }
          })
          .finally(() => {
            this.initialized = true;
            this.$root.$applicationLoaded();
          });
      });
    this.$root.$on('create-shortcut', this.createShortcut);
    this.$root.$on('show-version-history', this.showVersionHistory);
  },
  destroyed() {
    document.removeEventListener(`extension-${this.extensionApp}-${this.extensionType}-updated`, this.refreshViewExtensions);
  },
  methods: {
    restoreVersion(version) {
      return this.$documentFileService.restoreVersion(version.id).then(newVersion => {
        if (newVersion) {
          this.$root.$emit('show-alert', {type: 'success', message: this.$t('documents.restore.version.success')});
          this.$root.$emit('version-restored', newVersion);
          this.refreshVersions(this.versionableFile, newVersion);
          this.addRestoreVersionStatistics(this.versionableFile);
        }
      }).catch(() => {
        this.$root.$emit('show-alert', {type: 'error', message: this.$t('documents.restore.version.error')});
        this.$root.$emit('version-restore-error');
      });
    },
    updateVersionSummary(version, summary) {
      if (!summary) {
        this.$root.$emit('show-alert', {type: 'warning', message: this.$t('document.summary.add.empty.error')});
        this.$root.$emit('version-description-update-error', version);
        return;
      }
      return this.$documentFileService.updateVersionSummary(version.originId, version.id, summary).then(version => {
        this.$root.$emit('version-description-updated', version);
        this.$root.$emit('show-alert', {type: 'success', message: this.$t('documents.summary.added.success')});
      }).catch(() => {
        this.$root.$emit('show-alert', {type: 'error', message: this.$t('documents.summary.added.error')});
        this.$root.$emit('version-description-update-error', version);
      });
    },
    setLoading(loading) {
      this.loading = loading;
    },
    versionsDrawerClosed() {
      this.versions = [];
      this.allVersions = [];
    },
    loadMoreVersions() {
      this.versionsPageSize += 10;
      this.versions = this.allVersions.slice(0, this.versionsPageSize);
    },
    refreshVersions(file, newVersion) {
      this.versions.unshift(newVersion);
      this.$root.$emit('version-number-updated', file.id);
      this.updateVersionNumber(file);
    },
    updateVersionNumber(vFile) {
      const index = this.files.findIndex(file => file.id === vFile.id);
      this.files[index].versionNumber++;
    },
    showVersionHistory(file) {
      this.versionableFile = file;
      this.isLoadingVersions = true;
      this.canManageVersions = file.acl.canEdit;
      this.versionsPageSize = Math.round((window.innerHeight-79)/95);
      this.$refs.documentVersionHistory.open();
      const nodeId = file.sourceID ? file.sourceID : file.id;
      this.$documentFileService.getFileVersions(nodeId).then(versions => {
        this.allVersions = versions.versions;
        this.versions = this.allVersions.slice(0, this.versionsPageSize);
        this.isLoadingVersions = false;
      });
      this.addVersionHistoryStatistics();
    },
    addRestoreVersionStatistics(file) {
      document.dispatchEvent(new CustomEvent('exo-statistic-message', {
        detail: {
          module: 'Drive',
          subModule: 'Documents',
          userId: eXo.env.portal.userIdentityId,
          userName: eXo.env.portal.userName,
          operation: 'fileUpdated',
          parameters: {
            fileSize: file.size,
            documentType: 'nt:file',
            fileMimeType: file.mimeType,
            documentName: file.name,
            uuid: file.id
          },
          timestamp: Date.now()
        }
      }));
    },
    addVersionHistoryStatistics() {
      document.dispatchEvent(new CustomEvent('exo-statistic-message', {
        detail: {
          module: 'portal',
          subModule: 'ui',
          userId: eXo.env.portal.userIdentityId,
          userName: eXo.env.portal.userName,
          name: 'actionVersionHistory',
          operation: 'accessVersionHistory',
          parameters: {
            spaceId: eXo.env.portal.spaceId,
            view: this.selectedView === 'timeline' ? 'recentView': 'folderView',
          },
          timestamp: Date.now()
        }
      }));
    },
    folderTreeDrawer(){
      if (this.$refs.folderTreeDrawer){
        this.$refs.folderTreeDrawer.open();
      }
    },
    sort(sortField, ascending) {
      this.sortField = sortField;
      this.ascending = ascending;

      this.files = [];
      this.refreshFiles();
    },
    search(query) {
      const oldQuery = this.query;
      this.extendedSearch = false;
      this.query = query;
      this.refreshFiles();
      if (query && query.length>0){
        this.$root.$emit('enable-extend-filter');
      } else {
        this.$root.$emit('disable-extend-filter');
      }
      if (this.canSendSearchStat && oldQuery !== query) {
        this.canSendSearchStat = false;
        window.setTimeout(() => {
          this.simpleSearchStatistics();
          this.canSendSearchStat = true;
        }, 2000);
      }
      
    },
    extendSearch() {
      this.extendedSearch = true;
      this.refreshFiles();
      this.extendedSearchStatistics();
    },
    getFolderPath(path){
      if (!path){
        path = window.location.pathname;
      }
      if (eXo.env.portal.spaceName){
        if (path.includes('/documents/')){
          this.folderPath = path.substring(path.indexOf('/documents/') + '/documents/'.length);
          this.selectedView = 'folder';
        }
      } else if (path.includes('Private/')) {
        this.folderPath = path.substring(path.indexOf('Private/') + 'Private/'.length);
        this.selectedView = 'folder';
      }
    },
    duplicateDocument(documents){
      this.parentFolderId = documents.id;
      return this.$documentFileService
        .duplicateDocument(this.parentFolderId,this.ownerId,this.prefixClone)
        .then( () => {
          this.parentFolderId=null;
          this.getFolderPath(this.folderPath);
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
      let symlinkId = null;
      if (parentFolder.sourceID){
        symlinkId = parentFolder.id;
        this.parentFolderId = parentFolder.sourceID; 
      }
      this.files = [];
      this.refreshFiles(null, null, null, symlinkId);
      this.$root.$emit('set-breadcrumb', parentFolder);
      let folderPath ='';
      if (eXo.env.portal.spaceName) {
        let newParentPath = parentFolder.path;
        newParentPath = newParentPath.replace(`/spaces/${eXo.env.portal.spaceGroup}`, `/spaces/${eXo.env.portal.spaceGroup}/${eXo.env.portal.spaceName}`);
        const nodeUri = eXo.env.portal.selectedNodeUri.replace('/documents', '/Documents');
        let pathParts = newParentPath.split(nodeUri);
        if (pathParts.length>1){
          folderPath = pathParts[1];
        }
        pathParts = window.location.pathname.split(eXo.env.portal.selectedNodeUri);
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
      this.refreshFiles(this.primaryFilter,null, null, null , true);
    },
    changeView(view) {
      const realPageUrlIndex = window.location.href.toLowerCase().indexOf(eXo.env.portal.selectedNodeUri.toLowerCase()) + eXo.env.portal.selectedNodeUri.length;
      const url = new URL(window.location.href.substring(0, realPageUrlIndex));
      url.searchParams.set('view', view);
      window.history.replaceState('documents', 'Documents', url.toString());

      this.selectedView = view;
      this.parentFolderId = null;
      this.folderPath = null;
      this.files = [];
      this.checkDefaultViewOptions();
      this.refreshFiles(this.primaryFilter)
        .finally(() => {
          if (this.selectedView === 'folder') {
            this.$nextTick().then(() => this.$root.$emit('update-breadcrumb'));
          }
        });
    },
    openHome() {
      this.parentFolderId=null;  
      this.folderPath='';
      this.fileName=null;
      this.refreshFiles(this.primaryFilter);
      if (window.location.pathname.includes('/Private')){
        window.history.pushState('Documents', 'Personal Documents', `${window.location.pathname.split('/Private')[0]}?view=${this.selectedView}`);
      } else if (window.location.pathname.includes('/Public')){
        window.history.pushState('Documents', 'Personal Documents', `${window.location.pathname.split('/Public')[0]}?view=${this.selectedView}`);
      } else {
        window.history.pushState('Documents', 'Personal Documents', `${window.location.pathname}?view=${this.selectedView}`);
      }
    },
    refreshFiles(filterPrimary, deleted, documentId, symlinkId, append) {
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
      } else {
        this.extendedSearch = false;
      }
      if (this.extendedSearch) {
        filter.extendedSearch = this.extendedSearch;
      }
      if (this.sortField) {
        filter.sortField = this.sortField;
      }
      if (this.userId) {
        filter.userId = this.userId;
      }
      if (this.sortField === 'favorite') {
        filter.ascending = this.ascending = false;
      } else {
        filter.ascending = this.ascending;
      }
      if (filterPrimary && filterPrimary==='favorites') {
        this.isFavorites = true;
      }
      if (filterPrimary && filterPrimary==='all') {
        this.isFavorites  =  false;
      }
      if (symlinkId) {
        filter.symlinkFolderId  =  symlinkId;
      }
      filter.favorites = this.isFavorites;
      const expand = this.selectedViewExtension.filePropertiesExpand || 'modifier,creator,owner,metadatas';
      this.offset = append ? this.offset + this.pageSize : 0 ;
      this.limit = append ? this.limit + this.pageSize : this.pageSize ;
      this.loading = true;
      this.$root.$emit('set-documents-search', { 'extended': this.extendedSearch, 'query': this.query});
      
      return this.$documentFileService.getDocumentItems(filter, this.offset, this.limit + 1, expand)
        .then(files => {
          files = this.sortField === 'favorite' ? files && files.sort((file1, file2) => {
            if (file1.favorite === false && file2.favorite === true) {
              return this.ascending ? 1 : -1;
            }
            if (file1.favorite === true && file2.favorite === false) {
              return this.ascending ? -1 : 1;
            }
            return 0;
          }) || files : files;
          this.files = append ? this.files.concat(files) : files ;
          this.files = deleted ? this.files.filter(doc => doc.id !== documentId) : this.files;
          this.hasMore = files && files.length >= this.limit;
          if (this.fileName) {
            const result = files.filter(file => file?.path.endsWith(`/${this.fileName}`));
            if (result.length > 0) {
              this.showPreview(result[0].id);
            }
          }
          this.files.forEach(file => {
            file.canAdd = this.canAdd;
          });
          if (filter.query){
            this.$root.$emit('filer-query',filter.query); 
          }
        })
        .finally(() => this.loading = false);
    },
    checkDefaultViewOptions() {
      if (this.selectedView === 'folder') {
        this.sortField = 'name';
        this.ascending = true;
      } else if (this.selectedView === 'timeline') {
        this.sortField = 'lastUpdated';
        this.ascending = false;
      }
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
          self.fileName = null;
          window.history.pushState('', '', window.location.pathname);
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
      const ownerId = eXo.env.portal.spaceIdentityId || eXo.env.portal.userIdentityId;
      this.$documentFileService.getNewName(ownerId,this.parentFolderId,this.folderPath,'new folder') 
        .then( newName => {
          const newFolder = {
            'id': -1,
            'name': newName,
            'folder': true
          };
          this.files.unshift(newFolder);
        }).catch(e => console.error(e));
    },
    cancelAddFolder(folder){
      this.files.splice(this.files.indexOf(folder), 1);
    },
    createFolder(name){
      const ownerId = eXo.env.portal.spaceIdentityId || eXo.env.portal.userIdentityId;
      this.$documentFileService.createFolder(ownerId,this.parentFolderId,this.folderPath,name)
        .then(createdFolder => {
          this.files.shift();
          this.files.unshift(createdFolder);
        })
        .catch(e => console.error(e))
        .finally(() => this.loading = false);
    },
    renameDocument(file,name){
      const ownerId = eXo.env.portal.spaceIdentityId || eXo.env.portal.userIdentityId;
      this.$documentFileService.renameDocument(ownerId,file.id,name)
        .then(() => this.refreshFiles())
        .catch(e => console.error(e))
        .finally(() => this.loading = false);
    },
    moveDocument(ownerId,fileId,destPath){
      this.$documentFileService.moveDocument(ownerId,fileId,destPath)
        .then( () => {
          this.refreshFiles();
        })
        .catch(e => console.error(e))
        .finally(() => this.loading = false);
    },
    createShortcut(file,destPath, destFolder,space) {
      this.$documentFileService.createShortcut(file.id,destPath)
        .then(() => {
          this.createShortcutStatistics(file,space);
          if (space && space.groupId) {
            const folderPath = destFolder.path.includes('/Documents/') ? destFolder.path.split('/Documents/')[1] : '';
            window.location.href = `${window.location.pathname.split(':spaces')[0] + space.groupId.replaceAll('/', ':')}/${space.prettyName}/documents/${folderPath}`;
          } else {
            this.openFolder(destFolder);
          }
        })
        .catch(e => console.error(e))
        .finally(() => this.loading = false);
    },
    createShortcutStatistics(file,space) {
      document.dispatchEvent(new CustomEvent('exo-statistic-message', {
        detail: {
          module: 'Drive',
          subModule: 'Documents',
          userId: eXo.env.portal.userIdentityId,
          userName: eXo.env.portal.userName,
          name: 'actionFileCreated',
          operation: 'fileCreated',
          parameters: {
            documentName: file.name,
            documentType: 'exo:symlink',
            origin: 'Portlet document',
            category: file.folder ? 'folderCategory' : 'documentCategory',
            spaceId: space ? space.id : eXo.env.portal.spaceId,
            view: this.selectedView === 'timeline' ? 'recentView': 'folderView',
          },
          timestamp: Date.now()
        }
      }));
    },

    simpleSearchStatistics() {
      document.dispatchEvent(new CustomEvent('exo-statistic-message', {
        detail: {
          module: 'Drive',
          subModule: 'Documents',
          userId: eXo.env.portal.userIdentityId,
          userName: eXo.env.portal.userName,
          name: 'simpleSearch',
          operation: 'simpleSearch',
          parameters: {
            spaceId: eXo.env.portal.spaceId,
            origin: 'Portlet document',
            view: this.selectedView === 'timeline' ? 'recentView': 'folderView',
          },
          timestamp: Date.now()
        }
      }));
    },
    extendedSearchStatistics() {
      document.dispatchEvent(new CustomEvent('exo-statistic-message', {
        detail: {
          module: 'Drive',
          subModule: 'Documents',
          userId: eXo.env.portal.userIdentityId,
          userName: eXo.env.portal.userName,
          name: 'actionExtendedSearch',
          operation: 'extendedSearch',
          parameters: {
            spaceId: eXo.env.portal.spaceId,
            origin: 'Portlet document',
            view: this.selectedView === 'timeline' ? 'recentView': 'folderView',
          },
          timestamp: Date.now()
        }
      }));
    },
    saveVisibility(file){
      this.$documentFileService.saveVisibility(file)
        .then(() => this.refreshFiles())
        .catch(e => console.error(e))
        .finally(() => this.loading = false);
    },
    openDrawer(files) {

      let attachmentAppConfiguration = {
        'sourceApp': 'NEW.APP'
      };
      if (eXo.env.portal.spaceName){
        const pathparts = window.location.pathname.toLowerCase().split(`${eXo.env.portal.selectedNodeUri.toLowerCase()}/`);
        if (pathparts.length>1){
          attachmentAppConfiguration= {
            'sourceApp': 'NEW.APP',
            'defaultFolder': this.extractDefaultFolder(),
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
            'defaultFolder': `${this.extractDefaultFolder(true)}`,
            'defaultDrive': {
              isSelected: true,
              name: 'Personal Documents',
              title: 'Personal Documents'
            }
          };
        }
      }
      if (files){
        attachmentAppConfiguration.files=files;
      }
      document.dispatchEvent(new CustomEvent('open-attachments-app-drawer', {detail: attachmentAppConfiguration}));
    },
    extractDefaultFolder(isPersonalDrive) {
      const path = this.currentFolder.path;
      if (isPersonalDrive) {
        return path.substring(path.indexOf('Private') + '/Private'.length);
      }
      return path.substring(path.indexOf('Documents') + '/Documents'.length);
    },
    setCurrentFolder(folder) {
      this.currentFolder = folder;
    },
    getDocumentDataFromUrl() {
      const currentUrlSearchParams = window.location.search;
      const queryParams = new URLSearchParams(currentUrlSearchParams);
      if (!eXo.env.portal.spaceName && queryParams.has('userId')) {
        this.userId = queryParams.get('userId');
      }
      if (queryParams.has('documentPreviewId')) {
        this.loading = true;
        this.previewMode = true;
        const documentPreviewId = queryParams.get('documentPreviewId');
        this.selectedView = 'folder';
        return this.showPreview(documentPreviewId)
          .then(attachment => {
            if (attachment?.path) {
              this.selectFile(attachment.path);
            }
          });
      }
      if (queryParams.has('folderId')) {
        this.parentFolderId = queryParams.get('folderId');
        this.selectedView = 'folder';
      }
      if (queryParams.has('path')) {
        this.selectedView = 'folder';
        const path = queryParams.get('path') || '';
        this.selectFile(path);
      } else {
        const path = window.location.pathname;
        const pathParts  = path.split( `${eXo.env.portal.selectedNodeUri.toLowerCase()}/`);
        if (pathParts.length > 1) {
          this.folderPath = pathParts[1];
          this.selectedView = 'folder';
        }
        if (queryParams.has('view')) {
          const view = queryParams.get('view');
          if (view.toLowerCase() === 'folder'){
            this.selectedView = 'folder';
          } else {
            this.parentFolderId = null;
            this.folderPath = null;
            this.selectedView ='timeline';
          }
        }
        if (this.selectedView === 'folder') {
          this.getFolderPath(this.folderPath);
        }
      }
      return this.$nextTick();
    },
    onBrowserNavChange() {
      this.getDocumentDataFromUrl();
      this.refreshFiles()
        .finally(() => this.$root.$emit('update-breadcrumb'));
    },
    displayMessage(message) {
      this.message = message.message;
      this.alertType = message.type;
      this.alert = true;
      window.setTimeout(() => this.alert = false, 5000);
    },
    selectFile(path) {
      const parentDriveFolder = eXo.env.portal.spaceName && '/Documents/' || '/Private/';
      const nodePath = path.substring(path.indexOf(parentDriveFolder) + parentDriveFolder.length);
      const nodePathParts = nodePath.split('/');

      this.selectedView = 'folder';
      this.fileName = nodePathParts.pop();
      this.folderPath = nodePathParts.join('/');
      return this.getFolderPath(this.folderPath);
    },
    showVersionPreview(version) {
      return this.$attachmentService.getAttachmentById(version.originId)
        .then(attachment => {
          documentPreview.init({
            doc: {
              id: version.frozenId,
              repository: 'repository',
              workspace: 'collaboration',
              path: attachment.path,
              title: attachment.title,
              openUrl: `${attachment.openUrl}?version=${version.versionNumber}`,
              breadCrumb: null,
              size: attachment.size,
              downloadUrl: `${attachment.downloadUrl}?version=${version.versionNumber}`,
              isCloudDrive: attachment.cloudDrive
            },
            author: attachment.updater,
            version: {
              number: attachment.version
            },
            showComments: false,
            showOpenInFolderButton: false,
          });
          return attachment;
        })
        .catch(e => console.error(e))
        .finally(() => this.loading = false);
    },
    showPreview(documentPreviewId) {
      return this.$attachmentService.getAttachmentById(documentPreviewId)
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
              isCloudDrive: attachment.cloudDrive
            },
            author: attachment.updater,
            version: {
              number: attachment.version
            },
            showComments: false,
            showOpenInFolderButton: false,
          });
          return attachment;
        })
        .catch(e => console.error(e))
        .finally(() => this.loading = false);
    },
    dragFile(e){     
      if (this.canAdd){
        this.openDrawer(e.dataTransfer.files);
        this.$root.$emit('hide-upload-overlay');
      }
    },
    startDrag(){
      if (this.canAdd){
        this.$root.$emit('show-upload-overlay');
      }
    },
    canAddDocument(){
      const spaceId= eXo.env.portal.spaceId;
      if (!spaceId){
        this.canAdd = true;
      } else {
        this.$documentFileService.canAddDocument(spaceId)
          .then(canAdd => {
            this.canAdd = canAdd;
          });
      }
      
    }
  },
};
</script>
