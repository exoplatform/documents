<template>
  <v-app
    class="documents-application border-box-sizing"
    :class="isMobile ? 'mobile' : ''"
    role="main"
    flat>
    <v-hover v-model="$root.hover">
      <div class="application-body" @mouseover="hideOverlay">
        <div
          @dragover.prevent
          @drop.prevent
          @dragstart.prevent>
          <documents-header
            :files-size="files.length"
            :selected-view="selectedView"
            :can-add="canAdd"
            :query="query"
            :primary-filter="primaryFilter"
            :file-type="fileType"
            :after-date="afterDate"
            :before-date="beforeDate"
            :min-size="minSize"
            :max-size="maxSize"
            :is-mobile="isMobile"
            :selected-documents="selectedDocuments"
            @documents-type-view-applied="applyViewType" />
          <categories-filter
            v-if="displayCategoriesFilter"
            v-show="hasDocuments"
            v-model="$root.selectedCategoryId"
            :category-depth="$root.categoryDepth"
            :category-ids="$root.settings.categoryIds"
            :exclude-category-ids="$root.settings.excludeCategoryIds"
            :space-id="$root.ownerId"
            class="full-width border-box-sizing application-background-color application-border application-border-radius py-2 px-4"
            object-type="document"
            hide-on-empty />
          <div v-if="searchResult && !loading && initialized">
            <documents-no-result-body
              :is-mobile="isMobile"
              :show-extend-filter="showExtendFilter"
              :query="query" />
          </div>
          <div
            v-else-if="!filesLoad && selectedView !== 'folder' && !loading && initialized"
            @drop="dragFile"
            @dragover="startDrag">
            <documents-no-body
              :query="query"
              :can-add="canAdd"
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
              :file-type="fileType"
              :after-date="afterDate"
              :before-date="beforeDate"
              :min-size="minSize"
              :max-size="maxSize"
              :selected-view="selectedView"
              :selected-documents="selectedDocuments"
              :view-type="viewType"
              :folder-path="folderPath"
              :is-mobile="isMobile"
              class="px-4 no-border" />
            <exo-document-notification-alerts />
          </div>
        </div>
      
        <documents-visibility-drawer :is-mobile="isMobile" />
        <document-tree-selector-drawer :is-mobile="isMobile" />
        <documents-advanced-filter-drawer />
        <documents-download-drawer />
        <document-import-from-zip-drawer />
        <documents-offline-changes-reminder />
        <open-in-desktop-credentials-dialog
          ref="dialog" />
        <documents-actions-menu-mobile
          :is-mobile="isMobile"
          :current-view="selectedView"
          :is-search-result="isSearchResult" />
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
        <public-document-options-drawer />
        <category-form-drawer />
        <documents-add-new-menu-mobile
          ref="documentAddItemMenu"
          :selected-view="selectedView"
          :is-mobile="isMobile" />
        <v-file-input
          id="uploadVersionInput"
          class="d-none"
          accept="*/*"
          @change="handleUploadVersion" />
        <categories-bulk-edit-drawer />
      </div>
    </v-hover>
  </v-app>
</template>
<script>

export default {
  data: () => ({
    canAdd: false,
    spaceCanAdd: false,
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
    selectedView: '',
    previewMode: false,
    primaryFilter: 'all',
    documentsToBeDeleted: [],
    showOverlay: false,
    selectedDocuments: [],
    settings: {},
    settingsLoaded: false,
    uploadVersionInput: null,
    newVersionFile: {},
    fileType: [],
    afterDate: null,
    beforeDate: null,
    minSize: null,
    maxSize: null,
    showHidden: false,
    publicLinkUrl: `${window.location.origin}/${eXo.env.portal.containerName}/download-document/`,
    drivesLimit: 5,
    drivesOffset: 0,
    viewType: 'listView',
    drives: [],
  }),
  computed: {
    displayCategoriesFilter() {
      return this.$root.allowFilteringPerCategory && this.recentViewSelected;
    },
    hasDocuments() {
      return !!this.files.length;
    },
    recentViewSelected() {
      return this.selectedView === 'timeline';
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
    isSearchResult(){
      return ((this.query && this.query.length > 0) || this.minSize || this.maxSize || this.afterDate || this.beforeDate || this.fileType?.length>0 || this.primaryFilter!=='all') ;
    },
    selectedCategoryIds() {
      return this.$root.selectedCategoryIds?.length ? this.$root.selectedCategoryIds : this.$root.categoryIds;
    },
    excludeCategoryIds() {
      return this.$root.excludeCategoryIds;
    },
    canEditCurrentFolder() {
      return this.currentFolder?.accessList?.canEdit;
    }
  },
  watch: {
    recentViewSelected() {
      if (this.recentViewSelected) {
        this.$root.selectedPath = '/';
      }
    },
    selectedCategoryIds() {
      if (this.initialized) {
        this.refreshFiles();
      }
    },
  },
  created() {
    this.viewType = this.$documentsUtils.getViewType(this.$root.appId);
    document.addEventListener('categories-updated', this.refreshDocument);
    document.addEventListener('categories-drawer-closed', this.resetMultiSelection);

    document.addEventListener(`extension-${this.extensionApp}-${this.extensionType}-updated`, this.refreshViewExtensions);

    window.addEventListener('popstate', e => {this.onBrowserNavChange(e);});

    this.refreshViewExtensions();
    this.canAddDocument();

    this.$root.$on('documents-bulk-delete', this.bulkDeleteDocument);
    this.$root.$on('documents-bulk-download', this.bulkDownloadDocument);
    this.$root.$on('documents-bulk-edit-categories', this.bulkEditCategories);
    this.$root.$on('documents-bulk-move', this.bulkMoveDocument);
    this.$root.$on('documents-zip-import', this.importDocuments);
    this.$root.$on('cancel-bulk-Action', (actionId) => {
      this.setMultiActionLoading(false);
      this.resetSelections();
      this.cancelBulkAction(actionId);
    });
    this.$root.$on('open-add-new-mobile', this.displayAddMenuMobile);
    this.$root.$on('close-add-new-mobile', this.hideAddMenuMobile);
    this.$root.$on('documents-refresh-files', this.refreshFiles);
    this.$root.$on('document-load-more', this.loadMore);
    this.$root.$on('document-change-view', this.changeView);
    this.$root.$on('document-open-folder', this.openFolder);
    this.$root.$on('document-show-drives', this.showDrives);
    this.$root.$on('document-open-home', this.openHome);
    this.$root.$on('documents-add-folder', this.addFolder);
    this.$root.$on('duplicate-document', this.duplicateDocument);
    this.$root.$on('past-document', this.pastDocument);
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
    this.$root.$on('set-advanced-filter', advancedFilter => {
      this.fileType = advancedFilter.fileType;
      this.afterDate = advancedFilter.selectedPeriod?.min;
      this.beforeDate = advancedFilter.selectedPeriod?.max;
      this.minSize = advancedFilter.minSize;
      this.maxSize = advancedFilter.maxSize;
      this.showHidden = advancedFilter.showHidden;
      this.refreshFiles();
    });
    this.$root.$on('open-folder-by-id', (folderId) => {
      const realPageUrlIndex = window.location.href.toLowerCase().indexOf(location.pathname.toLowerCase()) + location.pathname.length;
      const url = new URL(window.location.href.substring(0, realPageUrlIndex));
      const params = new URLSearchParams(document.location.search);
      params.set('folderId', folderId);
      params.forEach((value, key) => { url.searchParams.append(key, value); });
      window.history.replaceState('documents', 'Documents', url.toString());
      this.parentFolderId = folderId;
      this.changeView('folder');
    });
    this.$root.$on('show-alert', (message) => {
      this.displayMessage(message);
    });
    this.initSettings()
      .finally(() => {
        this.getDocumentDataFromUrl()
          .finally(() => {
            this.checkDefaultViewOptions();
            this.optionsLoaded = true;
            const queryParams = new URLSearchParams(window.location.search);
            const disablePreview = queryParams.has('path');
            const options = localStorage.getItem('deletedDocument') !== null ? {'disablePreview': disablePreview, 'deleted': true,'documentId': localStorage.getItem('deletedDocument')} : {'disablePreview': disablePreview};
            this.refreshFiles(options)
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
      });
    this.$root.$on('create-shortcut', this.createShortcut);
    this.$root.$on('show-version-history', this.showVersionHistory);
    this.$on('keepBoth', this.handleConflicts);
    this.$on('createNewVersion', this.handleConflicts);
    this.$root.$on('update-selection-documents-list', this.updateSelectionList);
    this.$root.$on('breadcrumb-updated', this.resetSelections);
    this.$root.$on('upload-new-version-action-invoke', this.uploadNewVersion);
    this.$root.$on('copy-public-access-link', this.getDocumentPublicAccessLink);
    this.$root.$on('mark-document-as-viewed', this.markDocumentAsViewed);
    this.$root.$on('documents-folder-download', this.downloadFolder);
    this.$root.$on('documents-preview', this.previewDocument);
    this.$root.$on('hide-element', this.hideElement);
    this.$root.$on('add-drives', this.addDrives);
    document.addEventListener('move-dropped-documents', this.handleMoveDroppedDocuments);
    document.addEventListener('document-open-folder-to-drop', this.handleOpenFolderToDrop);
    document.addEventListener('document-category-selected', (event) => {
      if (event && event.detail) {
        this.selectedView = 'timeline';
        this.$nextTick().then(() => {
          if (this.$root.selectedCategoryId === event.detail.categoryId) {
            this.$root.selectedCategoryId = null;
          } else {
            this.$root.selectedCategoryId = event.detail.categoryId;
          }
        });
      }
    });
  },
  destroyed() {
    document.removeEventListener(`extension-${this.extensionApp}-${this.extensionType}-updated`, this.refreshViewExtensions);
  },
  beforeDestroy() {
    this.$root.$off('add-drives', this.addDrives());
  },
  methods: {
    copyPublicLinkToClipBoard(text) {
      navigator.clipboard.writeText(text).then(() => {
        this.$root.$emit('show-alert', {
          type: 'success',
          message: this.$t('documents.alert.success.label.linkCopied')
        });
      }).catch(() => {
        this.$root.$emit('show-alert', {
          type: 'error',
          message: this.$t('document.public.access.copyLink.error.message')
        });
      });
    },
    downloadFolder(file) {
      this.selectedDocuments.push(file);
      this.bulkDownloadDocument();
    },
    addDrives(drives) {
      if (drives) {
        this.drives.push(...drives);
      }
    },
    hideElement(element) {
      if (!this.showHidden){
        this.files = this.files.filter(file => file.id !== element.id);
      }
    },
    markDocumentAsViewed(file) {
      if (file && !file.version) {
        document.dispatchEvent(new CustomEvent('mark-attachment-as-viewed', {detail: {file: file}}));
      }
    },
    getDocumentPublicAccessLink(nodeId) {
      if (window.ClipboardItem && navigator.clipboard.write) {
        const text = new window.ClipboardItem({
          'text/plain': this.$documentFileService.getDocumentPublicAccess(nodeId)
            .then((publicDocumentAccess) => {
              this.$root.$emit('show-alert', {
                type: 'success',
                message: this.$t('documents.alert.success.label.linkCopied')
              });
              return new Blob([`${this.publicLinkUrl}${publicDocumentAccess.nodeId}`],
                { type: 'text/plain' });
            })
            .catch((e) => {
              if (e.status === 404) {
                this.$root.$emit('show-alert', {
                  type: 'warning',
                  message: this.$t('document.visibility.publicAccess.save.message')
                });
                return new Blob([`${this.publicLinkUrl}${nodeId}`],
                  { type: 'text/plain' });
              }
            })
        });
        navigator.clipboard.write([text]).catch(() => {
          this.$root.$emit('show-alert', {
            type: 'error',
            message: this.$t('document.visibility.publicAccess.error.message')
          });
        });
      }
      else {
        this.getAndCopyDocumentPublicAccessLink(nodeId);
      }
    },
    getAndCopyDocumentPublicAccessLink(nodeId) {
      this.$documentFileService.getDocumentPublicAccess(nodeId).then(publicDocumentAccess => {
        this.copyPublicLinkToClipBoard(`${this.publicLinkUrl}${publicDocumentAccess.nodeId}`);
      }).catch((e) => {
        if (e.status === 404) {
          navigator.clipboard.writeText(`${this.publicLinkUrl}${nodeId}`).then(() => {
            this.$root.$emit('show-alert', {
              type: 'warning',
              message: this.$t('document.visibility.publicAccess.save.message')
            });
          }).catch(() => {
            this.$root.$emit('show-alert', {
              type: 'error',
              message: this.$t('document.public.access.copyLink.error.message')
            });
          });
        } else {
          this.$root.$emit('show-alert', {
            type: 'error',
            message: this.$t('document.visibility.publicAccess.error.message')
          });
        }
      });
    },
    createDocumentPublicAccessLink(file, publicAccessOptions) {
      return this.$documentFileService.createDocumentPublicAccess(file.id, publicAccessOptions)
        .then(() => {
          this.refreshFiles();
        }).catch(() => {
          this.$root.$emit('show-alert', {
            type: 'error',
            message: this.$t('document.visibility.publicAccess.error.message')
          });
        });
    },
    handleOpenFolderToDrop(event) {
      const folderId = event.detail.folder;
      const index = this.files.findIndex(file => file.id === folderId);
      const file = this.files[index];
      if (file.folder) {
        this.openFolder(file);
      }
    },
    handleMoveDroppedDocuments(event) {
      const index = this.files.findIndex(file => file.id === event.detail.destinationId);
      let folder = this.files[index];
      if (!folder?.folder) {
        folder = event.detail.currentOpenedFolder;
      }
      const filesToMove = event.detail.sourceFiles;
      if (filesToMove?.length > 1) {
        this.selectedDocuments = filesToMove;
        this.bulkMoveDocument(folder.path, folder, null);
      } else {
        this.moveDocument(filesToMove[0], folder.path, folder, null, null);
      }
    },
    initSettings() {
      const lastViewStorageKey = eXo.env.portal.spaceId ? `lastView-${eXo.env.portal.spaceId}` : 'lastView';
      const lastView = eXo.env.portal.spaceId ? localStorage.getItem(lastViewStorageKey) : 'folder';
      if (lastView && Object.values(this.viewExtensions).find(viewExtension => viewExtension.id === lastView)) {
        this.selectedView = lastView;
      } else if (this.$root.settings?.defaultView  && Object.values(this.viewExtensions).find(viewExtension => viewExtension.id === this.$root.settings.defaultView)) {
        this.selectedView =  this.$root.settings.defaultView;
      } else {
        this.selectedView = eXo.env.portal.spaceId ? 'timeline' : 'folder';
      }
      this.$documentsWebSocket.initCometd(eXo.env.portal.cometdContext, eXo.env.portal.cometdToken, this.handleBulkActionNotif);
      return this.$documentFileService.getUserSettings(this.$root.ownerId)
        .then(userSettings => {
          this.userSettings = userSettings;
          if (userSettings) {
            this.$root.$emit('enable-import', userSettings.canImport);
          }
        })
        .finally(() => {
          this.settingsLoaded = true;
        });
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
      this.loading = true;
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
      }
      this.$root.$emit('selection-documents-list-updated', this.selectedDocuments);
    },
    handleBulkActionNotif(actionData) {
      const actionName = actionData.actionType.toLowerCase();
      const actionStatus = actionData.status.toLowerCase();
      if (actionName === 'download'){
        if (actionStatus === 'done_successfully') {
          this.$root.$emit('set-download-status','zip_file_created');
          this.setMultiActionLoading(false);
          this.resetSelections();
          this.getDownloadedZip(actionData);
        } else {
          this.$root.$emit('set-download-status',actionData.status);
        }
      } else if (actionName === 'import_zip'){
        this.$root.$emit('set-import-status', actionData);
        if (actionStatus==='import_limit_exceeded'){
          this.$root.$emit('enable-import', false);
        } 
        if (actionStatus==='import_limit_not_exceeded'){
          this.$root.$emit('enable-import', true);
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
        if (actionStatus === 'done_successfully') {
          this.setMultiActionLoading(false);
          this.displayMessage({
            type: 'success',
            message: this.$t(`documents.bulk.${actionName}.doneSuccessfully`, {0: treatedItems.length})
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
    getDownloadedZip(actionData) {
      this.$documentFileService.getDownloadZip(actionData.actionId).then((transfer) => {
        return transfer.blob();
      }).then((bytes) => {
        const today = new Date();
        const formattedDate = `${(today.getMonth() + 1).toString().padStart(2, '0')}_${today.getDate().toString().padStart(2, '0')}_${today.getFullYear().toString()}`;
        const sourceName = localStorage.getItem(`bulkDownloadSourceName${actionData.actionId}`);
        const zipName = `${sourceName}_${formattedDate}.zip`;
        localStorage.removeItem(sourceName);
        const elm = document.createElement('a');
        elm.href = URL.createObjectURL(bytes);
        elm.setAttribute('download', zipName);
        elm.click();
        this.$root.$emit('set-download-status',actionData.status);
        if (actionData.status==='DONE_WITH_ERRORS'){
          this.$root.$emit('show-alert', {type: 'error', message: this.$t(`documents.bulk.${actionData.actionType.toLowerCase()}.doneWithErrors`)});
        }
        if (actionData.status.toLowerCase()==='done_successfully'){
          this.$root.$emit('show-alert', {type: 'success', message: this.$t(`documents.bulk.${actionData.actionType.toLowerCase()}.doneSuccessfully`, {0: actionData.numberOfItems})});
        }
      }).catch(e => {
        if (e.status === 410) {
          this.$root.$emit('set-download-status',actionData.status);
        } else {
          console.error('Error when export note page', e);
          this.$root.$emit('show-alert', {
            type: 'error',
            message: this.$t(`documents.bulk.${actionData.actionType.toLowerCase()}.failed`)
          });
        }
      });
    },
    handleConflicts(action) {
      if (action.function.name === 'createShortcut') {
        this.createShortcut(...action.function.params, action.event);
      }
      if (action.function.name === 'moveDocument') {
        this.moveDocument(...action.function.params, action.event);
      }
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
      summary = summary.replaceAll(':', ' ').replaceAll('*', ' ');
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
            view: this.recentViewSelected ? 'recentView': 'folderView',
          },
          timestamp: Date.now()
        }
      }));
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
      if  (this.$root.driveView){
        this.searchDrives(query);
      } else {
        this.query = query;
        this.refreshFiles();
      }
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
      } else if (path !== window.location.pathname){
        this.folderPath = path;
        this.selectedView = 'folder';
      }

    },

    importDocuments(uploadId,overrideMode){
      this.setMultiActionLoading(true,'import');
      this.$documentFileService.importFilesFromZip(this.$root.ownerId,this.parentFolderId,this.folderPath,uploadId,overrideMode).then().catch(e => {
        console.error('Error when import documents', e);
        this.status='failed';
        this.$root.$emit('show-alert', {
          type: 'error',
          message: this.$t('documents.import.status.failed')
        });
      });
    },


    duplicateDocument(documents){
      return this.$documentFileService
        .duplicateDocument(documents.id,null,this.$root.ownerId,this.prefixClone)
        .then( () => {
          this.refreshFiles();
          if (documents.folder){
            this.$root.$emit('show-alert', {type: 'success',message: this.$t('documents.alert.success.label.duplicateFolder')});
          } else {
            this.$root.$emit('show-alert', {type: 'success',message: this.$t('documents.alert.success.label.duplicateDocument')});
          }

        }).catch(e => console.error(e));
    },

    pastDocument(documentId,destFolderId){
      return this.$documentFileService
        .duplicateDocument(documentId,destFolderId,this.$root.ownerId)
        .then( () => {
          this.refreshFiles();
          this.$root.$emit('show-alert', {type: 'success',message: this.$t('documents.alert.success.label.pasted')});
        }).catch(() => {
          this.$root.$emit('show-alert', {type: 'error',message: this.$t('documents.alert.error.label.pasted')});
        });
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
            view: this.recentViewSelected ? 'recentView': 'folderView',
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
        localStorage.removeItem('deletedDocument');
      }
    },
    bulkDownloadDocument(){
      this.setMultiActionLoading(true, 'download');
      const max = Math.floor(9999);
      const random = crypto.getRandomValues(new Uint32Array(1))[0];
      const actionId =random % max; 
      this.$root.$emit('open-download-drawer',actionId);
      localStorage.setItem(`bulkDownloadSourceName${actionId}`, eXo?.env?.portal?.spaceDisplayName || 'My Drive');
      return this.$documentFileService
        .bulkDownloadDocument(actionId,this.selectedDocuments)
        .catch(e => console.error(e));
    },
    bulkEditCategories(){
      this.setMultiActionLoading(true, 'categories');
      document.dispatchEvent(new CustomEvent('bulk-edit-categories-drawer-open', {detail: {
        objectType: 'document',
        objectIds: this.selectedDocuments.map(document => document.id),
        spaceId: eXo.env.portal.spaceId,
        summary1: this.$t('documents.bulk.categories.summary1'),
        summary2: this.$t('documents.bulk.categories.summary2'),
        summary3: this.$t('documents.bulk.categories.summary3'),
        categoryIds: [...new Set(this.selectedDocuments.flatMap(document => document.categoryIds))],
      }}));
    },
    cancelBulkAction(actionId){
      return this.$documentFileService
        .cancelBulkAction(actionId)
        .catch(e => console.error(e));
    },
    redirectToSpacePath(space, folder) {
      const folderPath = folder.path.includes('/Documents/') ? this.splitAtFirstOccurrence(folder.path,'/Documents/')[1] : '';
      const pathName = `${window.location.origin}/${eXo.env.portal.containerName}/g/`;
      const spaceGroup = space.groupId.replaceAll('/', ':');
      window.setTimeout(() => {
        window.location.href = `${pathName + spaceGroup }/${space.prettyName}/documents/${folderPath}`;
      }, 1000);
    },
    bulkMoveDocument(destPath, folder, space){
      const max = Math.floor(9999);
      const random = crypto.getRandomValues(new Uint32Array(1))[0];
      const actionId =random % max;
      this.setMultiActionLoading(true, 'move');
      sessionStorage.setItem('folder', JSON.stringify(folder));
      sessionStorage.setItem('space', JSON.stringify(space));
      return this.$documentFileService
        .bulkMoveDocuments(actionId,this.selectedDocuments, this.$root.ownerId, destPath)
        .catch(e => console.error(e));
    },
    openFile(file) {
      this.$attachmentService.getDocumentDetails(file.parentFolderId,'').then(folder => {
        this.openFolder(folder);
      });
    },
    openFolder(parentFolder) {
      this.$root.driveView = false;
      if (parentFolder.spaceId) {       
        this.$root.spaceId = parentFolder.spaceId;
      }
      if (parentFolder.identityId) {
        this.$root.ownerId = parentFolder.identityId;
      }
      this.folderPath = '';
      this.fileName = null;
      this.parentFolderId = parentFolder.drive ? null : parentFolder.id;
      let symlinkId = null;
      if (parentFolder.sourceID){
        symlinkId  = parentFolder.drive ? null : parentFolder.id;
        this.parentFolderId = parentFolder.sourceID; 
      }
      this.files = [];
      this.refreshFiles({'symlinkId': symlinkId});

      this.$root.$emit('set-breadcrumb', parentFolder);
      let folderPath ='';
      if (eXo.env.portal.spaceName) {
        let newParentPath = parentFolder.path;
        if (newParentPath.includes(`/spaces/${eXo.env.portal.spaceGroup}`)) {
          newParentPath = newParentPath.replace(`/spaces/${eXo.env.portal.spaceGroup}`, `/spaces/${eXo.env.portal.spaceGroup}/${eXo.env.portal.spaceName}`);
          const nodeUri = eXo.env.portal.selectedNodeUri.replace('/documents', '/Documents');
          const appPageName = nodeUri.replace(`/${eXo.env.portal.spaceGroup}`,'');
          if (appPageName!=='Documents'){
            newParentPath = newParentPath.replace('Documents', appPageName);
          }
          let pathParts = this.splitAtFirstOccurrence(newParentPath,nodeUri);
          if (pathParts.length>1){
            folderPath = pathParts[1];
          }
          pathParts = window.location.pathname.split(eXo.env.portal.selectedNodeUri);
          window.history.pushState(parentFolder.name, parentFolder.title, `${pathParts[0]}${eXo.env.portal.selectedNodeUri}${folderPath}?view=folder`);
        } else {
          // folder path doesn't contain the space group Id
          // folder is sub folder of a symlink from another space, we can not build the url from the folder path
          window.history.pushState(parentFolder.name, parentFolder.title, `${window.location.pathname}/${parentFolder.name}?view=folder`);
        }
      } else {
        const userName = eXo.env.portal.userName;
        const userPrivatePathPrefix = `${userName}/Private`;
        const userPublicPathPrefix = `${userName}/Public`;
        if (parentFolder.path?.includes(userPrivatePathPrefix)){
          const pathParts = this.splitAtFirstOccurrence(parentFolder.path,userPrivatePathPrefix);
          if (pathParts.length > 1){
            folderPath = pathParts[1];
          }
          
          window.history.pushState(parentFolder.name, parentFolder.title, `${window.location.pathname.split('/Private')[0]}/Private${folderPath}?view=folder`);
        }
        if (parentFolder.path?.includes(userPublicPathPrefix)){
          const pathParts = this.splitAtFirstOccurrence(parentFolder.path,userPublicPathPrefix);
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
      if (this.$root.driveView) {
        this.$root.$emit('load-more-drives');
        return;
      }
      this.refreshFiles({'primaryFilter': this.primaryFilter, 'append': true});
    },
    resetSelections() {
      this.$root.$emit('reset-selections');
      this.selectedDocuments = [];
    },
    changeView(view) {
      const realPageUrlIndex = window.location.href.toLowerCase().indexOf(location.pathname.toLowerCase()) + location.pathname.length;
      let url = new URL(window.location.href.substring(0, realPageUrlIndex));
      if (view === 'timeline') {
        url = new URL(window.location.href.split(eXo.env.portal.selectedNodeUri)[0] + eXo.env.portal.selectedNodeUri);
      }
      const params = new URLSearchParams(document.location.search);
      params.set('view', view);
      if (view !== 'folder'){
        params.delete('folderId');
        this.$root.ownerId = !eXo.env.portal.spaceId ? eXo.env.portal.userIdentityId: this.$root.ownerId;
      }
      params.forEach((value, key) => { url.searchParams.append(key, value); });
      window.history.replaceState('documents', 'Documents', url.toString());
      this.selectedView = view;
      localStorage.setItem(eXo.env.portal.spaceId ? `lastView-${eXo.env.portal.spaceId}` : 'lastView', view);
      if (view !== 'folder') {
        this.parentFolderId = null;
        this.folderPath = null;
      }
      this.files = [];
      this.$root.$emit('resetSearch');
      this.resetSelections();
      this.primaryFilter='all';
      this.query=null;
      this.extendedSearch=false;
      this.$root.$emit('set-documents-search', { 'extended': this.extendedSearch, 'query': this.query});
      this.$root.$emit('set-documents-filter', 'All');
      this.$root.selectedCategoryId = null;
      this.checkDefaultViewOptions();
      const options = {'primaryFilter': this.primaryFilter};
      const deletedDocument = localStorage.getItem('deletedDocument');
      if (deletedDocument != null) {
        options.deleted=true;
        options.documentId=deletedDocument;
      }
      this.refreshFiles(options)
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
        ownerId: this.$root.ownerId,
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
      if (this.userId) {
        filter.userId = this.userId;
      } 
      if (this.fileType?.length>0) {
        filter.fileType = this.fileType.join();
      }  
      if (this.afterDate) {
        filter.afterDate = this.afterDate;
      }      
      if (this.beforeDate) {
        filter.beforeDate = this.beforeDate;
      }     
      if (this.minSize) {
        filter.minSize = this.minSize;
      }  
      if (this.maxSize) {
        filter.maxSize = this.maxSize;
      } 
      if (this.showHidden) {
        filter.showHiddenFiles = this.showHidden;
      }
      filter.favorites = this.isFavorites;
      const expand = this.selectedViewExtension.filePropertiesExpand || 'modifier,creator,owner,metadatas';
      this.offset = options?.append ? this.offset + this.pageSize : 0 ;
      this.limit = options?.append ? this.limit + this.pageSize : this.pageSize ;
      this.loading = true;
      this.$root.$emit('loading-documents', true);
      this.$root.$emit('set-documents-search', { 'extended': this.extendedSearch, 'query': this.query});

      return this.$documentFileService.getDocumentItems(filter, this.selectedCategoryIds, this.excludeCategoryIds, this.offset, this.limit + 1, expand)
        .then(files => {
          this.files = options?.append ? this.files.concat(files) : files ;
          this.files = [...new Map(this.files.map((item) => [item['id'], item])).values()];
          this.files = options?.deleted ? this.files.filter(this.isDocumentsToBeDeleted) : this.files;
          if (this.sortField === 'favorite'){
            this.files.sort((file1, file2) => {
              if (file1.favorite === false && file2.favorite === true) {
                return this.ascending ? -1 : 1;
              }
              if (file1.favorite === true && file2.favorite === false) {
                return this.ascending ? 1 : -1;
              }
              return 0;
            });
          }
          if (this.sortField === 'visibility'){
            this.files.sort((file1, file2) => {
              if (file1.acl.visibilityChoice > file2.acl.visibilityChoice) {
                return this.ascending ? -1 : 1;
              }
              if (file1.acl.visibilityChoice < file2.acl.visibilityChoice) {
                return this.ascending ? 1 : -1;
              }
              return 0;
            }); 
          }
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
        .finally(() => {
          this.loading = false;
          this.$root.$emit('loading-documents', false);
        });
          
    },
    isDocumentsToBeDeleted(doc) {
      return this.documentsToBeDeleted.find(documentId => documentId === doc.id) ? false : true;
    },
    checkDefaultViewOptions() {
      if (this.selectedView === 'folder') {
        this.sortField = 'name';
        this.ascending = true;
      } else if (this.recentViewSelected) {
        this.sortField = 'lastUpdated';
        this.ascending = false;
      }
    },
    refreshViewExtensions(event) {
      let extensions = extensionRegistry.loadExtensions(this.extensionApp, this.extensionType);
      let changed = false;
      if (event?.detail?.forceUpdate) {
        this.viewExtensions = {};
      }
      if (!this.$root.settings.enabledViewList || this.$root.settings.enabledViewList.length > 0)
      {
        extensions = extensions.filter(item => this.$root.settings.enabledViewList.includes(item.id));
      }
      extensions.forEach(extension => {
        if (extension.id && (!this.viewExtensions[extension.id] || this.viewExtensions[extension.id] !== extension)) {
          this.viewExtensions[extension.id] = extension;
          changed = true;
        }
      });
      // force update of attribute to re-render switch new extension id
      if (changed) {
        this.viewExtensions = Object.assign({}, this.viewExtensions);
        if (this.selectedView && !this.viewExtensions[this.selectedView]) {
          const lastView = localStorage.getItem(eXo.env.portal.spaceId ? `lastView-${eXo.env.portal.spaceId}` : 'lastView');
          if (lastView!=null && this.viewExtensions[lastView]){
            this.changeView(lastView) ;
          } else {
            this.changeView(this.$root.settings.defaultView);
          }
        }
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
      const i18nName = this.$t('documents.label.newfolder');
      this.$documentFileService.getNewName(this.$root.ownerId, this.parentFolderId, this.folderPath, i18nName)
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
      this.$documentFileService.createFolder(this.$root.ownerId,this.parentFolderId,this.folderPath,name)
        .then(createdFolder => {
          createdFolder.canAdd = this.canAdd;
          this.files.shift();
          this.files.unshift(createdFolder);
          this.$root.$emit('folder-created',createdFolder);
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
      this.$documentFileService.renameDocument(this.$root.ownerId,file.id,name)
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
    moveDocument(file, destPath, destFolder, space, conflictAction) {
      this.$documentFileService.moveDocument(this.$root.ownerId, file.id, destPath, conflictAction)
        .then( () => {
          this.displayMessage({
            type: 'success',
            message: file.folder ? this.$t('document.alert.success.label.moveFolder') : this.$t('document.alert.success.label.moveDocument')
          });
          if (this.recentViewSelected) {
            const folderPath = eXo.env.portal.spaceName && destFolder.path.includes('/Documents/') ? this.splitAtFirstOccurrence(destFolder.path,'/Documents/')[1] : destFolder.path.substring(destFolder.path.indexOf('Private/'));
            window.setTimeout(() => {
              window.location.href = `${window.location.pathname}/${folderPath}?view=folder`;
            }, 1000);
          } else {
            this.openFolder(destFolder);
          }
          this.$root.$emit('document-moved');
        })
        .catch(e => {
          if (e.status === 409) {
            e.json().then(response => {
              this.$root.$emit('show-alert', {
                type: 'warning',
                message: this.getConflictMessage(file),
                actions: this.getConflictActions(response.existingObject, {
                  name: 'moveDocument',
                  params: [this.$root.ownerId, file, destPath, destFolder, space] // moveDocument function arguments
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
          const isShortcutToDifferentSpace = space?.id && eXo.env.portal.spaceId !== space.id;
          if (this.recentViewSelected) {
            if (isShortcutToDifferentSpace) {
              this.redirectTodestinationSpace(destFolder,space);
            } else {
              const folderPath = eXo.env.portal.spaceName && destFolder.path.includes('/Documents') ? this.splitAtFirstOccurrence(destFolder.path,'/Documents')[1] : destFolder.path.substring(destFolder.path.indexOf('/Private/'));
              window.location.href = `${window.location.pathname}/${folderPath}?view=folder`;
            }
          } else {
            if (isShortcutToDifferentSpace) {
              this.redirectTodestinationSpace(destFolder,space);
            } else {
              this.openFolder(destFolder);
            }
          }
          this.$root.$emit('shortcut-created');
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
            view: this.recentViewSelected ? 'recentView': 'folderView',
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
            view: this.recentViewSelected ? 'recentView': 'folderView',
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
            view: this.recentViewSelected ? 'recentView': 'folderView',
          },
          timestamp: Date.now()
        }
      }));
    },
    saveVisibility(file, publicLinkEnabled, publicOptions) {
      this.$documentFileService.saveVisibility(file)
        .then(() => {
          if (publicLinkEnabled && publicOptions) {
            return this.$documentFileService.createDocumentPublicAccess(file.id, {
              password: publicOptions.password,
              expirationDate: publicOptions.expirationDate || 0,
              hasPassword: publicOptions.hasPassword,
            });
          } else if (!publicLinkEnabled) {
            return this.$documentFileService.revokeDocumentPublicAccess(file.id);
          }
        })
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
        });
    },
    openDrawer(files) {

      const attachmentAppConfiguration = {
        'sourceApp': 'NEW.APP'
      };
      if (!eXo.env.portal.spaceName && this.$root.ownerId !== eXo.env.portal.userIdentityId){
        const drive = this.drives.find(drive => drive.identityId === this.$root.ownerId);
        if (drive) {
          attachmentAppConfiguration.defaultDrive = {
            isSelected: true,
            name: `.spaces.${drive.groupId}`,
            title: drive.name,
          };
          attachmentAppConfiguration.spaceId = drive.id;
          if (files){
            attachmentAppConfiguration.files=files;
          }
          if (this.parentFolderId) {
            this.$attachmentService.getDocumentDetails(this.parentFolderId,'')
              .then(folder => {
                attachmentAppConfiguration.defaultFolder = folder?.path.substring(folder.path.indexOf('Documents') + '/Documents'.length);
                document.dispatchEvent(new CustomEvent('open-attachments-app-drawer', {detail: attachmentAppConfiguration}));
              });
          } else {
            document.dispatchEvent(new CustomEvent('open-attachments-app-drawer', {detail: attachmentAppConfiguration}));
          }
        }
      } else {
        document.dispatchEvent(new CustomEvent('open-attachments-app-drawer', { detail: {
          'sourceApp': 'NEW.APP',
          defaultDrive: this.$root.selectedDrive,
          defaultFolder: this.$root.selectedPath.indexOf(this.$root.selectedDrive?.path) !== -1 ? this.splitAtFirstOccurrence(this.$root.selectedPath,this.$root.selectedDrive?.path)[1] : this.$root.selectedPath,
          files,
        }}));
      }
    },
    setCurrentFolder(folder) {
      this.currentFolder = folder;
      this.canAdd = this.spaceCanAdd || folder?.accessList?.canEdit;
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
        this.showHidden = true;
        return this.showPreview(documentPreviewId)
          .then(attachment => {
            if (attachment?.path) {
              this.openFile(attachment);
            }
          });
      }
      if (queryParams.has('ownerId')) {
        this.$root.ownerId = queryParams.get('ownerId');
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
        const pathParts  = path.toLowerCase().includes('/drives') || path.toLowerCase().includes('/documents')? this.splitAtFirstOccurrence(path,`${eXo.env.portal.selectedNodeUri.toLowerCase()}/`) : [path];
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
            if (this.recentViewSelected){
              this.parentFolderId = null;
              this.folderPath = null;
            }
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
      this.$root.$emit('alert-message', message.message, message.type);
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
      return this.$attachmentService.getDocumentDetails(version.originId)
        .then(attachment => {
          return this.$attachmentService.getDocumentDetails(version.frozenId)
            .then(file => {
              file.downloadUrl =`/rest/jcr/repository/collaboration${attachment.path}?version=${version.versionNumber}`;
              file.path = attachment.path;
              file.mimeType = attachment.mimeType;
              file.filename = attachment.name;
              file.source = 'documents';
              if (this.isFileReadable(attachment)){
                this.openFileInEditor(file,'view');
              } else {
                const versionFile = {'id': file.id,'filename': attachment.name,'mimetype': attachment.mimeType,'source': 'documents','downloadUrl': file.downloadUrl, 'icon': this.getFileIcon(attachment)};
                document.dispatchEvent(new CustomEvent('open-attachments-preview', {detail: {'attachments': [versionFile],'id': file.id }}));
                return attachment;}
            });
        })
        .catch(e => console.error(e))
        .finally(() => this.loading = false);
    },
    showPreview(documentPreviewId) {
      return this.$attachmentService.getDocumentDetails(documentPreviewId,'')
        .then(attachment => {
          if (this.isFileReadable(attachment)){
            if (attachment?.acl?.canEdit && this.isFileEditable(attachment)){
              this.openFileInEditor(attachment,'edit','_self');
            } else {
              this.openFileInEditor(attachment,'view','_self');

            }
          } else {
            const file = {'id': attachment.id,'filename': attachment.name,'mimetype': attachment.mimeType,'source': 'documents','downloadUrl': `/rest/v1/documents/content/${attachment.id}`, 'icon': this.getFileIcon(attachment)};
            document.dispatchEvent(new CustomEvent('open-attachments-preview', {detail: {'attachments': [file],'id': documentPreviewId }}));
            return attachment;
          }
        })
        .catch(e => console.error(e))
        .finally(() => this.loading = false);
    },
    isFileEditable(file) {
      return  this.$supportedDocuments && this.$supportedDocuments.filter(doc => doc.edit && doc.mimeType === file.mimeType ).length > 0;
    },
    isFileReadable(file) {
      return this.$supportedDocuments && this.$supportedDocuments.filter(doc => doc.mimeType === file.mimeType).length > 0;
    },
    openFileInEditor(attachment,mode,tab) {
      if (attachment && attachment.id) {
        const url = this.$documentsUtils.getEditorUrl(attachment,mode);
        window.open(url,tab ? tab:'_blank');
      }
    },
    async dragFile(e){     
      if (this.canAdd) {
        const items = e.dataTransfer.items;
        const filesArray = [];
        const entryPromises = [];
        for (let i = 0; i < items.length; i++) {
          const item = items[i].webkitGetAsEntry?.();
          if (item) {
            entryPromises.push(this.getFilesList(item, '', filesArray));
          }
        }
        await Promise.all(entryPromises);
        this.openDrawer(filesArray);
      }
      this.showOverlay = false;
      this.$root.$emit('hide-upload-overlay');
    },
    startDrag(){
      this.showOverlay = true;
      if (this.canAdd) {
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
        this.spaceCanAdd = true;
        this.canAdd = true;
      } else {
        this.$documentFileService.canAddDocument(spaceId)
          .then(canAdd => {
            this.spaceCanAdd = canAdd;
            this.canAdd = canAdd || this.canEditCurrentFolder;
          });
      }
    },
    redirectTodestinationSpace(destFolder, space) {
      const folderPath = this.splitAtFirstOccurrence(destFolder.path,`Groups${space.groupId}`)[1].replace('/Documents', '/documents');
      const pathName = `${eXo.env.portal.context}/g/${space.groupId.replaceAll('/', ':')}/${space.prettyName}`;
      window.location.replace(`${pathName}${folderPath}?view=folder`, window.location.pathname);
    },
    displayAddMenuMobile() {
      this.$refs.documentAddItemMenu.open();
    }, 
    hideAddMenuMobile() {
      this.$refs.documentAddItemMenu.close();
    },
    previewDocument(file) {
      const files = [];
      this.files.forEach((item) => {
        if (!item.folder && Vue.prototype?.$supportedDocuments.filter(doc =>doc.mimeType === item.mimeType).length === 0){
          files.push({'id': item.id,'filename': item.name,'mimetype': item.mimeType,'source': 'documents','downloadUrl': `/rest/v1/documents/content/${item.id}`, 'icon': this.getFileIcon(item)});}
      }
      );
      document.dispatchEvent(new CustomEvent('open-attachments-preview', {detail: {'attachments': files,'id': file.id }}));
    },
    getFileIcon(file) {
      const extensions = this.$documentsIconsExtension;
      if (file?.folder) {
        return extensions[0].get('folder');
      } else {
        let extension = extensions[0].get(file?.mimeType);
        if (!extension) {
          extension = extensions[0].get('file');
        }
        return extension;
      }
    },
    resetMultiSelection() {
      this.setMultiActionLoading(false);
      this.resetSelections();
    },
    refreshDocument(event) {
      const detail = event.detail;
      if (detail?.objectType === 'document' &&  detail?.objectIds.length) {
        this.refreshFiles();
        this.setMultiActionLoading(false);
        this.resetSelections();
      } else if (detail?.objectType === 'document' && this.files.some(file => file.id === detail?.objectId)) {
        if (this.selectedCategoryIds.length) {
          this.refreshFiles();
        } else {
          return this.$documentFileService.getDocumentById(detail?.objectId)
            .then(file => {
              const index = this.files.findIndex(f => f.id === file.id);
              if (index !== -1) {
                this.files.splice(index, 1, file);
              }
            });
        }
      }
    },
    async getFilesList(entry, path, result) {
      path = path || '';
      if (entry.isFile) {
        const file = await new Promise((resolve) => {
          entry.file(resolve);
        });
        const folderPath = path.endsWith('/') ? path.slice(0, -1) : path;
        file.destinationFolder=folderPath;
        result.push(file);
      } else if (entry.isDirectory) {
        const reader = entry.createReader();
        const entries = await new Promise((resolve) => {
          reader.readEntries(resolve);
        });
        const promises = entries.map(child =>
          this.getFilesList(child, `${path}${entry.name}/`, result)
        );
        await Promise.all(promises);
      }
    },
    searchDrives(query) {
      this.$root.driveView = true;
      this.$root.$emit('search-drives', query);
    },
    showDrives(treeChildren){
      this.$root.driveView = true;
      this.hasMore = treeChildren.some(item => item.id === 'load_more');
      this.files= treeChildren.filter(item => item.id !== 'load_more');
    },
    applyViewType(viewType) {
      this.viewType = viewType;
      this.refreshFiles();
      this.$nextTick().then(() => this.$root.$emit('update-breadcrumb', this.folderPath));
    },
    splitAtFirstOccurrence(str, delimiter) {
      const index = str.indexOf(delimiter);
      if (index === -1) {return [str];}
      return [
        str.slice(0, index),
        str.slice(index + delimiter.length)
      ];
    },
  },
};
</script>
