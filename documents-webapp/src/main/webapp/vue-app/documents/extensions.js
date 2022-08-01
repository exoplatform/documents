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

extensionRegistry.registerComponent('DocumentsHeaderRight', 'documents-header-right', {
  id: 'primary-filter',
  cssClass: 'pt-1',
  vueComponent: Vue.options.components['documents-filter'],
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
  cssClass: 'font-weight-bold text-no-wrap last-updated-tooltip-marker',
  width: '190px',
  rank: 40,
  componentOptions: {
    vueComponent: Vue.options.components['documents-last-updated-cell'],
  },
});

extensionRegistry.registerExtension('DocumentMenu', 'menuActionMenu', {
  id: 'favorite',
  labelKey: 'documents.label.favorite',
  align: 'center',
  sortable: true,
  cssClass: 'font-weight-bold text-no-wrap',
  width: '190px',
  rank: 40,
  enabled: () => true,
  componentOptions: {
    vueComponent: Vue.options.components['favorite-menu-action'],
  },
});

extensionRegistry.registerExtension('DocumentMenu', 'menuActionMenu', {
  id: 'copyLink',
  labelKey: 'documents.label.copy.link',
  align: 'center',
  sortable: true,
  cssClass: 'font-weight-bold text-no-wrap',
  width: '190px',
  rank: 40,
  enabled: () => true,
  componentOptions: {
    vueComponent: Vue.options.components['copy-link-menu-action'],
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
  enabled: (acl) => {
    return acl.canEdit;
  },
  componentOptions: {
    vueComponent: Vue.options.components['edit-menu-action'],
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
  enabled: (acl) => {
    return acl.canEdit;
  },
  componentOptions: {
    vueComponent: Vue.options.components['rename-menu-action'],
  },
});

extensionRegistry.registerExtension('DocumentMenu', 'menuActionMenu', {
  id: 'move',
  labelKey: 'documents.label.move',
  align: 'center',
  sortable: true,
  cssClass: 'font-weight-bold text-no-wrap',
  width: '190px',
  rank: 40,
  enabled: (acl) => {
    return acl.canEdit;
  },
  componentOptions: {
    vueComponent: Vue.options.components['move-menu-action'],
  },
});

extensionRegistry.registerExtension('DocumentMenu', 'menuActionMenu', {
  id: 'duplicate',
  labelKey: 'documents.label.duplicate',
  align: 'center',
  sortable: true,
  cssClass: 'font-weight-bold text-no-wrap',
  width: '190px',
  rank: 40,
  enabled: (acl) => {
    return acl.canEdit;
  },
  componentOptions: {
    vueComponent: Vue.options.components['duplicate-menu-action'],
  },
});

extensionRegistry.registerExtension('DocumentMenu', 'menuActionMenu', {
  id: 'visibility',
  labelKey: 'documents.label.visibility',
  align: 'center',
  sortable: true,
  cssClass: 'font-weight-bold text-no-wrap',
  width: '190px',
  rank: 40,
  enabled: (acl, isSymlink) => {
    return acl.canEdit && !isSymlink;
  },
  componentOptions: {
    vueComponent: Vue.options.components['visibility-menu-action'],
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
  enabled: (acl) => {
    return acl.canEdit;
  },
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
  enabled: () => true,
  componentOptions: {
    vueComponent: Vue.options.components['details-menu-action'],
  },
});

extensionRegistry.registerExtension('DocumentMenu', 'menuActionMenu', {
  id: 'delete',
  labelKey: 'documents.label.delete',
  align: 'center',
  sortable: true,
  cssClass: 'font-weight-bold text-no-wrap',
  width: '190px',
  rank: 40,
  enabled: (acl) => {
    return acl.canEdit;
  },
  componentOptions: {
    vueComponent: Vue.options.components['delete-menu-action'],
  },
});

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

extensionRegistry.registerExtension('Documents', 'views', {
  id: 'folder',
  labelKey: 'documents.label.folderView',
  listingType: 'FOLDER',
  filePropertiesExpand: 'creator,modifier,owner,metadatas',
  componentOptions: {
    vueComponent: Vue.options.components['documents-folder-view'],
  },
  rank: 30,
});


extensionRegistry.registerExtension('DocumentTabs', 'documentsHeaderTab', {
  id: 'recentView',
  viewName: 'timeline',
  icon: 'fas fa-history',
  labelKey: 'documents.label.timelineView',
  rank: 10,
});


extensionRegistry.registerExtension('DocumentTabs', 'documentsHeaderTab', {
  id: 'folderView',
  viewName: 'folder',
  icon: 'fas fa-folder',
  labelKey: 'documents.label.folderView',
  rank: 20,
});

