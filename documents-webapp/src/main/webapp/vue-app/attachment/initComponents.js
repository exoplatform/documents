import AttachmentsDrawer  from './components/AttachmentsDrawer.vue';
import AttachmentItem  from './components/attachments-upload-components/AttachmentItem.vue';
import AttachmentsFolderActionsMenu  from './components/attachments-drive-explorer/AttachmentsFolderActionsMenu.vue';
import Attachment from './components/Attachment.vue';
import AttachmentsDriveExplorerFileItem from './components/attachments-drive-explorer/AttachmentsDriveExplorerFileItem.vue';
import AttachmentsListDrawer from './components/AttachmentsListDrawer.vue';
import AttachmentsUploadInput from './components/attachments-upload-components/AttachmentsUploadInput.vue';
import AttachmentsUploadedFiles from './components/attachments-upload-components/AttachmentsUploadedFiles.vue';
import AttachmentsDriveExplorerDrawer from './components/attachments-drive-explorer/AttachmentsDriveExplorerDrawer.vue';
import AttachmentsSelectFromDrive from './components/attachments-drive-explorer/AttachmentsSelectFromDrive.vue';
import ActivityAttachments from './components/activity/ActivityAttachments.vue';
import ActivityAttachment from './components/activity/ActivityAttachment.vue';
import ActivityComposerAttachments from './components/activity/ActivityComposerAttachments.vue';
import AttachmentCreateDocumentInput from './components/attachment-document-creator/AttachmentCreateDocumentInput.vue';
import AttachmentCreateDocumentDrawer from './components/attachment-document-creator/AttachmentCreateDocumentDrawer.vue';
import TaskAttachment from './components/task/TaskAttachment.vue';
import AnalyticsTableCellDocumentTitleValue from './components/analytics/AnalyticsTableCellDocumentTitleValue.vue';
import AnalyticsTableCellDocumentSizeValue from './components/analytics/AnalyticsTableCellDocumentSizeValue.vue';
import AnalyticsTableCellDocumentOriginValue from './components/analytics/AnalyticsTableCellDocumentOriginValue.vue';
import ContentAttachmentList from './components/content/ContentAttachmentList.vue';
import ContentAttachmentItem from './components/content/ContentAttachmentItem.vue';
import NotesAttachment from './components/notes/NotesAttachment.vue';
import NotesAttachmentCarousel from './components/notes/NotesAttachmentCarousel.vue';
import NotesAttachmentItem from './components/notes/NotesAttachmentItem.vue';
import NotesAttachmentButton from './components/notes/NotesAttachmentButton.vue';
import ActivityAttachmentIcon from './components/activity/ActivityAttachmentIcon.vue';
// Reused Documents navigation drawer (drives/spaces/folder tree) + its tree
// content, imported so they exist in the attachmentApp bundle even on pages
// where the Documents bundle is not loaded (e.g. the activity composer). The
// folder/file rows are rendered by the drawer's own simple template.
import FolderTreeViewDrawer from '../documents/components/body/views/FolderTreeViewDrawer.vue';
import TreeView from '../documents/components/body/views/TreeView.vue';
import DocumentsBreadcrumb from '../documents/components/body/views/DocumentsBreadcrumb.vue';
// Reused Documents-app grid card (thumbnail preview + info icon) used to render
// the "Add a document" drawer Templates section. DocumentsFileEditNameCell is a
// leaf dependency of the card template (inline rename, never triggered here).
import DocumentItemCard from '../documents/components/body/views/DocumentItemCard.vue';
import DocumentsFileEditNameCell from '../documents/components/body/table/cells/DocumentsFileEditNameCell.vue';

const components = {
  'attachments-drawer': AttachmentsDrawer,
  'attachment': Attachment,
  'attachment-item': AttachmentItem,
  'attachments-drive-explorer-drawer': AttachmentsDriveExplorerDrawer,
  'attachments-select-from-drive': AttachmentsSelectFromDrive,
  'attachments-drive-explorer-file-item': AttachmentsDriveExplorerFileItem,
  'attachments-folder-actions-menu': AttachmentsFolderActionsMenu,
  'attachments-list-drawer': AttachmentsListDrawer,
  'attachments-uploaded-files': AttachmentsUploadedFiles,
  'attachments-upload-input': AttachmentsUploadInput,
  'activity-attachments': ActivityAttachments,
  'activity-attachment': ActivityAttachment,
  'activity-composer-attachments': ActivityComposerAttachments,
  'activity-attachment-icon': ActivityAttachmentIcon,
  'attachment-create-document-input': AttachmentCreateDocumentInput,
  'attachment-create-document-drawer': AttachmentCreateDocumentDrawer,
  'task-attachment': TaskAttachment,
  'analytics-table-cell-document-title-value': AnalyticsTableCellDocumentTitleValue,
  'analytics-table-cell-document-size-value': AnalyticsTableCellDocumentSizeValue,
  'analytics-table-cell-document-origin-value': AnalyticsTableCellDocumentOriginValue,
  'content-attachment-list': ContentAttachmentList,
  'content-attachment-item': ContentAttachmentItem,
  'notes-attachment': NotesAttachment,
  'notes-attachment-carousel': NotesAttachmentCarousel,
  'notes-attachment-item': NotesAttachmentItem,
  'notes-attachment-button': NotesAttachmentButton,
  // Reused Documents navigation drawer + breadcrumb used by the drive explorer.
  'folder-treeview-drawer': FolderTreeViewDrawer,
  'document-tree-view': TreeView,
  'documents-breadcrumb': DocumentsBreadcrumb,
  // Reused Documents-app grid card for the create-document drawer Templates section.
  'documents-item-card': DocumentItemCard,
  'documents-file-edit-name-cell': DocumentsFileEditNameCell,
};

for (const key in components) {
  Vue.component(key, components[key]);
}

import * as attachmentService from '../../js/attachmentService.js';

if (!Vue.prototype.$attachmentService) {
  window.Object.defineProperty(Vue.prototype, '$attachmentService', {
    value: attachmentService,
  });
}

import * as documentFileService from '../../js/DocumentFileService.js';

if (!Vue.prototype.$documentFileService) {
  window.Object.defineProperty(Vue.prototype, '$documentFileService', {
    value: documentFileService,
  });
}

import * as transferRulesService from '../../js/transferRulesService.js';

if (!Vue.prototype.$transferRulesService) {
  window.Object.defineProperty(Vue.prototype, '$transferRulesService', {
    value: transferRulesService,
  });
}

// Required by the reused Documents-app leaf cards ($root.getImageUrl /
// $root.getDownloadUrl delegate to it to build thumbnail/download URLs).
import * as documentsUtils from '../../js/DocumentsUtils.js';

if (!Vue.prototype.$documentsUtils) {
  window.Object.defineProperty(Vue.prototype, '$documentsUtils', {
    value: documentsUtils,
  });
}
