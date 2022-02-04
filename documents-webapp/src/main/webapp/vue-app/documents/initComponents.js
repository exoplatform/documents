import DocumentsMain from './components/DocumentsMain.vue';
import DocumentsHeader from './components/header/DocumentsHeader.vue';
import DocumentsHeaderLeft from './components/header/DocumentsHeaderLeft.vue';
import DocumentsHeaderRight from './components/header/DocumentsHeaderRight.vue';
import DocumentsAddNewFile from './components/header/actions/DocumentsAddNewFile.vue';
import DocumentsFilterInput from './components/header/actions/DocumentsFilterInput.vue';
import DocumentsBody from './components/body/DocumentsBody.vue';
import DocumentsNoBody from './components/body/DocumentsNoBody.vue';
import DocumentsNoResultBody from './components/body/DocumentsNoResultBody.vue';
import DocumentsTimelineView from './components/body/views/DocumentsTimelineView.vue';
import DocumentsTimelineGroupHeader from './components/body/views/DocumentsTimelineGroupHeader.vue';
import DocumentsTableCell from './components/body/table/DocumentsTableCell.vue';
import DocumentsLastUpdatedCell from './components/body/table/cells/DocumentsLastUpdatedCell.vue';
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
import EditMenuAction from './components/body/actions/EditMenuAction.vue';
import DownloadMenuAction from './components/body/actions/DownloadMenuAction.vue';
import FavoriteMenuAction from './components/body/actions/FavoriteMenuAction.vue';
import RenameMenuAction from './components/body/actions/RenameMenuAction.vue';
import DetailsMenuAction from './components/body/actions/DetailsMenuAction.vue';
import DocumentsInfoDetailsCell from './components/body/table/cells/DocumentsInfoDetailsCell.vue';

const components = {
  'documents-main': DocumentsMain,
  'documents-header': DocumentsHeader,
  'documents-header-left': DocumentsHeaderLeft,
  'documents-header-right': DocumentsHeaderRight,
  'documents-add-new-file': DocumentsAddNewFile,
  'documents-filter-input': DocumentsFilterInput,
  'documents-filter': DocumentsFilter,
  'documents-body': DocumentsBody,
  'documents-no-body': DocumentsNoBody,
  'documents-no-result-body': DocumentsNoResultBody,
  'documents-timeline-view': DocumentsTimelineView,
  'documents-timeline-group-header': DocumentsTimelineGroupHeader,
  'documents-table-cell': DocumentsTableCell,
  'documents-last-updated-cell': DocumentsLastUpdatedCell,
  'documents-file-size-cell': DocumentsFileSizeCell,
  'documents-last-activity-cell': DocumentsLastActivityCell,
  'documents-favorite-cell': DocumentsFavoriteCell,
  'documents-favorite-action': DocumentsFavoriteAction,
  'documents-file-name-cell': DocumentsFileNameCell,
  'documents-file-edit-name-cell': DocumentsFileEditNameCell,
  'documents-actions-menu': DocumentActionMenu,
  'documents-actions-menu-mobile': DocumentActionMenuMobile,
  'documents-info-drawer': DocumentInfoDrawer,
  'edit-menu-action': EditMenuAction,
  'rename-menu-action': RenameMenuAction,
  'download-menu-action': DownloadMenuAction,
  'favorite-menu-action': FavoriteMenuAction,
  'details-menu-action': DetailsMenuAction,
  'documents-info-details-cell': DocumentsInfoDetailsCell,
};

for (const key in components) {
  Vue.component(key, components[key]);
}