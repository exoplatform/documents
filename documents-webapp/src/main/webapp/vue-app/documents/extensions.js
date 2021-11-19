extensionRegistry.registerComponent('DocumentsHeaderLeft', 'documents-header-left', {
  id: 'add-new-file',
  vueComponent: Vue.options.components['documents-add-new-file'],
  rank: 20,
});

extensionRegistry.registerComponent('DocumentsHeaderRight', 'documents-header-right', {
  id: 'filter-input',
  vueComponent: Vue.options.components['documents-filter-input'],
  rank: 20,
});

extensionRegistry.registerExtension('Documents', 'views', {
  id: 'timeline',
  labelKey: 'documents.label.timelineView',
  listingType: 'TIMELINE',
  filePropertiesExpand: 'creator,modifier,owner,metadatas',
  componentOptions: {
    vueComponent: Vue.options.components['documents-timeline-view'],
  },
  rank: 20,
});

extensionRegistry.registerExtension('Documents', 'timelineViewHeader', {
  id: 'name',
  labelKey: 'documents.label.name',
  align: 'left',
  sortable: true,
  cssClass: 'font-weight-bold',
  width: 'auto',
  rank: 20,
  componentOptions: {
    vueComponent: Vue.options.components['documents-file-name-cell'],
  },
});

extensionRegistry.registerExtension('Documents', 'timelineViewHeader', {
  id: 'lastUpdated',
  labelKey: 'documents.label.lastUpdated',
  align: 'center',
  sortable: true,
  cssClass: 'font-weight-bold text-no-wrap',
  width: '190px',
  rank: 40,
  componentOptions: {
    vueComponent: Vue.options.components['documents-last-updated-cell'],
  },
});

extensionRegistry.registerExtension('Documents', 'timelineViewHeader', {
  id: 'size',
  labelKey: 'documents.label.fileSize',
  align: 'center',
  sortable: false,
  cssClass: 'font-weight-bold text-no-wrap',
  width: '120px',
  rank: 50,
  componentOptions: {
    vueComponent: Vue.options.components['documents-file-size-cell'],
  },
});

if (eXo.env.portal.activityFavoritesEnabled) {
  extensionRegistry.registerExtension('Documents', 'timelineViewHeader', {
    id: 'favorite',
    labelKey: 'documents.label.favorite',
    align: 'center',
    sortable: false,
    cssClass: 'font-weight-bold text-no-wrap',
    width: '120px',
    rank: 60,
    componentOptions: {
      vueComponent: Vue.options.components['documents-favorite-cell'],
    },
  });
}


