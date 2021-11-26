import DocumentsMain from './components/DocumentsMain.vue';
import DocumentsHeader from './components/header/DocumentsHeader.vue';
import DocumentsHeaderLeft from './components/header/DocumentsHeaderLeft.vue';
import DocumentsHeaderRight from './components/header/DocumentsHeaderRight.vue';
import DocumentsAddNewFile from './components/header/actions/DocumentsAddNewFile.vue';
import DocumentsFilterInput from './components/header/actions/DocumentsFilterInput.vue';
import DocumentsBody from './components/body/DocumentsBody.vue';
import DocumentsNoBody from './components/body/DocumentsNoBody.vue';
import DocumentsTimelineView from './components/body/views/DocumentsTimelineView.vue';
import DocumentsTimelineGroupHeader from './components/body/views/DocumentsTimelineGroupHeader.vue';
import DocumentsTableCell from './components/body/table/DocumentsTableCell.vue';
import DocumentsLastUpdatedCell from './components/body/table/cells/DocumentsLastUpdatedCell.vue';
import DocumentsFileSizeCell from './components/body/table/cells/DocumentsFileSizeCell.vue';
import DocumentsLastActivityCell from './components/body/table/cells/DocumentsLastActivityCell.vue';
import DocumentsFavoriteCell from './components/body/table/cells/DocumentsFavoriteCell.vue';
import DocumentsFileNameCell from './components/body/table/cells/DocumentsFileNameCell.vue';

const components = {
  'documents-main': DocumentsMain,
  'documents-header': DocumentsHeader,
  'documents-header-left': DocumentsHeaderLeft,
  'documents-header-right': DocumentsHeaderRight,
  'documents-add-new-file': DocumentsAddNewFile,
  'documents-filter-input': DocumentsFilterInput,
  'documents-body': DocumentsBody,
  'documents-no-body': DocumentsNoBody,
  'documents-timeline-view': DocumentsTimelineView,
  'documents-timeline-group-header': DocumentsTimelineGroupHeader,
  'documents-table-cell': DocumentsTableCell,
  'documents-last-updated-cell': DocumentsLastUpdatedCell,
  'documents-file-size-cell': DocumentsFileSizeCell,
  'documents-last-activity-cell': DocumentsLastActivityCell,
  'documents-favorite-cell': DocumentsFavoriteCell,
  'documents-file-name-cell': DocumentsFileNameCell,
};

for (const key in components) {
  Vue.component(key, components[key]);
}