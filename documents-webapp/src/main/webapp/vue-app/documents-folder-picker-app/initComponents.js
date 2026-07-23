/*
 * Copyright (C) 2025 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

// The ONE existing folders-only drive explorer (revamped, EXO-88721) and its own
// component closure, registered globally exactly like the attachment app registers
// them (Vue.component registration is idempotent, so coexisting with the attachment
// bundle on the same page is safe). No component is copied or forked: these are the
// same single .vue sources the attachment app builds from.
import AttachmentsDriveExplorerDrawer from '../attachment/components/attachments-drive-explorer/AttachmentsDriveExplorerDrawer.vue';
import AttachmentsFolderActionsMenu from '../attachment/components/attachments-drive-explorer/AttachmentsFolderActionsMenu.vue';
// Reused Documents navigation drawer (drives/spaces/folder tree) + breadcrumb the
// explorer leans on, same reuse the attachment bundle makes.
import FolderTreeViewDrawer from '../documents/components/body/views/FolderTreeViewDrawer.vue';
import TreeView from '../documents/components/body/views/TreeView.vue';
import DocumentsBreadcrumb from '../documents/components/body/views/DocumentsBreadcrumb.vue';

const components = {
  'attachments-drive-explorer-drawer': AttachmentsDriveExplorerDrawer,
  'attachments-folder-actions-menu': AttachmentsFolderActionsMenu,
  'folder-treeview-drawer': FolderTreeViewDrawer,
  'document-tree-view': TreeView,
  'documents-breadcrumb': DocumentsBreadcrumb,
};

for (const key in components) {
  Vue.component(key, components[key]);
}

// Services the explorer + nav tree + breadcrumb call ($documentFileService for
// /v1/documents browsing and folder CRUD; guarded the same way as in the
// attachment bundle so whichever bundle loads first wins).
import * as documentFileService from '../../js/DocumentFileService.js';

if (!Vue.prototype.$documentFileService) {
  window.Object.defineProperty(Vue.prototype, '$documentFileService', {
    value: documentFileService,
  });
}
