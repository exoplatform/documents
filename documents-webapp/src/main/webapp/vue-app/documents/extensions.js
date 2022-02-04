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

if (eXo.env.portal.filesFavoritesEnabled) {
  extensionRegistry.registerComponent('DocumentsHeaderRight', 'documents-header-right', {
    id: 'primary-filter',
    cssClass: 'pt-1',
    vueComponent: Vue.options.components['documents-filter'],
    rank: 20,
  });
}

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

extensionRegistry.registerExtension('DocumentMenu', 'menuActionMenu', {
  id: 'edit',
  labelKey: 'documents.label.edit',
  align: 'center',
  sortable: true,
  cssClass: 'font-weight-bold text-no-wrap',
  width: '190px',
  rank: 40,
  componentOptions: {
    vueComponent: Vue.options.components['edit-menu-action'],
  },
});

extensionRegistry.registerExtension('DocumentMenu', 'menuActionMenu', {
  id: 'favorite',
  labelKey: 'favorite.label.download',
  align: 'center',
  sortable: true,
  cssClass: 'font-weight-bold text-no-wrap',
  width: '190px',
  rank: 40,
  componentOptions: {
    vueComponent: Vue.options.components['favorite-menu-action'],
  },
});

extensionRegistry.registerExtension('DocumentMenu', 'menuActionMenu', {
  id: 'rename',
  labelKey: 'documents.label.rename',
  align: 'center',
  sortable: true,
  cssClass: 'font-weight-bold text-no-wrap',
  width: '190px',
  rank: 40,
  componentOptions: {
    vueComponent: Vue.options.components['rename-menu-action'],
  },
});

extensionRegistry.registerExtension('DocumentMenu', 'menuActionMenu', {
  id: 'download',
  labelKey: 'documents.label.download',
  align: 'center',
  sortable: true,
  cssClass: 'font-weight-bold text-no-wrap',
  width: '190px',
  rank: 40,
  componentOptions: {
    vueComponent: Vue.options.components['download-menu-action'],
  },
});

extensionRegistry.registerExtension('DocumentMenu', 'menuActionMenu', {
  id: 'details',
  labelKey: 'documents.label.details',
  align: 'center',
  sortable: true,
  cssClass: 'font-weight-bold text-no-wrap',
  width: '190px',
  rank: 40,
  componentOptions: {
    vueComponent: Vue.options.components['details-menu-action'],
  },
});

/*extensionRegistry.registerExtension('Documents', 'timelineViewHeader', {
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
extensionRegistry.registerExtension('Documents', 'timelineViewHeader', {
  id: 'lastActivity',
  labelKey: 'documents.label.lastActivity',
  align: 'center',
  sortable: false,
  cssClass: 'font-weight-bold text-no-wrap',
  width: '120px',
  rank: 30,
  componentOptions: {
    vueComponent: Vue.options.components['documents-last-activity-cell'],
  },
});*/
if (eXo.env.portal.filesFavoritesEnabled) {
  extensionRegistry.registerExtension('Documents', 'timelineViewHeader', {
    id: 'favorite',
    labelKey: 'documents.label.favorite',
    align: 'center',
    sortable: true,
    cssClass: 'font-weight-bold text-no-wrap',
    width: '120px',
    rank: 60,
    componentOptions: {
      vueComponent: Vue.options.components['documents-favorite-cell'],
    },
  });
}


