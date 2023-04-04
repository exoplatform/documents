/*
 * This file is part of the Meeds project (https://meeds.io/).
 *
 * Copyright (C) 2020 - 2023 Meeds Association contact@meeds.io
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

import DocumentsMain from './components/DocumentsMain.vue';
import DocumentsAppReminder from './components/DocumentsAppReminder.vue';
import SwitchNewDocument from './components/SwitchNewDocument.vue';
import DocumentsHeader from './components/header/DocumentsHeader.vue';
import DocumentsHeaderLeft from './components/header/DocumentsHeaderLeft.vue';
import DocumentsHeaderRight from './components/header/DocumentsHeaderRight.vue';
import DocumentsHeaderCenter from './components/header/DocumentsHeaderCenter.vue';
import DocumentsAddNewFile from './components/header/actions/DocumentsAddNewFile.vue';
import DocumentsFilterInput from './components/header/actions/DocumentsFilterInput.vue';
import DocumentsBody from './components/body/DocumentsBody.vue';
import DocumentsNoBody from './components/body/DocumentsNoBody.vue';
import DocumentsNoBodyFolder from './components/body/DocumentsNoBodyFolder.vue';
import DocumentsNoResultBody from './components/body/DocumentsNoResultBody.vue';
import DocumentsTimelineView from './components/body/views/DocumentsTimelineView.vue';
import DocumentsFolderView from './components/body/views/DocumentsFolderView.vue';
import DocumentsTimelineGroupHeader from './components/body/views/DocumentsTimelineGroupHeader.vue';
import DocumentsTableCell from './components/body/table/DocumentsTableCell.vue';
import DocumentsLastUpdatedCell from './components/body/table/cells/DocumentsLastUpdatedCell.vue';
import DocumentsVisibilityCell from './components/body/table/cells/DocumentsVisibilityCell.vue';
import DocumentsFileSizeCell from './components/body/table/cells/DocumentsFileSizeCell.vue';
import DocumentsLastActivityCell from './components/body/table/cells/DocumentsLastActivityCell.vue';
import DocumentsFavoriteCell from './components/body/table/cells/DocumentsFavoriteCell.vue';
import DocumentsFavoriteAction from './components/body/table/action/DocumentFavoriteAction.vue';
import DocumentsFileNameCell from './components/body/table/cells/DocumentsFileNameCell.vue';
import DocumentsFileEditNameCell from './components/body/table/cells/DocumentsFileEditNameCell.vue';
import DocumentsFilter from './components/header/actions/DocumentsFilter.vue';
import DocumentActionMenu from './components/body/table/DocumentActionMenu.vue';
import DocumentActionMenuMobile from './components/body/table/DocumentActionMenuMobile.vue';
import DocumentInfoDrawer from './components/body/table/DocumentInfoDrawer.vue';
import DocumentsTreeSelectorDrawer from './components/body/table/DocumentsTreeSelectorDrawer.vue';
import DocumentsMoveSpaces from './components/body/table/DocumentsMoveSpaces.vue';
import DocumentsVisibilityDrawer from './components/body/table/DocumentsVisibilityDrawer.vue';
import DocumentsAllUsersVisibilityDrawer from './components/body/table/DocumentsAllUsersVisibilityDrawer.vue';
import DocumentsVisibilityCollaborators from './components/body/table/DocumentsVisibilityCollaborators.vue';
import DocumentsVisibilityMenu from './components/body/table/DocumentsVisibilityMenu.vue';
import EditMenuAction from './components/body/actions/EditMenuAction.vue';
import DownloadMenuAction from './components/body/actions/DownloadMenuAction.vue';
import MoveMenuAction from './components/body/actions/MoveMenuAction.vue';
import DuplicateMenuAction from './components/body/actions/DuplicateMenuAction.vue';
import VisibilityMenuAction from './components/body/actions/VisibilityMenuAction.vue';
import FavoriteMenuAction from './components/body/actions/FavoriteMenuAction.vue';
import RenameMenuAction from './components/body/actions/RenameMenuAction.vue';
import CopyLinkMenuAction from './components/body/actions/CopyLinkMenuAction.vue';
import DocumentsBreadcrumb from './components/body/views/DocumentsBreadcrumb.vue';
import FolderTreeViewDrawer from './components/body/views/FolderTreeViewDrawer.vue';
import DetailsMenuAction from './components/body/actions/DetailsMenuAction.vue';
import DeleteMenuAction from './components/body/actions/DeleteMenuAction.vue';
import ShortcutMenuAction from './components/body/actions/ShortcutMenuAction.vue';
import DocumentsInfoDetailsCell from './components/body/table/cells/DocumentsInfoDetailsCell.vue';
import DocumentAddNewMobile from './components/header/actions/DocumentAddNewMobile.vue';
import DocumentFilterMenuMobile from './components/header/actions/DocumentFilterMenuMobile.vue';
import FavoriteFilterActionMobile from './components/header/actions/FavoriteFilterActionMobile.vue';
import AllFilterActionMobile from './components/header/actions/AllFilterActionMobile.vue';
import QuickFilterActionMobile from './components/header/actions/QuickFilterActionMobile.vue';
import ExtendFilterActionMobile from './components/header/actions/ExtendFilterActionMobile.vue';
import UploadOverlay from './components/body/UploadOverlay.vue';
import VersionHistoryMenuAction from './components/body/actions/VersionHistoryMenuAction.vue';
import DocumentsSelectionCell from './components/body/table/cells/DocumentsSelectionCell.vue';
import DocumentActionContextMenu from './components/body/table/DocumentActionContextMenu.vue';

const components = {
  'documents-main': DocumentsMain,
  'documents-header': DocumentsHeader,
  'documents-header-left': DocumentsHeaderLeft,
  'documents-header-right': DocumentsHeaderRight,
  'documents-header-center': DocumentsHeaderCenter,
  'documents-add-new-file': DocumentsAddNewFile,
  'documents-filter-input': DocumentsFilterInput,
  'documents-filter': DocumentsFilter,
  'documents-body': DocumentsBody,
  'documents-no-body': DocumentsNoBody,
  'documents-no-body-folder': DocumentsNoBodyFolder,
  'documents-no-result-body': DocumentsNoResultBody,
  'documents-folder-view': DocumentsFolderView,
  'documents-timeline-view': DocumentsTimelineView,
  'documents-timeline-group-header': DocumentsTimelineGroupHeader,
  'documents-table-cell': DocumentsTableCell,
  'documents-last-updated-cell': DocumentsLastUpdatedCell,
  'documents-visibility-cell': DocumentsVisibilityCell,
  'documents-file-size-cell': DocumentsFileSizeCell,
  'documents-last-activity-cell': DocumentsLastActivityCell,
  'documents-favorite-cell': DocumentsFavoriteCell,
  'documents-favorite-action': DocumentsFavoriteAction,
  'documents-file-name-cell': DocumentsFileNameCell,
  'documents-file-edit-name-cell': DocumentsFileEditNameCell,
  'documents-actions-menu': DocumentActionMenu,
  'documents-actions-menu-mobile': DocumentActionMenuMobile,
  'documents-add-new-menu-mobile': DocumentAddNewMobile,
  'documents-filter-menu-mobile': DocumentFilterMenuMobile,
  'documents-info-drawer': DocumentInfoDrawer,
  'document-tree-selector-drawer': DocumentsTreeSelectorDrawer,
  'documents-move-spaces': DocumentsMoveSpaces,
  'documents-visibility-drawer': DocumentsVisibilityDrawer,
  'documents-visibility-all-users-drawer': DocumentsAllUsersVisibilityDrawer,
  'documents-visibility-collaborators': DocumentsVisibilityCollaborators,
  'documents-visibility-menu': DocumentsVisibilityMenu,
  'edit-menu-action': EditMenuAction,
  'rename-menu-action': RenameMenuAction,
  'download-menu-action': DownloadMenuAction,
  'move-menu-action': MoveMenuAction,
  'duplicate-menu-action': DuplicateMenuAction,
  'visibility-menu-action': VisibilityMenuAction,
  'copy-link-menu-action': CopyLinkMenuAction,
  'favorite-menu-action': FavoriteMenuAction,
  'delete-menu-action': DeleteMenuAction,
  'shortcut-menu-action': ShortcutMenuAction,
  'documents-breadcrumb': DocumentsBreadcrumb,
  'folder-treeview-drawer': FolderTreeViewDrawer,
  'details-menu-action': DetailsMenuAction,
  'documents-info-details-cell': DocumentsInfoDetailsCell,
  'switch-new-document': SwitchNewDocument,
  'documents-app-reminder': DocumentsAppReminder,
  'upload-overlay': UploadOverlay,
  'versionHistory-menu-action': VersionHistoryMenuAction,
  'favorite-filter-action': FavoriteFilterActionMobile,
  'all-filter-action': AllFilterActionMobile,
  'quick-filter-action': QuickFilterActionMobile,
  'extend-filter-action': ExtendFilterActionMobile,
  'documents-selection-cell': DocumentsSelectionCell,
  'document-action-context-menu': DocumentActionContextMenu
};

for (const key in components) {
  Vue.component(key, components[key]);
}
