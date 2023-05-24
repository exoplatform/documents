<template>
  <v-app
    class="documents-application border-box-sizing"
    :class="isMobile ? 'mobile' : ''"
    role="main"
    flat>
    <div @mouseover="hideOverlay">
      <div
        class="pa-3 white"
        @dragover.prevent
        @drop.prevent
        @dragstart.prevent>
        <documents-header
          :files-size="files.length"
          :selected-view="selectedView"
          :can-add="canAdd"
          :query="query"
          :primary-filter="primaryFilter"
          :is-mobile="isMobile"
          :selected-documents="selectedDocuments"
          class="py-2" />
        <div v-if="searchResult && !loading">
          <documents-no-result-body
            :is-mobile="isMobile"
            :show-extend-filter="showExtendFilter"
            :query="query" />
        </div>
        <div
          v-else-if="!filesLoad && !loading && selectedView === 'folder' "
          @drop="dragFile"
          @dragover="startDrag">
          <documents-no-body-folder
            :query="query"
            :is-mobile="isMobile" />
        </div>
        <div
          v-else-if="!filesLoad && !loading"
          @drop="dragFile"
          @dragover="startDrag">
          <documents-no-body
            :query="query"
            :is-mobile="isMobile" />
        </div>
        <div
          v-else
          @drop="dragFile"
          @dragover="startDrag">
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
            :show-extend-filter="showExtendFilter"
            :primary-filter="primaryFilter"
            :selected-view="selectedView"
            :selected-documents="selectedDocuments"
            :is-mobile="isMobile" />
          <exo-document-notification-alerts />
        </div>
      </div>
      <documents-visibility-drawer :is-mobile="isMobile" />
      <document-tree-selector-drawer :is-mobile="isMobile" />
      <documents-download-drawer />
      <documents-info-drawer
        :selected-view="selectedView"
        :is-mobile="isMobile" />
      <folder-treeview-drawer
        ref="folderTreeDrawer"
        :is-mobile="isMobile" />
      <documents-app-reminder :is-mobile="isMobile" />
      <documents-actions-menu-mobile :is-mobile="isMobile" />
      <documents-filter-menu-mobile
        :primary-filter="primaryFilter"
        :query="query"
        :extended-search="extendedSearch"
        :is-mobile="isMobile" />
      <version-history-drawer
        :can-manage="canManageVersions"
        :enable-edit-description="true"
        :versions="versions"
        :is-loading="isLoadingVersions"
        :show-load-more="showLoadMoreVersions"
        :is-mobile="isMobile" 
        @drawer-closed="versionsDrawerClosed"
        @open-version="showVersionPreview"
        @restore-version="restoreVersion"
        @version-update-description="updateVersionSummary"
        @load-more="loadMoreVersions"
        ref="documentVersionHistory" />
      <document-action-context-menu />
      <v-file-input
        id="uploadVersionInput"
        class="d-none"
        accept="*/*"
        @change="handleUploadVersion" />
    </div>
    <v-alert
      v-model="alert"
      :icon="false"
      :colored-border="isMobile"
      :border="isMobile && !isAlertActionRunning? 'top' : ''"
      :color="alertType"
      :type="!isMobile? alertType: ''"
      :class="isMobile? 'documents-alert-mobile': 'documents-alert'"
      :dismissible="!isMobile">
      <v-progress-linear
        v-if="isAlertActionRunning"
        :active="isAlertActionRunning"
        :height="isMobile? '8px': '4px'"
        :indeterminate="true"
        :class="progressAlertClassMobile"
        :color="progressAlertColor" />
      {{ message }}
      <v-btn
        v-for="action in alertActions"
        :key="action.event"
        :disabled="isAlertActionRunning"
        plain
        text
        color="primary"
        @click="emitAlertAction(action)">
        {{ $t(`document.conflicts.action.${action.event}`) }}
      </v-btn>
      <template #close="{ toggle }">
        <v-btn
          v-if="!isMobile"
          icon
          @click="handleAlertClose(toggle)">
          <v-icon>
            mdi-close-circle
          </v-icon>
        </v-btn>
      </template>
    </v-alert>
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
    showExtendFilter: false,
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
    alertActions: [],
    ownerId: eXo.env.portal.spaceIdentityId || eXo.env.portal.userIdentityId,
    isAlertActionRunning: false,
    documentsToBeDeleted: [],
    showOverlay: false,
    selectedDocuments: [],
    settings: {},
    settingsLoaded: false,
    uploadVersionInput: null,
    newVersionFile: {},
  }),
  computed: {
    progressAlertColor() {
      return this.alertType === 'warning' ? 'amber' : this.alertType === 'error' ? 'red' : 'primary';
    },
    progressAlertClassMobile() {
      return this.isMobile && 'position-relative document-mobile-alert-progress' || '';
    },
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
      return this.$vuetify.breakpoint.width < 960;
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

    this.$root.$on('documents-bulk-delete', this.bulkDeleteDocument);
    this.$root.$on('documents-bulk-download', this.bulkDownloadDocument);
    this.$root.$on('documents-bulk-move', this.bulkMoveDocument);
    this.$root.$on('cancel-bulk-Action', (actionId) => {
      this.setMultiActionLoading(false);
      this.resetSelections();
      this.cancelBulkAction(actionId);
    });
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
      this.refreshFiles({'primaryFilter': this.primaryFilter});
    });
    this.$root.$on('show-alert', (message) => {
      this.displayMessage(message);
    });
    this.getDocumentDataFromUrl()
      .finally(() => {
        this.checkDefaultViewOptions();
        this.optionsLoaded = true;
        const queryParams = new URLSearchParams(window.location.search);
        const disablePreview = queryParams.has('path');
        this.refreshFiles({'disablePreview': disablePreview})
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
    this.$on('keepBoth', this.handleConflicts);
    this.$on('createNewVersion', this.handleConflicts);
    this.$root.$on('cancel-alert-actions', this.handleCancelAlertActions);
    this.$root.$on('update-selection-documents-list', this.updateSelectionList);
    this.$root.$on('breadcrumb-updated', this.resetSelections);
    this.$root.$on('upload-new-version-action-invoke', this.uploadNewVersion);
    this.initSettings();
    document.addEventListener('move-dropped-documents', this.handleMoveDroppedDocuments);
    document.addEventListener('document-open-folder-to-drop', this.handleOpenFolderToDrop);
  },
  destroyed() {
    document.removeEventListener(`extension-${this.extensionApp}-${this.extensionType}-updated`, this.refreshViewExtensions);
  },
  methods: {
    handleOpenFolderToDrop(event) {
      const folderId = event.detail.folder;
      const index = this.files.findIndex(file => file.id === folderId);
      const file = this.files[index];
      if (file.folder) {
        this.openFolder(file);
      }
    },
    handleMoveDroppedDocuments(event) {
      const ownerId = eXo.env.portal.spaceIdentityId || eXo.env.portal.userIdentityId;
      const index = this.files.findIndex(file => file.id === event.detail.destinationId);
      let folder = this.files[index];
      if (!folder?.folder) {
        folder = event.detail.currentOpenedFolder;
      }
      const filesToMove = event.detail.sourceFiles;
      if (filesToMove?.length > 1) {
        this.selectedDocuments = filesToMove;
        this.bulkMoveDocument(ownerId, folder.path, folder, null);
      } else {
        this.moveDocument(ownerId, filesToMove[0], folder.path, folder, null, null);
      }
    },
    initSettings(userSettings) {
      if (userSettings) {
        this.settings = userSettings;
        this.$documentsWebSocket.initCometd(this.settings.cometdContextName, this.settings.cometdToken, this.handleBulkActionNotif);
      } else {
        return this.$documentFileService.getUserSettings()
          .then(settings => {
            if (settings) {
              this.settings = settings;
              this.$documentsWebSocket.initCometd(this.settings.cometdContextName, this.settings.cometdToken, this.handleBulkActionNotif);
            }
          })
          .finally(() => {
            this.settingsLoaded = true;
          });
      }
    },
    uploadNewVersion(file) {
      const fileUpload = document.getElementById('uploadVersionInput');
      this.newVersionFile = {
        targetFileId: file.id
      };
      if (fileUpload != null) {
        fileUpload.accept = file.mimeType;
        fileUpload.click();
      }
    },
    handleUploadVersion(file) {
      if (!file) {
        this.newVersionFile = null;
        return;
      }
      const targetFileId = this.newVersionFile.targetFileId;
      const reader = new FileReader();
      reader.onloadstart = () => {
        this.$root.$emit('document-set-icon-loading', targetFileId, true);
      };
      reader.onload = e => {
        this.newVersionFile.fileBody = e.target.result ;
        this.newVersionFile.mimeType = file.type;
      };
      reader.onloadend = () => {
        const fileBody = this.newVersionFile.fileBody || null;
        this.$documentFileService.uploadNewFileVersion(targetFileId, fileBody)
          .catch(() => {
            this.$root.$emit('show-alert', {
              type: 'error',
              message: this.$t('documents.upload.newVersion.error.message')
            });
          })
          .finally(() => {
            this.$root.$emit('document-set-icon-loading', targetFileId, false);
            this.$root.$emit('show-alert', {
              type: 'success',
              message: this.$t('documents.upload.newVersion.success.message')
            });
            this.refreshFiles();
          });
      };
      reader.readAsArrayBuffer(file);
    },
    pushOrRemoveIfReadOnly(array, file, push) {
      if (file.acl.canEdit) {
        return false;
      }
      const index = array.findIndex(object => object.id === file.id);
      if (index === -1 && push) {
        array.push(file);
      } else {
        array.splice(index, 1);
      }
      return true;
    },
    updateSelectionList(selected, file) {
      const index = this.selectedDocuments.findIndex(object => object.id === file.id);
      const selectedReadOnly = [];
      if (selected && index === -1) {
        const newFile = Object.assign({}, file);
        newFile.metadatas=null;
        this.selectedDocuments.push(newFile);
        const pushed = this.pushOrRemoveIfReadOnly(selectedReadOnly, newFile, true);
        if (pushed) {
          this.$root.$emit('show-alert', {
            type: 'warning',
            message: this.$t('document.multiSelection.readOnly.selected.message')
          });
        }
      } else if (!selected) {
        this.selectedDocuments.splice(index, 1);
        this.pushOrRemoveIfReadOnly(selectedReadOnly, file);
        if (!selectedReadOnly.length) {
          this.hideMessage();
        }
      }
      this.$root.$emit('selection-documents-list-updated', this.selectedDocuments);
    },
    handleBulkActionNotif(actionData) {
      const actionName = actionData.actionType.toLowerCase();
      const actionStatus = actionData.status.toLowerCase();
      if (actionName === 'download'){
        if (actionStatus === 'done_succsussfully') {
          this.$root.$emit('set-download-status','zip_file_created');
          this.setMultiActionLoading(false);
          this.resetSelections();
          this.getDownlodedZip(actionData); 
        } else {
          this.$root.$emit('set-download-status',actionData.status);
        }
      } else {
        const treatedItems = this.selectedDocuments.filter(file => actionData.treatedItemsIds.includes(file.id));
        this.resetSelections();
        if (actionStatus === 'done_with_errors') {
          this.setMultiActionLoading(false);
          this.$root.$emit('show-alert', {
            type: 'error',
            message: this.$t(`documents.bulk.${actionName}.doneWithErrors`)
          });
        }
        if (actionStatus === 'done_succsussfully') {
          this.setMultiActionLoading(false);
          this.displayMessage({
            type: 'success',
            message: this.$t(`documents.bulk.${actionName}.doneSuccessfully`, {0: actionData.numberOfItems})
          });
          if (actionName === 'move') {
            this.handleBulkMoveRedirect();
          }
        }
        if (treatedItems.length && actionName === 'delete') {
          treatedItems.forEach(file => this.addDeleteDocumentStatistics(file, true));
        }
        this.refreshFiles();
      }

    },
    handleBulkMoveRedirect() {
      const folder = JSON.parse(sessionStorage.getItem('folder'));
      const space = JSON.parse(sessionStorage.getItem('space'));
      if (space && space.groupId) {
        this.redirectToSpacePath(space, folder);
      } else {
        setTimeout(() => this.openFolder(folder), 500);
      }
    },
    getDownlodedZip(actionData) {
      this.$documentFileService.getDownloadZip(actionData.actionId).then((transfer) => {
        return transfer.blob();
      }).then((bytes) => {
        const today = new Date();
        const formattedDate = `${(today.getMonth() + 1).toString().padStart(2, '0')}_${today.getDate().toString().padStart(2, '0')}_${today.getFullYear().toString()}`;
        const zipName = eXo.env.portal.spaceDisplayName? `${eXo.env.portal.spaceDisplayName}_${formattedDate}.zip` : `My Drive_${formattedDate}.zip`;
        const elm = document.createElement('a');
        elm.href = URL.createObjectURL(bytes);
        elm.setAttribute('download', zipName);
        elm.click();
        this.$root.$emit('set-download-status',actionData.status);
        if (actionData.status==='DONE_WITH_ERRORS'){
          this.$root.$emit('show-alert', {type: 'error', message: this.$t(`documents.bulk.${actionData.actionType.toLowerCase()}.doneWithErrors`)});
        }
        if (actionData.status==='DONE_SUCCSUSSFULLY'){
          this.$root.$emit('show-alert', {type: 'success', message: this.$t(`documents.bulk.${actionData.actionType.toLowerCase()}.doneSuccessfully`, {0: actionData.numberOfItems})});
        }
      }).catch(e => {
        console.error('Error when export note page', e);
        this.$root.$emit('show-alert', {
          type: 'error',
          message: this.$t(`documents.bulk.${actionData.actionType.toLowerCase()}.failed`)
        });
      });
    },
    handleAlertClose() {
      this.$root.$emit('cancel-action');
      document.dispatchEvent(new CustomEvent('cancel-action-alert'));
      this.alert = false;
    },
    handleCancelAlertActions() {
      if (this.alertActions?.length) {
        this.alert = false;
        this.alertActions = [];
      }
    },
    handleConflicts(action) {
      this.isAlertActionRunning = true;
      if (action.function.name === 'createShortcut') {
        this.createShortcut(...action.function.params, action.event);
      }
      if (action.function.name === 'moveDocument') {
        this.moveDocument(...action.function.params, action.event);
      }
    },
    emitAlertAction(action) {
      this.$emit(action.event, action);
    },
    restoreVersion(version) {
      return this.$documentFileService.restoreVersion(version.id).then(newVersion => {
        if (newVersion) {
          this.$root.$emit('show-alert', {type: 'success', message: this.$t('documents.restore.version.success')});
          this.$root.$emit('version-restored', newVersion);
          this.refreshVersions(this.versionableFile, newVersion);
          this.refreshFiles();
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
        this.showExtendFilter=true;
      } else {
        this.$root.$emit('disable-extend-filter');
        this.showExtendFilter=false;
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
      this.showExtendFilter = false;
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
    addDeleteDocumentStatistics(file, multi) {
      document.dispatchEvent(new CustomEvent('exo-statistic-message', {
        detail: {
          module: 'Drive',
          subModule: 'Document',
          userId: eXo.env.portal.userIdentityId,
          userName: eXo.env.portal.userName,
          operation: 'fileMovedToTrash',
          parameters: {
            uuid: file.id,
            fileName: file.name,
            fileType: file.folder? 'nt:folder' : 'nt:file',
            fileExtension: file.name.split('.').pop(),
            fileSize: file.size,
            fileMimeType: file.mimeType,
            origin: 'Portlet document',
            spaceId: eXo.env.portal.spaceId,
            view: this.selectedView === 'timeline' ? 'recentView': 'folderView',
            deletionType: multi? 'massDeletion': 'individualDeletion'
          },
          timestamp: Date.now()
        }
      }));
    },
    deleteDocument(file){
      const redirectionTime = 500;
      setTimeout(() => {
        const deletedDocument = localStorage.getItem('deletedDocument');
        if (deletedDocument != null) {
          this.refreshFiles({'deleted': true,'documentId': file.id });
          this.addDeleteDocumentStatistics(file);
        }
      }, redirectionTime);
    },
    setMultiActionLoading(status, action) {
      this.$root.$emit('set-action-loading', status, action);
    },
    bulkDeleteDocument(){
      const max = Math.floor(9999);
      const random = crypto.getRandomValues(new Uint32Array(1))[0];
      const actionId =random % max;
      this.setMultiActionLoading(true, 'delete');
      return this.$documentFileService
        .bulkDeleteDocuments(actionId,this.selectedDocuments)
        .catch(e => console.error(e));
    },
    undoDeleteDocument(){
      const deletedDocument = localStorage.getItem('deletedDocument');
      if (deletedDocument != null) {
        this.refreshFiles();
      }
    },
    bulkDownloadDocument(){
      this.setMultiActionLoading(true, 'download');
      const max = Math.floor(9999);
      const random = crypto.getRandomValues(new Uint32Array(1))[0];
      const actionId =random % max; 
      this.$root.$emit('open-download-drawer',actionId);
      return this.$documentFileService
        .bulkDownloadDocument(actionId,this.selectedDocuments)
        .catch(e => console.error(e));
    },
    cancelBulkAction(actionId){
      return this.$documentFileService
        .cancelBulkAction(actionId)
        .catch(e => console.error(e));
    },
    redirectToSpacePath(space, folder) {
      const folderPath = folder.path.includes('/Documents/') ? folder.path.split('/Documents/')[1] : '';
      const pathName = `${window.location.origin}/${eXo.env.portal.containerName}/g/`;
      const spaceGroup = space.groupId.replaceAll('/', ':');
      window.setTimeout(() => {
        window.location.href = `${pathName + spaceGroup }/${space.prettyName}/documents/${folderPath}`;
      }, 1000);
    },
    bulkMoveDocument(ownerId, destPath, folder, space){
      const max = Math.floor(9999);
      const random = crypto.getRandomValues(new Uint32Array(1))[0];
      const actionId =random % max;
      this.setMultiActionLoading(true, 'move');
      sessionStorage.setItem('folder', JSON.stringify(folder));
      sessionStorage.setItem('space', JSON.stringify(space));
      return this.$documentFileService
        .bulkMoveDocuments(actionId,this.selectedDocuments, ownerId, destPath)
        .catch(e => console.error(e));
    },
    openFolder(parentFolder) {
      this.folderPath = '';
      this.fileName = null;
      this.parentFolderId = parentFolder.id;
      let symlinkId = null;
      if (parentFolder.sourceID){
        symlinkId = parentFolder.id;
        this.parentFolderId = parentFolder.sourceID; 
      }
      this.files = [];
      this.refreshFiles({'symlinkId': symlinkId});

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
        if (parentFolder.path?.includes(userPrivatePathPrefix)){
          const pathParts = parentFolder.path.split(userPrivatePathPrefix);
          if (pathParts.length > 1){
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
      document.dispatchEvent(new CustomEvent('documents-folder-opened', {detail: {folder: parentFolder}}));
      this.resetSelections();
    },
    loadMore() {
      this.refreshFiles({'primaryFilter': this.primaryFilter, 'append': true});
    },
    resetSelections() {
      this.$root.$emit('reset-selections');
      this.selectedDocuments = [];
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
      this.$root.$emit('resetSearch');
      this.resetSelections();
      this.primaryFilter='all';
      this.query=null;
      this.extendedSearch=false;
      this.$root.$emit('set-documents-search', { 'extended': this.extendedSearch, 'query': this.query});
      this.$root.$emit('set-documents-filter', 'All');
      this.checkDefaultViewOptions();
      this.refreshFiles({'primaryFilter': this.primaryFilter})
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
      this.refreshFiles({'primaryFilter': this.primaryFilter});
      if (window.location.pathname.includes('/Private')){
        window.history.pushState('Documents', 'Personal Documents', `${window.location.pathname.split('/Private')[0]}?view=${this.selectedView}`);
      } else if (window.location.pathname.includes('/Public')){
        window.history.pushState('Documents', 'Personal Documents', `${window.location.pathname.split('/Public')[0]}?view=${this.selectedView}`);
      } else {
        window.history.pushState('Documents', 'Personal Documents', `${window.location.pathname}?view=${this.selectedView}`);
      }
      document.dispatchEvent(new CustomEvent('documents-folder-opened', {detail: {folder: null}}));
    },
    refreshFiles(options) {
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
      filter.ascending = this.ascending;
      if (options?.primaryFilter==='favorites') {
        this.isFavorites = true;
      }
      if (options?.primaryFilter==='all') {
        this.isFavorites  =  false;
      }
      if (options?.symlinkId) {
        filter.symlinkFolderId  =  options.symlinkId;
      }
      if (options?.deleted) {
        this.documentsToBeDeleted.push(options?.documentId);
      }
      filter.favorites = this.isFavorites;
      const expand = this.selectedViewExtension.filePropertiesExpand || 'modifier,creator,owner,metadatas';
      this.offset = options?.append ? this.offset + this.pageSize : 0 ;
      this.limit = options?.append ? this.limit + this.pageSize : this.pageSize ;
      this.loading = true;
      this.$root.$emit('set-documents-search', { 'extended': this.extendedSearch, 'query': this.query});

      return this.$documentFileService.getDocumentItems(filter, this.offset, this.limit + 1, expand)
        .then(files => {
          files = this.sortField === 'favorite' ? files && files.sort((file1, file2) => {
            if (file1.favorite === false && file2.favorite === true) {
              return this.ascending ? -1 : 1;
            }
            if (file1.favorite === true && file2.favorite === false) {
              return this.ascending ? 1 : -1;
            }
            return 0;
          }) || files : files;
          this.files = options?.append ? this.files.concat(files) : files ;
          this.files = [...new Map(this.files.map((item) => [item['id'], item])).values()];
          this.files = options?.deleted ? this.files.filter(this.isDocumentsToBeDeleted) : this.files;
          this.hasMore = files && files.length >= this.limit;
          if (this.fileName && !options?.disablePreview) {
            const result = files.filter(file => file?.path.endsWith(`/${this.fileName}`));
            if (result.length > 0) {
              this.showPreview(result[0].id);
            }
          }
          this.files.forEach(file => {
            file.canAdd = this.canAdd;
          });
        })
        .finally(() => this.loading = false);
    },
    isDocumentsToBeDeleted(doc) {
      return this.documentsToBeDeleted.find(documentId => documentId === doc.id) ? false : true;
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
      const i18nName = this.$t('Folder.label.newfolder');
      this.$documentFileService.getNewName(ownerId, this.parentFolderId, this.folderPath, i18nName)
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
          createdFolder.canAdd = this.canAdd;
          this.files.shift();
          this.files.unshift(createdFolder);
        }).catch(e => {
          if (e.status === 409) {
            this.$root.$emit('show-alert', {
              type: 'warning',
              message: this.$t('document.folder.conflict.error.message')
            });
          }
        })
        .finally(() => this.loading = false);
    },
    renameDocument(file,name){
      const ownerId = eXo.env.portal.spaceIdentityId || eXo.env.portal.userIdentityId;
      this.$documentFileService.renameDocument(ownerId,file.id,name)
        .then(() => {
          this.refreshFiles();
          this.$root.$emit('document-renamed', file);
        })
        .catch(e => {
          if (e.status === 409) {
            this.$root.$emit('show-alert', {
              type: 'warning',
              message: file.folder ? this.$t('document.folder.conflict.error.message')
                : this.$t('document.file.conflict.error.message')
            });
          }
        })
        .finally(() => {
          this.loading = false;
        });
    },
    getConflictMessage(file) {
      if (file.folder && !this.isMobile) {
        return this.$t('document.folder.conflict.error.message.action');
      } else if (file.folder && this.isMobile) {
        return this.$t('document.folder.conflict.error.message');
      } else if (!file.folder && this.isMobile) {
        return this.$t('document.file.conflict.error.message');
      } else {
        return this.$t('document.file.conflict.error.message.action');
      }
    },
    getConflictActions(object, fn) {
      const actions = [{
        event: 'keepBoth',
        function: fn
      }];
      if (object.versionable) {
        actions.push({event: 'createNewVersion', function: fn});
        return actions;
      } else {
        return actions;
      }
    },
    moveDocument(ownerId, file, destPath, destFolder, space, conflictAction) {
      this.$documentFileService.moveDocument(ownerId, file.id, destPath, conflictAction)
        .then( () => {
          this.displayMessage({
            type: 'success',
            message: file.folder ? this.$t('document.alert.success.label.moveFolder') : this.$t('document.alert.success.label.moveDocument')
          });
          if (space && space.groupId) {
            const folderPath = destFolder.path.includes('/Documents/') ? destFolder.path.split('/Documents/')[1] : '';
            window.setTimeout(() => {
              window.location.href = `${window.location.pathname.split(':spaces')[0] + space.groupId.replaceAll('/', ':')}/${space.prettyName}/documents/${folderPath}`;
            }, 1000);
          } else {
            this.openFolder(destFolder);
          }
          this.$root.$emit('document-moved');
          this.isAlertActionRunning = false;
        })
        .catch(e => {
          if (e.status === 409) {
            e.json().then(response => {
              this.$root.$emit('show-alert', {
                type: 'warning',
                message: this.getConflictMessage(file),
                actions: this.getConflictActions(response.existingObject, {
                  name: 'moveDocument',
                  params: [ownerId, file, destPath, destFolder, space] // moveDocument function arguments
                })
              });
            });
          } else {
            this.$root.$emit('show-alert', {type: 'error', message: this.$t('document.alert.move.error')});
          }
        })
        .finally(() => this.loading = false);
    },
    createShortcut(file,destPath, destFolder,space, conflictAction) {
      this.$documentFileService.createShortcut(file.id,destPath, conflictAction)
        .then(() => {
          this.$root.$emit('show-alert', {type: 'success', message: this.$t('document.shortcut.creationSuccess')});
          this.createShortcutStatistics(file,space);
          if (space && space.groupId) {
            const folderPath = destFolder.path.includes('/Documents/') ? destFolder.path.split('/Documents/')[1] : '';
            window.location.href = `${window.location.pathname.split(':spaces')[0] + space.groupId.replaceAll('/', ':')}/${space.prettyName}/documents/${folderPath}`;
          } else {
            this.openFolder(destFolder);
          }
          this.$root.$emit('shortcut-created');
          this.isAlertActionRunning = false;
        })
        .catch((e) => {
          if (e.status === 409) {
            this.$root.$emit('show-alert', {
              type: 'warning',
              message: this.getConflictMessage(file),
              actions: [{
                event: 'keepBoth',
                function: {
                  name: 'createShortcut',
                  params: [file, destPath, destFolder, space] // createShortcut function arguments
                }
              }],
            });
          } else {
            this.$root.$emit('show-alert', {type: 'error', message: this.$t('document.shortcut.creationError')});
          }
        })
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
          name: 'actionSimpleSearch',
          operation: 'simpleSearch',
          parameters: {
            spaceId: eXo.env.portal.spaceId,
            origin: eXo.env.portal.spaceId ? 'Document':'Personal document',
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
            origin: eXo.env.portal.spaceId ? 'Document':'Personal document',
            view: this.selectedView === 'timeline' ? 'recentView': 'folderView',
          },
          timestamp: Date.now()
        }
      }));
    },
    saveVisibility(file){
      this.$documentFileService.saveVisibility(file)
        .then(() => {
          this.refreshFiles();
          this.$root.$emit('show-alert', {type: 'success', message: this.$t('documents.label.saveVisibility.success')});
          this.$root.$emit('visibility-saved');
        })
        .catch(() => {
          this.$root.$emit('show-alert', {type: 'error', message: this.$t('documents.label.saveVisibility.error')});
        })
        .finally(() => {
          this.loading = false;
        }
        );
    },
    openDrawer(files) {

      const attachmentAppConfiguration = {
        'sourceApp': 'NEW.APP'
      };
      if (eXo.env.portal.spaceName) {
        attachmentAppConfiguration.defaultDrive = {
          isSelected: true,
          name: `.spaces.${eXo.env.portal.spaceGroup}`,
          title: eXo.env.portal.spaceDisplayName,
        };
        const pathparts = window.location.pathname.toLowerCase().split(`${eXo.env.portal.selectedNodeUri.toLowerCase()}/`);
        if (pathparts.length > 1) {
          attachmentAppConfiguration.defaultFolder = this.extractDefaultFolder();
        }
      } else {
        attachmentAppConfiguration.defaultDrive = {
          isSelected: true,
          name: 'Personal Documents',
          title: 'Personal Documents'
        };
        attachmentAppConfiguration.defaultFolder = '/';
        let pathparts = window.location.pathname.split(`${eXo.env.portal.selectedNodeUri}/`);
        if (pathparts.length > 1 && pathparts[1].startsWith('Private/')){
          pathparts = pathparts[1].split('Private/');
        }
        if (pathparts.length > 1) {
          attachmentAppConfiguration.defaultFolder = `${this.extractDefaultFolder(true)}`;
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
    getDocumentDataFromUrl(path) {
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
        if (!path) {
          path = window.location.pathname;
        }
        const pathParts  = path.split( `${eXo.env.portal.selectedNodeUri.toLowerCase()}/`);
        if (pathParts.length > 1) {
          this.folderPath = pathParts[1];
          this.selectedView = 'folder';
        } else {
          this.folderPath = '';
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
    onBrowserNavChange(e) {
      this.resetSelections();
      this.getDocumentDataFromUrl(e.currentTarget.location.pathname);
      this.parentFolderId = null;
      this.refreshFiles()
        .finally(() => this.$root.$emit('update-breadcrumb', this.folderPath));
    },
    displayMessage(message) {
      this.message = message.message;
      this.alertType = message.type;
      this.alertActions = message.actions;
      this.alert = true;
      setTimeout(() => {
        if (!this.alertActions?.length) {
          this.alert = false;
        }
      }, 5000);
    },
    hideMessage() {
      this.alert = false;
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
      const item = e.dataTransfer.items[0];
      const isFile = item && item.type !== '';
      if (this.canAdd && isFile){
        this.openDrawer(e.dataTransfer.files);
        this.showOverlay = false;
        this.$root.$emit('hide-upload-overlay');
      }
    },
    startDrag(e){
      this.showOverlay = true;
      const item = e.dataTransfer.items[0];
      //folder haven't type
      const isFile = item && item.type !== '';
      if (this.canAdd && isFile){
        this.$root.$emit('show-upload-overlay');
      }
    },
    hideOverlay() {
      if (this.showOverlay){
        this.showOverlay = false;
        this.$root.$emit('hide-upload-overlay');
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
