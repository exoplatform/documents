<!--
 This file is part of the Meeds project (https://meeds.io/).

 Copyright (C) 2020 - 2025 Meeds Association contact@meeds.io

 This program is free software; you can redistribute it and/or
 modify it under the terms of the GNU Lesser General Public
 License as published by the Free Software Foundation; either
 version 3 of the License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU Lesser General Public License for more details.

 You should have received a copy of the GNU Lesser General Public License
 along with this program; if not, see <http://www.gnu.org/licenses/>.
-->
<template>
  <exo-drawer
    ref="driveExplorerDrawer"
    class="driveExplorerDrawer"
    right
    :use-filter="!!currentDrive"
    :filter-placeholder="$t('attachments.drawer.search')"
    @filter-updated="onHeaderSearch">
    <!-- Standard exo-drawer header: an explicit back arrow (returns to the
         level-1 attachments drawer underneath) + title + close button. Search
         reuses exo-drawer's built-in use-filter, which turns the header title
         itself into an inline text field (the platform-wide drawer search
         pattern) and drives the same server-side `query` param. -->
    <template slot="title">
      <div class="d-flex align-center">
        <v-btn
          icon
          small
          class="ms-n2 me-1 flex-shrink-0"
          :aria-label="$t('attachments.drawer.back')"
          :title="$t('attachments.drawer.back')"
          @click="closeAttachmentsDriveExplorerDrawer()">
          <v-icon size="20">fa-arrow-left</v-icon>
        </v-btn>
        <span class="text-truncate">{{ driveExplorerDrawerTitle }}</span>
      </div>
    </template>
    <template slot="content">
      <div class="serverFiles pt-0 pa-3" @click="closeFolderActionsMenu">
        <div class="contentHeader border-bottom-color d-flex align-center pb-2 ma-3">
          <div class="currentDirectory d-flex align-center mr-2">
            <!-- Nav-toggle: opens the reused FolderTreeViewDrawer. Rendered
                 identically to the breadcrumb back button (fa-angle-left): same
                 v-btn/v-icon markup + classes, and the same wrapper padding
                 (documents-breadcrumb-mobile uses pa-1 mb-1 ps-0) so both icons
                 sit on the same line, same size/padding/baseline. -->
            <div class="d-flex align-center pa-1 mb-1 ps-0">
              <v-btn
                icon
                small
                class="ms-n1 me-1 flex-shrink-0"
                :title="$t('attachments.drawer.drives')"
                @click="openNavigationTree()">
                <v-icon size="18">fas fa-columns</v-icon>
              </v-btn>
            </div>
            <!-- Reused Documents breadcrumb, forced to its mobile branch
                 (fa-angle-left to the parent + current folder / drive name). Its
                 own tree toggle is hidden (show-icon=false); the panel icon above
                 opens the nav tree. -->
            <documents-breadcrumb
              v-if="currentDrive"
              :show-icon="false"
              :is-mobile="true"
              class="attachments-drive-breadcrumb" />
          </div>
          <div class="selectorActions d-flex align-center">
            <a
              v-if="modeFolderSelection && currentDrive"
              :title="$t('attachments.filesFoldersSelector.button.addNewFOlder.tooltip')"
              rel="tooltip"
              data-placement="bottom"
              class="uiIconLightGray uiIconAddFolder"
              @click="addNewFolder()"></a>
          </div>
          <!-- Action buttons for extensionRegistry extensions -->
          <div
            v-for="action in attachmentsComposerActions"
            :key="action.key"
            :class="`${action.appClass}Action`"
            class="actionBox ml-1 align-center">
            <div
              v-if="!modeFolderSelection"
              class="actionBoxLogo"
              @click="executeAction(action)">
              <v-icon v-if="action.iconName" class="uiActionIcon pa-2">
                {{ action.iconName }}
              </v-icon>
              <i
                v-else
                :class="action.iconClass"
                class="uiActionIcon"></i>
            </div>
          </div>
          <!-- end of action buttons block -->
        </div>

        <transition name="fade" mode="in-out">
          <div v-show="showErrorMessage" class="alert foldersFilesSelectorAlert alert-error mx-auto">
            <i class="uiIconError"></i>{{ errorMessage }}
          </div>
        </transition>

        <div class="contentBody">
          <!-- Drive browsing (a drive is opened) -->
          <div v-if="currentDrive" class="selectionBox">
            <div v-if="loading && !browseItems.length" class="loader flex-column d-flex align-center">
              <v-progress-circular
                :size="30"
                :width="3"
                indeterminate
                class="loadingRing"
                color="#476A9C" />
            </div>
            <div
              v-else
              class="content-explorer px-3">
              <!-- Inline folder create / rename (destination mode) - kept as
                   lightweight inputs above the list to preserve folder CRUD. -->
              <div v-if="creatingNewFolder" class="newFolderRow d-flex align-center px-3 pb-2">
                <i class="uiIcon24x24nt_folder uiIcon24x24FolderDefault uiIconEcmsLightGray me-2"></i>
                <input
                  ref="newFolder"
                  v-model="newFolderName"
                  type="text"
                  class="newFolderInput ignore-vuetify-classes"
                  @click.stop
                  @blur="createNewFolder()"
                  @keyup.enter="$event.target.blur()"
                  @keyup.esc="cancelCreatingNewFolder($event)">
              </div>
              <div v-else-if="renameFolderAction" class="renameFolderRow d-flex align-center px-3 pb-2">
                <i class="uiIcon24x24nt_folder uiIcon24x24FolderDefault uiIconEcmsLightGray me-2"></i>
                <input
                  ref="rename"
                  v-model="newName"
                  type="text"
                  class="newFolderInput ignore-vuetify-classes"
                  @click.stop
                  @blur="saveNewNameFolder()"
                  @keyup.enter="$event.target.blur()"
                  @keyup.esc="cancelRenameNewFolder($event)">
              </div>
              <div v-if="emptyFolder" class="emptyFolder my-10 mx-auto flex-column d-flex align-center">
                <i class="uiIconEmptyFolder"></i>
                <p>{{ $t('attachments.drawer.destination.folder.empty') }}</p>
              </div>
              <!-- Very simple rows: [checkbox (files only)] [icon] [name], all
                   left-aligned and vertically centered; no date, no dividers, no
                   badge / 3-dots. Folders drill on click; files toggle selection
                   feeding the preserved attachments-changed-from-drives contract. -->
              <div v-else class="drive-explorer-list">
                <div
                  v-for="item in listItems"
                  :key="item.id"
                  :class="{ 'selected-row': isRowSelected(item) }"
                  class="drive-explorer-row d-flex align-center"
                  @click="onRowClick(item)"
                  @contextmenu="onRowContextMenu(item, $event)">
                  <v-simple-checkbox
                    v-if="!modeFolderSelection && !item.folder"
                    :value="isRowSelected(item)"
                    color="primary"
                    class="drive-explorer-row-check flex-shrink-0 my-0 me-1"
                    @input="toggleFileSelection(item)"
                    @click.stop />
                  <v-icon
                    :color="rowIconColor(item)"
                    size="20"
                    class="drive-explorer-row-icon flex-shrink-0 me-2">
                    {{ rowIconClass(item) }}
                  </v-icon>
                  <span class="drive-explorer-row-name text-truncate">{{ item.name }}</span>
                </div>
                <div v-if="hasMore" class="d-flex justify-center py-2">
                  <v-btn
                    text
                    small
                    :loading="loading"
                    class="primary--text"
                    @click="loadMore()">
                    {{ $t('attachments.drawer.loadMore') }}
                  </v-btn>
                </div>
              </div>
              <attachments-folder-actions-menu
                ref="folderActionsMenu"
                :folder-actions-menu-left="folderActionsMenuLeft"
                :folder-actions-menu-top="folderActionsMenuTop"
                :selected-folder="selectedFolder"
                @renameFolder="renameFolder()"
                @deleteFolder="deleteFolder"
                @closeMenu="closeFolderActionsMenu" />
            </div>
          </div>
          <!-- No drive selected yet: prompt to pick one via the nav tree. -->
          <div v-else class="emptyFolder my-10 mx-auto flex-column d-flex align-center">
            <i class="uiIconEmptyFolder"></i>
            <p>{{ $t('attachments.drawer.destination.folder.empty') }}</p>
          </div>
        </div>
      </div>
      <exo-confirm-dialog
        ref="confirmDialog"
        :title="titleLabel"
        :message="popupBodyMessage"
        :ok-label="okLabel"
        :cancel-label="cancelLabel"
        @ok="okConfirmDialog" />
      <!-- Reused Documents navigation drawer (drives / spaces / folder tree).
           Opened by the panel icon; selecting a folder closes it and loads the
           selected location via the 'open-folder' event handled below. -->
      <folder-treeview-drawer :folder-path="folderPath" />
    </template>
    <template slot="footer">
      <div class="d-flex">
        <v-spacer />
        <v-btn
          class="btn mr-3"
          @click="closeAttachmentsDriveExplorerDrawer()">
          {{ $t('attachments.drawer.cancel') }}
        </v-btn>
        <v-btn
          class="btn btn-primary"
          :disabled="!selectFromDrivesEnabled"
          @click="selectActionDriveExplorerDrawer()">
          {{ $t('attachments.drawer.select') }}
        </v-btn>
      </div>
    </template>
  </exo-drawer>
</template>

<script>
import {getAttachmentsComposerExtensions, executeExtensionAction} from '../../../../js/extension';

export default {
  props: {
    spaceId: {
      type: String,
      default: ''
    },
    attachedFiles: {
      type: Array,
      default: () => []
    },
    extensionRefs: { // references to extension dynamic components
      type: Array,
      default: () => []
    },
    entityId: {
      type: String,
      default: ''
    },
    entityType: {
      type: String,
      default: ''
    },
    defaultDrive: {
      type: Object,
      default: () => null
    },
    defaultFolder: {
      type: String,
      default: ''
    },
    createEntityTypeFolder: {
      type: Boolean,
      default: true
    }
  },
  data() {
    return {
      // Drives model (two sources: My Documents + My Spaces)
      drives: [],
      loadingDrives: false,
      loadingMoreSpaces: false,
      spacesOffset: 0,
      spacesLimit: 20,
      spacesHasMore: false,
      // Current drive + folder browsing state
      currentDrive: null,
      breadcrumb: [], // result of /v1/documents/breadcrumb (index 0 = drive root)
      parentFolderId: null,
      folderPath: '',
      browseItems: [], // documents (folders + files) of the current folder
      // Pagination
      pageSize: 20,
      offset: 0,
      limit: 20,
      hasMore: false,
      loading: false,
      // Sort (server-side; fixed name-ascending for the picker)
      sortField: 'name',
      ascending: true,
      // Search (driven by the exo-drawer header filter field)
      query: '',
      searchDebounce: null,
      // Selection (existing files -> attachments)
      selectedFiles: [],
      removedFiles: [],
      maxFilesCount: 20,
      filesCountClass: '',
      // Modes
      modeFolderSelection: true, // destination folder picker
      modeFolderSelectionForFile: false, // destination for a single moved file
      movedFile: {},
      // Folder CRUD
      creatingNewFolder: false,
      newFolderName: '',
      renameFolderAction: false,
      newName: '',
      selectedFolder: {},
      folderActionsMenuTop: 0,
      folderActionsMenuLeft: 0,
      // Confirm dialog
      titleLabel: '',
      okLabel: '',
      cancelLabel: '',
      popupBodyMessage: '',
      okAction: false,
      // Errors
      showErrorMessage: false,
      errorMessage: '',
      MESSAGE_TIMEOUT: 5000,
      // Extensions
      attachmentsComposerActions: [],
      // Infinite scroll
      scrollObserver: null,
      // Default destination model (upload bootstrap)
      defaultDriveModel: null,
    };
  },
  computed: {
    // All items reported by the server that are folders (or drives).
    folderItems() {
      return this.browseItems.filter(item => item.folder || item.drive);
    },
    // All items reported by the server that are plain files.
    fileItems() {
      return this.browseItems.filter(item => !item.folder && !item.drive);
    },
    // Rows rendered: folders (for navigation) plus, in "select existing files"
    // mode, the files. These are the raw browse items (already name-decoded).
    listItems() {
      if (this.modeFolderSelection) {
        return this.folderItems;
      }
      return this.folderItems.concat(this.fileItems);
    },
    selectedFilesCount() {
      return (this.attachedFiles.length + this.selectedFiles.length) - this.removedFiles.length;
    },
    filesCountLeft() {
      return this.maxFilesCount - this.selectedFilesCount;
    },
    emptyFolder() {
      return !this.loading && this.listItems.length === 0;
    },
    driveExplorerDrawerTitle() {
      return this.modeFolderSelection ? this.$t('attachments.drawer.destination.folder') : this.$t('attachments.drawer.existingUploads');
    },
    isSelectedFromDrivesFiles() {
      return this.selectedFiles && !!this.selectedFiles.length;
    },
    isRemovedFromDrivesFiles() {
      return this.removedFiles && !!this.removedFiles.length;
    },
    selectFromDrivesEnabled() {
      return this.isSelectedFromDrivesFiles || this.isRemovedFromDrivesFiles || this.modeFolderSelection;
    }
  },
  watch: {
    filesCountLeft() {
      this.filesCountClass = this.filesCountLeft === 0 ? 'noFilesLeft' : '';
    },
    showErrorMessage(newVal) {
      if (newVal) {
        setTimeout(() => this.showErrorMessage = false, this.MESSAGE_TIMEOUT);
      }
    },
    entityId() {
      this.initDestinationFolderPath();
    },
    defaultFolder() {
      this.initDestinationFolderPath();
    },
    defaultDrive() {
      this.initDestinationFolderPath();
    },
  },
  created() {
    this.initDestinationFolderPath();
    document.addEventListener('extension-AttachmentsComposer-attachments-composer-action-updated', () => this.attachmentsComposerActions = getAttachmentsComposerExtensions());
    this.attachmentsComposerActions = getAttachmentsComposerExtensions();
    this.$root.$on('open-drive-explorer-drawer', () => {
      this.modeFolderSelection = true;
      this.modeFolderSelectionForFile = false;
      this.openAttachmentsDriveExplorerDrawer();
    });
    this.$root.$on('open-select-from-drives-drawer', () => this.openSelectFromDrivesDrawer());
    this.$root.$on('change-attachment-destination-path', (file) => {
      this.modeFolderSelection = true;
      this.modeFolderSelectionForFile = true;
      this.movedFile = file;
      this.openAttachmentsDriveExplorerDrawer();
    });
    // The reused breadcrumb brokers navigation: nav-tree selection ('open-folder')
    // and breadcrumb crumb / angle-left clicks are re-emitted as
    // 'document-open-folder' (a folder / drive node) or 'document-open-home' (the
    // personal root) -> load that location in the list.
    this.$root.$on('document-open-folder', this.handleDocumentOpenFolder);
    this.$root.$on('document-open-home', this.handleDocumentOpenHome);
  },
  beforeDestroy() {
    this.$root.$off('document-open-folder', this.handleDocumentOpenFolder);
    this.$root.$off('document-open-home', this.handleDocumentOpenHome);
  },
  methods: {
    // ---------------------------------------------------------------------
    // Drawer lifecycle
    // ---------------------------------------------------------------------
    // Opens the level-2 drawer and shows the initial view (a drive or the drive list).
    openAttachmentsDriveExplorerDrawer() {
      this.resetSearch();
      this.resetSelection();
      if (this.spaceId) {
        // A space context is known -> auto-open that space's drive
        this.openSpaceDrive(this.spaceId);
      } else if (this.defaultDrive && this.defaultDriveModel && this.defaultDriveModel.ownerId) {
        // A default drive was provided by the caller -> open it
        this.openDrive(this.defaultDriveModel);
      } else {
        // Generic case -> open My Documents; the panel icon switches drives.
        this.openMyDocuments();
      }
      this.$refs.driveExplorerDrawer.open();
    },
    // Resolves and opens a space's drive from its space id (via the drive list).
    openSpaceDrive(spaceId) {
      this.loadDrives().then(() => {
        const spaceDrive = this.drives.find(drive => drive.spaceId === spaceId);
        if (spaceDrive) {
          this.openDrive(spaceDrive);
        } else if (this.defaultDriveModel && this.defaultDriveModel.ownerId) {
          this.openDrive(this.defaultDriveModel);
        }
      });
    },
    // Opens the drawer in "select existing files" mode (multi-select of files).
    openSelectFromDrivesDrawer() {
      this.modeFolderSelection = false;
      this.modeFolderSelectionForFile = false;
      this.openAttachmentsDriveExplorerDrawer();
    },
    // Closes the drawer and clears the transient selection state.
    closeAttachmentsDriveExplorerDrawer() {
      this.resetSelection();
      this.disconnectScrollObserver();
      this.$refs.driveExplorerDrawer.close();
    },
    // True when the drawer is currently closed (used by the parent).
    isClosed() {
      return this.$refs.driveExplorerDrawer.$el.classList.contains('v-navigation-drawer--close');
    },
    // ---------------------------------------------------------------------
    // Drive navigation (reused Documents nav tree)
    // ---------------------------------------------------------------------
    // Opens the reused navigation drawer at the current location. The current
    // breadcrumb (enriched with the owner identity so the tree can locate the
    // space-drive node) is passed THROUGH the open event, so the tree re-emits it
    // after its lazily-created content + root data are ready (fixes the lost-event
    // timing) and expands the drive + ancestors down to the current folder.
    openNavigationTree() {
      const ownerId = this.currentDrive && this.currentDrive.ownerId;
      const enriched = (this.breadcrumb || []).map(crumb => Object.assign({}, crumb, {identityId: crumb.identityId || ownerId}));
      this.$root.$emit('openTreeFolderDrawer', false, enriched);
    },
    // Opens the personal "My Documents" drive at its root.
    openMyDocuments() {
      this.openDrive({
        name: 'Personal Documents',
        title: this.$t('attachments.drawer.myDocuments'),
        ownerId: eXo.env.portal.userIdentityId,
        spaceId: null,
        avatarUrl: null,
      });
    },
    // Breadcrumb re-emits 'document-open-home' for the personal root (Private).
    handleDocumentOpenHome() {
      this.openMyDocuments();
    },
    // ".spaces.<group>" ECMS drive name from a space groupId (else Personal).
    driveNameFromGroupId(groupId) {
      if (!groupId) {
        return 'Personal Documents';
      }
      const group = groupId.includes('/spaces/') ? groupId.split('/spaces/')[1] : groupId.replace(/^\//, '');
      return `.spaces.${group}`;
    },
    // Builds "My Documents" and fetches the first page of "My Spaces".
    loadDrives() {
      this.loadingDrives = true;
      this.drives = [{
        driverType: 'My Documents',
        name: 'Personal Documents',
        title: this.$t('attachments.drawer.myDocuments'),
        ownerId: eXo.env.portal.userIdentityId,
        avatarUrl: null,
        drive: true,
      }];
      this.spacesOffset = 0;
      return this.fetchSpaces(false).finally(() => this.loadingDrives = false);
    },
    // Fetches a page of member spaces and maps each to a drive descriptor.
    fetchSpaces(append) {
      return this.$spaceService.getSpaces(this.query || null, this.spacesOffset, this.spacesLimit, 'member', 'identity')
        .then(data => {
          const spaces = (data && data.spaces) || [];
          const spaceDrives = spaces.filter(space => space && space.identity && space.identity.id).map(space => ({
            driverType: 'My Spaces',
            name: this.spaceDriveName(space),
            title: space.displayName,
            ownerId: space.identity.id,
            spaceId: space.id,
            avatarUrl: space.avatarUrl,
            drive: true,
          }));
          if (append) {
            this.drives = this.drives.concat(spaceDrives);
          } else {
            this.drives = this.drives.filter(drive => drive.driverType !== 'My Spaces').concat(spaceDrives);
          }
          const size = (data && data.size) || 0;
          this.spacesHasMore = (this.spacesOffset + this.spacesLimit) < size;
        }).catch(() => {
          this.errorMessage = this.$t('attachments.getDrivers.error');
          this.showErrorMessage = true;
        });
    },
    // Computes the legacy ECMS drive name (".spaces.<group>") for a space.
    spaceDriveName(space) {
      const groupId = space.groupId || '';
      const group = groupId.includes('/spaces/') ? groupId.split('/spaces/')[1] : groupId.replace(/^\//, '');
      return `.spaces.${group}`;
    },
    // ---------------------------------------------------------------------
    // Folder browsing (breadcrumb drill-down)
    // ---------------------------------------------------------------------
    // Opens a drive at its root folder.
    openDrive(drive) {
      if (!drive) {
        return this.openMyDocuments();
      }
      this.currentDrive = {
        name: drive.name,
        title: drive.title,
        ownerId: drive.ownerId,
        spaceId: drive.spaceId,
        avatarUrl: drive.avatarUrl,
      };
      this.parentFolderId = null;
      this.folderPath = '';
      this.resetSearch();
      return this.navigate();
    },
    // Handles a folder open from: a folder row click (mapped browse item), the
    // breadcrumb (crumb / angle-left click), or the nav tree (brokered by the
    // breadcrumb as 'document-open-folder'). A drive/space tree node (folder.drive)
    // opens that drive; otherwise we drill into the folder, syncing the drive
    // owner the tree may have changed (cross-drive jump).
    handleDocumentOpenFolder(folder) {
      if (!folder || this.creatingNewFolder || this.renameFolderAction) {
        return;
      }
      if (folder.drive) {
        return this.openDrive({
          name: this.driveNameFromGroupId(folder.groupId),
          title: folder.title || folder.name,
          ownerId: folder.identityId || folder.ownerId,
          spaceId: folder.spaceId,
          avatarUrl: folder.avatarUrl,
        });
      }
      // If the nav tree switched the owner to another drive, rebuild the drive
      // context from the folder path (destination is recomputed from breadcrumb).
      const treeOwner = this.$root.ownerId;
      if (treeOwner && treeOwner !== '0' && this.currentDrive && treeOwner !== this.currentDrive.ownerId && folder.path) {
        const isSpace = folder.path.startsWith('/Groups/spaces');
        this.currentDrive = {
          name: isSpace ? `.spaces.${folder.path.split('/').filter(segment => segment && segment.length)[2]}` : 'Personal Documents',
          title: isSpace ? folder.name : this.$t('attachments.drawer.myDocuments'),
          ownerId: treeOwner,
          spaceId: this.$root.spaceId,
          avatarUrl: null,
        };
      }
      const isDriveRoot = this.breadcrumb && this.breadcrumb.length && folder.id === this.breadcrumb[0].id;
      this.parentFolderId = isDriveRoot ? null : folder.id;
      this.folderPath = '';
      this.resetSearch();
      return this.navigate();
    },
    // Reloads the current folder contents (page 0) and the breadcrumb, and feeds
    // the reused breadcrumb the current folder so it renders/updates.
    navigate() {
      this.offset = 0;
      this.limit = this.pageSize;
      this.browseItems = [];
      this.$root.ownerId = this.currentDrive.ownerId;
      this.loadBreadcrumb().then(() => this.syncVisualBreadcrumb());
      return this.fetchDocuments(false);
    },
    // Feeds the reused documents-breadcrumb the current folder (it fetches its own
    // crumb chain from the last folder's id).
    syncVisualBreadcrumb() {
      const last = this.breadcrumb && this.breadcrumb.length ? this.breadcrumb[this.breadcrumb.length - 1] : null;
      if (last) {
        this.$root.$emit('set-breadcrumb', last);
      }
    },
    // Fetches the breadcrumb chain for the current folder.
    loadBreadcrumb() {
      return this.$documentFileService.getBreadCrumbs(this.parentFolderId, this.currentDrive.ownerId, this.folderPath)
        .then(breadcrumb => {
          this.breadcrumb = breadcrumb || [];
        }).catch(() => {
          this.breadcrumb = [];
        });
    },
    // Fetches (a page of) the current folder's documents via /v1/documents.
    // Sort mirrors the Documents app; the header search icon drives `query`.
    fetchDocuments(append) {
      const filter = {
        ownerId: this.currentDrive.ownerId,
        listingType: 'FOLDER',
        sortField: this.sortField || 'name',
        ascending: this.ascending,
        favorites: false,
      };
      if (this.parentFolderId) {
        filter.parentFolderId = this.parentFolderId;
      }
      if (this.folderPath) {
        filter.folderPath = this.folderPath;
      }
      if (this.query && this.query.trim().length) {
        filter.query = this.query.trim();
      }
      this.offset = append ? this.offset + this.pageSize : 0;
      this.limit = append ? this.limit + this.pageSize : this.pageSize;
      this.loading = true;
      // request one extra item to detect whether more pages exist
      return this.$documentFileService.getDocumentItems(filter, null, null, this.offset, this.limit + 1, 'owner')
        .then(items => {
          const documents = (items || []).map(item => this.mapDocument(item));
          this.hasMore = documents.length > this.limit;
          const pageItems = this.hasMore ? documents.slice(0, this.limit) : documents;
          this.browseItems = append ? this.dedupe(this.browseItems.concat(pageItems)) : pageItems;
        }).catch(error => {
          this.errorMessage = `${this.$t('attachments.fetchFoldersAndFiles.error')}. ${error && error.message ? error.message : ''}`;
          this.showErrorMessage = true;
        }).finally(() => this.loading = false);
    },
    // Loads the next page of the current folder (infinite scroll).
    loadMore() {
      if (this.loading || !this.hasMore) {
        return;
      }
      this.fetchDocuments(true);
    },
    // Maps a /v1/documents item to the shape used by this drawer.
    mapDocument(item) {
      let name = item.name;
      try {
        name = decodeURIComponent(item.name);
      } catch (e) {
        // keep the raw name when it is not a valid encoded string
      }
      return {
        id: item.id,
        name: name,
        folder: !!item.folder,
        drive: !!item.drive,
        mimetype: item.mimeType,
        sourceID: item.sourceID,
        modifiedDate: item.modifiedDate,
        lastModified: item.modifiedDate,
        size: item.size,
        path: item.path,
        downloadUrl: item.downloadUrl,
        acl: item.acl,
        canRemove: !!(item.acl && item.acl.canDelete) || !!(item.acl && item.acl.canEdit),
        isCloudFile: false,
        isSelected: this.attachedFiles.some(f => f.id === item.id) || this.selectedFiles.some(f => f.id === item.id),
      };
    },
    dedupe(items) {
      return [...new Map(items.map(item => [item.id, item])).values()];
    },
    // Human-readable label for a breadcrumb crumb.
    crumbLabel(crumb) {
      if (crumb.name === 'Private') {
        return this.$t('attachments.drawer.myDocuments');
      }
      if (crumb.name === 'Documents') {
        return this.currentDrive.title;
      }
      return crumb.name;
    },
    // ---------------------------------------------------------------------
    // Search
    // ---------------------------------------------------------------------
    // Header search: exo-drawer's built-in filter field emits 'filter-updated'
    // on each keystroke -> debounced server-side search of the current folder.
    onHeaderSearch(text) {
      this.query = text || '';
      if (this.searchDebounce) {
        clearTimeout(this.searchDebounce);
      }
      this.searchDebounce = setTimeout(() => this.triggerSearch(), 400);
    },
    triggerSearch() {
      if (this.currentDrive) {
        this.offset = 0;
        this.limit = this.pageSize;
        this.browseItems = [];
        this.fetchDocuments(false);
      } else {
        this.spacesOffset = 0;
        this.fetchSpaces(false);
      }
    },
    resetSearch() {
      this.query = '';
    },
    // ---------------------------------------------------------------------
    // File selection (-> attachments-changed-from-drives)
    // ---------------------------------------------------------------------
    // Adds a file to the picked set feeding the (unchanged)
    // attachments-changed-from-drives emit contract (called via the list-view
    // selection cell's 'update-selection-documents-list' event).
    onFileSelected(cardFile) {
      const original = this.fileItems.find(f => f.id === cardFile.id);
      if (!original) {
        return;
      }
      const alreadyAttached = this.attachedFiles.some(f => f.id === cardFile.id);
      if (!alreadyAttached && !this.selectedFiles.some(f => f.id === cardFile.id)) {
        this.selectedFiles.push(this.buildSelectedFile(original));
      }
      const removedIndex = this.removedFiles.findIndex(f => f.id === cardFile.id);
      if (removedIndex !== -1) {
        this.removedFiles.splice(removedIndex, 1);
      }
    },
    // Removes a file from the picked set (deselection counterpart).
    onFileUnselected(cardFile) {
      const attachedIndex = this.attachedFiles.findIndex(f => f.id === cardFile.id);
      const newlyAddedIndex = this.selectedFiles.findIndex(f => f.id === cardFile.id);
      if (attachedIndex !== -1) {
        if (!this.removedFiles.some(f => f.id === cardFile.id)) {
          this.removedFiles.push(this.attachedFiles[attachedIndex]);
        }
      } else if (newlyAddedIndex !== -1) {
        this.selectedFiles.splice(newlyAddedIndex, 1);
      }
    },
    // ---------------------------------------------------------------------
    // Custom row rendering + interactions
    // ---------------------------------------------------------------------
    // True when the file is already attached or currently picked.
    isRowSelected(item) {
      return this.attachedFiles.some(f => f.id === item.id) || this.selectedFiles.some(f => f.id === item.id);
    },
    // Row click: folders drill down, files (select mode) toggle selection.
    onRowClick(item) {
      if (item.folder || item.drive) {
        this.handleDocumentOpenFolder(item);
      } else if (!this.modeFolderSelection) {
        this.toggleFileSelection(item);
      }
    },
    // Toggles a file's selection, feeding the preserved emit contract.
    toggleFileSelection(item) {
      if (this.isRowSelected(item)) {
        this.onFileUnselected(item);
      } else {
        this.onFileSelected(item);
      }
    },
    // Right-click a folder row -> folder actions menu (destination mode only).
    onRowContextMenu(item, event) {
      if (!this.modeFolderSelection || !item || !item.folder) {
        return;
      }
      this.openFolderActionsMenu(item, event);
    },
    // Icon class/color from the shared documents icons extension.
    rowIconClass(item) {
      if (item.folder || item.drive) {
        return 'fas fa-folder';
      }
      const icon = this.$documentsIconsExtension && this.$documentsIconsExtension[0] && this.$documentsIconsExtension[0].get(item.mimetype);
      return icon && icon.class || 'fas fa-file';
    },
    rowIconColor(item) {
      if (item.folder || item.drive) {
        return 'primary';
      }
      const icon = this.$documentsIconsExtension && this.$documentsIconsExtension[0] && this.$documentsIconsExtension[0].get(item.mimetype);
      return icon && icon.color || 'grey';
    },
    // Builds the file payload consumed by the level-1 drawer / attachments list.
    buildSelectedFile(file) {
      const dest = this.computeLegacyDestination();
      const fromSpace = this.currentDrive.spaceId ? {title: this.currentDrive.title, name: dest.driveName} : {};
      return {
        id: file.id,
        name: file.name,
        title: file.name,
        size: file.size,
        mimetype: file.mimetype,
        path: dest.relativePath ? `${dest.relativePath}/${file.name}` : file.name,
        downloadUrl: file.downloadUrl,
        acl: file.acl,
        isSelected: true,
        isSelectedFromDrives: true,
        fileDrive: {name: dest.driveName, title: this.currentDrive.title},
        space: fromSpace,
        eXoDrive: true,
      };
    },
    // Emits the picked files to the level-1 drawer.
    addSelectedFiles() {
      this.$root.$emit('attachments-changed-from-drives', this.selectedFiles, this.removedFiles);
      this.resetSelection();
    },
    resetSelection() {
      this.selectedFiles = [];
      this.removedFiles = [];
    },
    // ---------------------------------------------------------------------
    // Destination selection (-> select-destination-path-for-all / add-destination-path-for-file)
    // ---------------------------------------------------------------------
    // Confirms the current selection: destination folder or picked files.
    selectActionDriveExplorerDrawer() {
      if (this.modeFolderSelection) {
        const dest = this.computeLegacyDestination();
        const drive = {
          name: dest.driveName,
          title: this.currentDrive.title,
          mainTitle: this.currentDrive.title,
          isSelected: true,
        };
        if (this.modeFolderSelectionForFile) {
          this.$root.$emit('add-destination-path-for-file', this.movedFile, dest.relativePath, dest.folderName, drive);
          this.modeFolderSelectionForFile = false;
        } else {
          this.$root.$emit('select-destination-path-for-all', dest.relativePath, dest.relativePath, dest.schemaFolder, drive);
        }
      } else {
        this.addSelectedFiles();
      }
      this.closeAttachmentsDriveExplorerDrawer();
    },
    // Derives the legacy ECMS drive name + drive-root-relative path from the current breadcrumb, so the (unchanged) level-1 upload/move flow keeps working. Mirrors the mapping used by the documents app breadcrumb.
    computeLegacyDestination() {
      const fallbackDriveName = this.currentDrive && this.currentDrive.name || 'Personal Documents';
      const fallbackTitle = this.currentDrive && this.currentDrive.title || '';
      if (!this.breadcrumb || !this.breadcrumb.length) {
        return {driveName: fallbackDriveName, relativePath: '', folderName: fallbackTitle, schemaFolder: fallbackTitle};
      }
      const root = this.breadcrumb[0];
      const last = this.breadcrumb[this.breadcrumb.length - 1];
      let driveName;
      if (root.path && root.path.startsWith('/Groups/spaces')) {
        driveName = `.spaces.${root.path.split('/').filter(segment => segment && segment.length)[2]}`;
      } else {
        driveName = 'Personal Documents';
      }
      let relativePath = (last.path && root.path && last.path !== root.path)
        ? last.path.replace(`${root.path}/`, '')
        : '';
      if (relativePath.indexOf('Public') !== -1) {
        relativePath = `Public/${relativePath.split('Public')[1]}`.replace('//', '/') || 'Public';
      }
      const folderName = this.breadcrumb.length > 1 ? this.crumbLabel(last) : fallbackTitle;
      const schemaFolder = relativePath ? `${fallbackTitle}/${relativePath}` : fallbackTitle;
      return {driveName, relativePath, folderName, schemaFolder};
    },
    // ---------------------------------------------------------------------
    // Folder CRUD (destination mode only) via DocumentFileService
    // ---------------------------------------------------------------------
    // Shows the inline "new folder" input above the list.
    addNewFolder() {
      if (this.creatingNewFolder) {
        return;
      }
      this.renameFolderAction = false;
      this.creatingNewFolder = true;
      this.newFolderName = this.$t('attachments.drawer.newFolder');
      this.$nextTick(() => this.$refs.newFolder && this.$refs.newFolder.focus && this.$refs.newFolder.focus());
    },
    // Creates the folder via DocumentFileService.createFolder.
    createNewFolder() {
      if (!this.creatingNewFolder) {
        return;
      }
      const name = this.newFolderName && this.newFolderName.trim();
      if (!name) {
        this.creatingNewFolder = false;
        return;
      }
      const nameExists = this.browseItems.some(item => item.folder && item.name === name);
      if (nameExists) {
        this.openWarningDialog(this.$t('attachments.filesFoldersSelector.popup.folderNameExists'));
        return;
      }
      const dest = this.computeLegacyDestination();
      this.creatingNewFolder = false;
      return this.$documentFileService.createFolder(this.currentDrive.ownerId, this.parentFolderId, dest.relativePath, name)
        .then(() => {
          this.newFolderName = '';
          this.navigate();
        }).catch(() => {
          this.errorMessage = this.$t('attachments.createFolder.error');
          this.showErrorMessage = true;
        });
    },
    cancelCreatingNewFolder() {
      this.creatingNewFolder = false;
      this.newFolderName = '';
    },
    // Starts inline renaming of the selected folder.
    renameFolder() {
      if (!this.selectedFolder.canRemove) {
        return;
      }
      this.newName = this.selectedFolder.name;
      this.creatingNewFolder = false;
      this.renameFolderAction = true;
      this.closeFolderActionsMenu();
      this.$nextTick(() => this.$refs.rename && this.$refs.rename.focus && this.$refs.rename.focus());
    },
    // Persists the folder rename via DocumentFileService.renameDocument.
    saveNewNameFolder() {
      const name = this.newName && this.newName.trim();
      if (!name || name === this.selectedFolder.name) {
        this.renameFolderAction = false;
        this.selectedFolder = {};
        return;
      }
      const nameExists = this.browseItems.some(item => item.folder && item.name === name);
      if (nameExists) {
        this.openWarningDialog(this.$t('attachments.renameFolder.error'));
        this.cancelRenameNewFolder();
        return;
      }
      return this.$documentFileService.renameDocument(this.currentDrive.ownerId, this.selectedFolder.id, name)
        .then(() => {
          this.renameFolderAction = false;
          this.selectedFolder = {};
          this.navigate();
        }).catch(() => {
          this.errorMessage = this.$t('attachments.renameFolder.error');
          this.showErrorMessage = true;
        });
    },
    cancelRenameNewFolder() {
      this.renameFolderAction = false;
      this.newName = this.selectedFolder.name;
    },
    // Opens the delete-confirmation dialog for the selected folder.
    deleteFolder() {
      if (!this.selectedFolder.canRemove) {
        return;
      }
      this.closeFolderActionsMenu();
      this.okAction = true;
      this.titleLabel = this.$t('attachments.filesFoldersSelector.action.delete.popup.title');
      this.okLabel = this.$t('attachments.filesFoldersSelector.action.delete.popup.button.ok');
      this.cancelLabel = this.$t('attachments.cancel');
      this.popupBodyMessage = this.$t('attachments.filesFoldersSelector.action.delete.popup.bodyMessage');
      this.$refs.confirmDialog.open();
    },
    // Confirm-dialog OK handler (folder deletion).
    okConfirmDialog() {
      if (!this.okAction) {
        return;
      }
      this.okAction = false;
      this.$documentFileService.deleteDocument(this.selectedFolder.path, this.selectedFolder.id, false, 0)
        .then(() => this.navigate())
        .catch(() => {
          this.errorMessage = this.$t('attachments.deleteFolderOrFile.error');
          this.showErrorMessage = true;
        });
    },
    openWarningDialog(message) {
      this.okAction = false;
      this.titleLabel = this.$t('attachments.filesFoldersSelector.popup.title');
      this.okLabel = this.$t('attachments.ok');
      this.cancelLabel = this.$t('attachments.cancel');
      this.popupBodyMessage = message;
      this.$refs.confirmDialog.open();
    },
    // ---------------------------------------------------------------------
    // Folder context menu
    // ---------------------------------------------------------------------
    openFolderActionsMenu(folder, event) {
      event.preventDefault();
      if (!this.modeFolderSelection || !folder || !folder.folder) {
        return;
      }
      this.selectedFolder = folder;
      this.folderActionsMenuTop = event.clientY;
      this.folderActionsMenuLeft = event.clientX;
      this.$nextTick(() => this.$refs.folderActionsMenu.openMenu());
    },
    closeFolderActionsMenu() {
      this.$refs.folderActionsMenu?.closeMenu();
    },
    // ---------------------------------------------------------------------
    // Infinite scroll
    // ---------------------------------------------------------------------
    // (Re)connects an IntersectionObserver on the load-more sentinel.
    observeScrollSentinel() {
      this.disconnectScrollObserver();
      const sentinel = this.$refs.loadMoreSentinel;
      if (!sentinel || !window.IntersectionObserver) {
        return;
      }
      this.scrollObserver = new IntersectionObserver(entries => {
        if (entries.some(entry => entry.isIntersecting)) {
          this.loadMore();
        }
      });
      this.scrollObserver.observe(sentinel);
    },
    disconnectScrollObserver() {
      if (this.scrollObserver) {
        this.scrollObserver.disconnect();
        this.scrollObserver = null;
      }
    },
    // ---------------------------------------------------------------------
    // Extensions
    // ---------------------------------------------------------------------
    executeAction(action) {
      executeExtensionAction(action, this.extensionRefs[action.key][0]);
    },
    // ---------------------------------------------------------------------
    // Default destination bootstrap (feeds the unchanged upload flow)
    // ---------------------------------------------------------------------
    // Computes the default upload destination folder (per-entity folders) and emits it so the level-1 drawer keeps uploading into the expected location. This only prepares the destination path; the upload itself is unchanged.
    initDestinationFolderPath() {
      const ownerId = eXo.env.portal.spaceIdentityId || eXo.env.portal.userIdentityId;
      const driveName = eXo.env.portal.spaceGroup ? `.spaces.${eXo.env.portal.spaceGroup}` : 'Personal Documents';
      const driveTitle = (this.defaultDrive && this.defaultDrive.title)
        || eXo.env.portal.spaceDisplayName
        || this.$t('attachments.drawer.myDocuments');
      this.defaultDriveModel = {name: driveName, title: driveTitle, ownerId};
      if (!ownerId) {
        return;
      }
      if (this.entityType && this.createEntityTypeFolder) {
        this.ensureEntityFolders(ownerId)
          .then(relativePath => this.emitDefaultFolder(relativePath, driveTitle))
          .catch(() => this.emitDefaultFolder('/', driveTitle));
      } else {
        this.emitDefaultFolder(this.defaultFolder && this.defaultFolder !== '/' ? this.defaultFolder : '/', driveTitle);
      }
    },
    emitDefaultFolder(relativePath, driveTitle) {
      const schema = relativePath && relativePath !== '/' ? `${driveTitle}/${relativePath}` : driveTitle;
      this.$root.$emit('attachments-default-folder-path-initialized', relativePath, schema);
    },
    // Find-or-create the per-entity folders (entityType[/entityId]) at the drive root.
    ensureEntityFolders(ownerId) {
      const entityFolderName = this.entityType === 'activity' ? 'Activity Stream Documents' : this.entityType;
      return this.ensureFolder(ownerId, null, '', entityFolderName).then(entityFolder => {
        if (!this.entityId || !entityFolder || !entityFolder.id) {
          return entityFolderName;
        }
        return this.ensureFolder(ownerId, entityFolder.id, entityFolderName, String(this.entityId))
          .then(() => `${entityFolderName}/${this.entityId}`);
      });
    },
    // Returns an existing child folder by name, creating it when missing.
    ensureFolder(ownerId, parentFolderId, folderPath, name) {
      const filter = {ownerId, listingType: 'FOLDER', sortField: 'name', ascending: true, favorites: false};
      if (parentFolderId) {
        filter.parentFolderId = parentFolderId;
      }
      return this.$documentFileService.getDocumentItems(filter, null, null, 0, 200, 'owner')
        .then(items => {
          const found = (items || []).find(item => item.folder && item.name === name);
          if (found) {
            return found;
          }
          return this.$documentFileService.createFolder(ownerId, parentFolderId, folderPath, name);
        });
    },
  }
};
</script>
