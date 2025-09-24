/*
 * Copyright (C) 2024 eXo Platform SAS.
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

extensionRegistry.registerComponent('DocumentsHeaderLeft', 'documents-header-left', {
  id: 'add-new-file',
  vueComponent: Vue.options.components['documents-add-new-file'],
  rank: 20,
});

extensionRegistry.registerExtension('Documents', 'views', {
  id: 'timeline',
  labelKey: 'documents.label.timelineView',
  listingType: 'TIMELINE',
  filePropertiesExpand: 'creator,modifier,owner,metadatas',
  value: 'timeline',
  viewName: 'timeline',
  icon: 'fas fa-history',
  componentOptions: {
    vueComponent: Vue.options.components['documents-timeline-view'],
  },
  rank: 20,
});

extensionRegistry.registerExtension('Documents', 'views', {
  id: 'folder',
  labelKey: 'documents.label.folderView',
  listingType: 'FOLDER',
  filePropertiesExpand: 'creator,modifier,owner,metadatas',
  value: 'folder',
  viewName: 'folder',
  icon: 'fas fa-folder',
  componentOptions: {
    vueComponent: Vue.options.components['documents-folder-view'],
  },
  rank: 30,
});

extensionRegistry.registerExtension('Documents', 'timelineViewHeader', {
  id: 'name',
  labelKey: 'documents.label.name',
  align: 'left',
  sortable: true,
  cssClass: 'text-truncate tooltip-marker',
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
  cssClass: 'text-truncate tooltip-marker',
  width: '170px',
  rank: 40,
  componentOptions: {
    vueComponent: Vue.options.components['documents-last-updated-cell'],
  },
});

extensionRegistry.registerExtension('Documents', 'timelineViewHeader', {
  id: 'visibility',
  labelKey: 'documents.label.extension.visibility',
  align: 'center',
  sortable: true,
  cssClass: 'text-truncate tooltip-marker',
  width: '100px',
  rank: 60,
  componentOptions: {
    vueComponent: Vue.options.components['documents-visibility-cell'],
  },
});

extensionRegistry.registerExtension('DocumentMenu', 'menuActionMenu', {
  id: 'favorite',
  labelKey: 'documents.label.favorite',
  align: 'center',
  sortable: true,
  cssClass: 'text-truncate',
  width: '190px',
  rank: 13,
  enabled: (file, isMobile) => {
    return file && !file.folder && !file.cloudDriveFolder && isMobile;
  },
  enabledForMultiSelection: () => false,
  componentOptions: {
    vueComponent: Vue.options.components['favorite-menu-action'],
  },
});

extensionRegistry.registerExtension('DocumentMenu', 'menuActionMenu', {
  id: 'copyLink',
  labelKey: 'documents.label.copy.link',
  align: 'center',
  sortable: true,
  cssClass: 'text-truncate',
  width: '190px',
  rank: 10,
  enabled: () => true,
  enabledForMultiSelection: () => false,
  componentOptions: {
    vueComponent: Vue.options.components['copy-link-menu-action'],
  },
});

extensionRegistry.registerExtension('DocumentMenu', 'menuActionMenu', {
  id: 'openReadOnly',
  labelKey: 'documents.label.open.read.only',
  align: 'center',
  sortable: true,
  cssClass: 'text-truncate ',
  width: '190px',
  rank: 51,
  parent: 'openGroup',
  enabled: (file) => {
    return  file && !file.cloudDriveFolder
                 && Vue.prototype?.$supportedDocuments.filter(doc => doc.edit && doc.mimeType === file?.mimeType).length > 0;
  },
  enabledForMultiSelection: () => false,
  componentOptions: {
    vueComponent: Vue.options.components['open-read-only-menu-action'],
  },
});

extensionRegistry.registerExtension('DocumentMenu', 'menuActionMenu', {
  id: 'goLocation',
  labelKey: 'documents.label.go.location',
  align: 'center',
  sortable: true,
  cssClass: 'text-truncate ',
  width: '190px',
  rank: 53,
  parent: 'openGroup',
  enabled: (file,isMobile,currentView,searchResult) => {
    return (currentView === 'timeline'||searchResult)
            && file
            && !file.cloudDriveFolder;
  },
  enabledForMultiSelection: () => false,
  componentOptions: {
    vueComponent: Vue.options.components['open-location-menu-action'],
  },
});

extensionRegistry.registerExtension('DocumentMenu', 'menuActionMenu', {
  id: 'edit',
  labelKey: 'documents.label.edit',
  align: 'center',
  sortable: true,
  cssClass: 'text-truncate ',
  width: '190px',
  rank: 52,
  parent: 'openGroup',
  enabled: (file) => {
    return file && !file.cloudDriveFolder
                && file.acl.canEdit
                && (eXo.env.portal.spaceIdentityId === '' || file.creatorUserName!=='__system' || eXo.env.portal.isAdministrator)
                && Vue.prototype?.$supportedDocuments.filter(doc => doc.edit && doc.mimeType === file?.mimeType).length > 0;
  },
  enabledForMultiSelection: () => false,
  componentOptions: {
    vueComponent: Vue.options.components['edit-menu-action'],
  },
});

extensionRegistry.registerExtension('DocumentMenu', 'menuActionMenu', {
  id: 'rename',
  labelKey: 'documents.label.rename',
  align: 'center',
  sortable: true,
  cssClass: 'text-truncate',
  width: '190px',
  rank: 20,
  enabled: (file) => {
    return file && !file.cloudDriveFolder && file.acl.canEdit && (eXo.env.portal.spaceIdentityId === '' || file.creatorUserName!=='__system' || eXo.env.portal.isAdministrator);
  },
  enabledForMultiSelection: () => false,
  componentOptions: {
    vueComponent: Vue.options.components['rename-menu-action'],
  },
});


extensionRegistry.registerExtension('DocumentMenu', 'menuActionMenu', {
  id: 'move',
  labelKey: 'documents.label.move',
  align: 'center',
  sortable: true,
  cssClass: 'text-truncate',
  width: '190px',
  rank: 61,
  parent: 'copyMoveGroup',
  enabled: (file) => {
    return file && !file.cloudDriveFolder && file.acl.canEdit && (eXo.env.portal.spaceIdentityId === '' || file.creatorUserName!=='__system' || eXo.env.portal.isAdministrator);
  },
  enabledForMultiSelection: () => true,
  componentOptions: {
    vueComponent: Vue.options.components['move-menu-action'],
  },
});

extensionRegistry.registerExtension('DocumentMenu', 'menuActionMenu', {
  id: 'addCategory',
  labelKey: 'documents.label.addCategories',
  align: 'center',
  sortable: true,
  cssClass: 'text-truncate',
  width: '190px',
  rank: 30,
  enabled: (file) => {
    return file && file.acl.canEdit && !file.folder;
  },
  enabledForMultiSelection: () => false,
  componentOptions: {
    vueComponent: Vue.options.components['add-category-menu-action'],
  },
});

extensionRegistry.registerExtension('DocumentMenu', 'menuActionMenu', {
  id: 'duplicate',
  labelKey: 'documents.label.duplicate',
  align: 'center',
  sortable: true,
  cssClass: 'text-truncate',
  width: '190px',
  rank: 64,
  parent: 'copyMoveGroup',
  enabled: (file) => {
    return file && !file.cloudDriveFolder && file.acl.canEdit && (eXo.env.portal.spaceIdentityId === '' || file.creatorUserName!=='__system' || eXo.env.portal.isAdministrator);
  },
  enabledForMultiSelection: () => false,
  componentOptions: {
    vueComponent: Vue.options.components['duplicate-menu-action'],
  },
});

extensionRegistry.registerExtension('DocumentMenu', 'menuActionMenu', {
  id: 'shortcut',
  labelKey: 'documents.label.shortcut',
  align: 'center',
  sortable: true,
  cssClass: 'text-truncate',
  width: '190px',
  rank: 72,
  parent: 'visibilityGroup',
  enabled: (file) => {
    return file && !file.cloudDriveFolder && file.acl.canEdit && (eXo.env.portal.spaceIdentityId === '' || file.creatorUserName!=='__system' || eXo.env.portal.isAdministrator) && !file.sourceID;
  },
  enabledForMultiSelection: () => false,
  componentOptions: {
    vueComponent: Vue.options.components['shortcut-menu-action'],
  },
});


extensionRegistry.registerExtension('DocumentMenu', 'menuActionMenu', {
  id: 'visibility',
  labelKey: 'documents.label.visibility',
  align: 'center',
  sortable: true,
  cssClass: 'text-truncate',
  width: '190px',
  rank: 71,
  parent: 'visibilityGroup',
  enabled: (file) => {
    if (Vue.prototype.$shareDocumentSuspended) {
      return false;
    }
    return file && !file.cloudDriveFolder
                && file.acl.canEdit
                && (eXo.env.portal.spaceIdentityId === '' || file.creatorUserName!=='__system' || eXo.env.portal.isAdministrator)
                && !file.sourceID
                && !file.path?.includes('News Attachments');
  },
  enabledForMultiSelection: () => false,
  componentOptions: {
    vueComponent: Vue.options.components['visibility-menu-action'],
  },
});

extensionRegistry.registerExtension('DocumentMenu', 'menuActionMenu', {
  id: 'hide',
  labelKey: 'documents.label.hide',
  align: 'center',
  sortable: true,
  cssClass: 'text-truncate',
  width: '190px',
  rank: 100,
  enabled: (file,isMobile,currentView) => {
    return file && currentView === 'folder' && file.folder && !file.cloudDriveFolder && file.acl.canEdit && (eXo.env.portal.spaceIdentityId === '' || file.creatorUserName!=='__system' || eXo.env.portal.isAdministrator);
  },
  enabledForMultiSelection: () => false,
  componentOptions: {
    vueComponent: Vue.options.components['hide-menu-action'],
  },
});


extensionRegistry.registerExtension('DocumentMenu', 'menuActionMenu', {
  id: 'versionHistory',
  labelKey: 'documents.label.showVersionHistory',
  align: 'center',
  sortable: true,
  cssClass: 'text-truncate',
  width: '190px',
  rank: 81,
  parent: 'versionsGroup',
  enabled: (file) => {
    return file && !file.cloudDriveFolder && file.versionable;
  },
  enabledForMultiSelection: () => false,
  componentOptions: {
    vueComponent: Vue.options.components['versionHistory-menu-action'],
  },
});

extensionRegistry.registerExtension('DocumentMenu', 'menuActionMenu', {
  id: 'uploadNewVersion',
  labelKey: 'documents.label.upload.newVersion',
  align: 'center',
  sortable: true,
  cssClass: 'text-truncate',
  width: '190px',
  rank: 82,
  parent: 'versionsGroup',
  enabled: (file) => {
    if (!file.versionable || file.cloudDriveFolder) {
      return false;
    }
    return file.acl.canEdit;
  },
  enabledForMultiSelection: () => false,
  componentOptions: {
    vueComponent: Vue.options.components['upload-new-version-menu-action'],
  },
});

extensionRegistry.registerExtension('DocumentMenu', 'menuActionMenu', {
  id: 'download',
  labelKey: 'documents.label.download',
  align: 'center',
  sortable: true,
  cssClass: 'text-truncate',
  width: '190px',
  rank: 40,
  enabled: (file) => {
    if (Vue.prototype.$downloadDocumentSuspended) {
      return false;
    }
    return file && !file.cloudDriveFolder || file.sourceID;
  },
  enabledForMultiSelection: () => true,
  componentOptions: {
    vueComponent: Vue.options.components['download-menu-action'],
  },
});

extensionRegistry.registerExtension('DocumentMenu', 'menuActionMenu', {
  id: 'details',
  labelKey: 'documents.label.details',
  align: 'center',
  sortable: true,
  cssClass: 'text-truncate',
  width: '190px',
  rank: 14,
  enabled: (file, isMobile) => {
    return file && !file.cloudDriveFolder && !file.folder && isMobile;
  },
  enabledForMultiSelection: () => false,
  componentOptions: {
    vueComponent: Vue.options.components['details-menu-action'],
  },
});

extensionRegistry.registerExtension('DocumentMenu', 'menuActionMenu', {
  id: 'delete',
  labelKey: 'documents.label.delete',
  align: 'center',
  sortable: true,
  cssClass: 'text-truncate',
  width: '190px',
  rank: 110,
  enabled: (file) => {
    return file && !file.cloudDriveFolder && file.acl.canEdit && (eXo.env.portal.spaceIdentityId === '' || file.creatorUserName!=='__system' || eXo.env.portal.isAdministrator);
  },
  enabledForMultiSelection: () => true,
  componentOptions: {
    vueComponent: Vue.options.components['delete-menu-action'],
  },
});

extensionRegistry.registerExtension('DocumentMenu', 'menuActionMenu', {
  id: 'openInDesktop',
  labelKey: 'documents.label.openInDesktop',
  align: 'center',
  sortable: true,
  cssClass: 'text-truncate',
  width: '190px',
  rank: 54,
  parent: 'openGroup',
  enabled: (file, isMobile, currentView, isSearchResult, iconExtension) => {
    return !isMobile
      && iconExtension?.protocol
      && file?.acl?.canEdit
      && ((file.path.startsWith('/Groups/spaces/')
            && file.path.includes('/Documents/'))
          || (file.path.startsWith('/Users/')
              && file.path.includes(`/${eXo.env.portal.userName}/`)
              && file.path.includes('/Private/')));
  },
  componentOptions: {
    vueComponent: Vue.options.components['open-in-desktop-menu-action'],
  },
});

extensionRegistry.registerExtension('Documents', 'timelineViewHeader', {
  id: 'size',
  labelKey: 'documents.label.size',
  align: 'center',
  sortable: true,
  cssClass: 'text-truncate tooltip-marker',
  width: '100px',
  rank: 60,
  componentOptions: {
    vueComponent: Vue.options.components['documents-size-cell'],
  },
});

extensionRegistry.registerExtension('Documents', 'timelineViewHeader', {
  id: 'favorite',
  labelKey: 'documents.label.favorite',
  align: 'center',
  sortable: true,
  cssClass: 'text-truncate tooltip-marker',
  width: '120px',
  rank: 70,
  componentOptions: {
    vueComponent: Vue.options.components['documents-favorite-cell'],
  },
});

extensionRegistry.registerExtension('DocumentMobileFilterMenu', 'menuMobileFilterMenu', {
  id: 'extendSearchToContent',
  labelKey: 'documents.label.extendSearchToContent',
  align: 'center',
  sortable: true,
  cssClass: 'text-truncate',
  width: '190px',
  rank: 40,
  menuType: 'searchType',
  componentOptions: {
    vueComponent: Vue.options.components['extend-filter-action'],
  },
});
extensionRegistry.registerExtension('DocumentMobileFilterMenu', 'menuMobileFilterMenu', {
  id: 'quickFilter',
  labelKey: 'documents.label.quickFilter',
  align: 'center',
  sortable: true,
  cssClass: 'text-truncate',
  width: '190px',
  rank: 40,
  menuType: 'searchType',
  componentOptions: {
    vueComponent: Vue.options.components['quick-filter-action'],
  },
});

extensionRegistry.registerExtension('DocumentMobileFilterMenu', 'menuMobileFilterMenu', {
  id: 'mobileAdvancedFilter',
  labelKey: 'documents.label.mobileAdvancedFilter',
  align: 'center',
  sortable: true,
  cssClass: 'text-truncate',
  width: '190px',
  rank: 50,
  menuType: 'searchType',
  componentOptions: {
    vueComponent: Vue.options.components['mobile-advanced-filter-action'],
  },
});

extensionRegistry.registerExtension('DocumentMobileFilterMenu', 'menuMobileFilterMenu', {
  id: 'all',
  labelKey: 'documents.label.all',
  align: 'center',
  sortable: true,
  cssClass: 'text-truncate',
  width: '190px',
  rank: 40,
  menuType: 'filterOptions',
  componentOptions: {
    vueComponent: Vue.options.components['all-filter-action'],
  },
});

extensionRegistry.registerExtension('DocumentMobileFilterMenu', 'menuMobileFilterMenu', {
  id: 'favorites',
  labelKey: 'documents.label.favorites',
  align: 'center',
  sortable: true,
  cssClass: 'text-truncate',
  width: '190px',
  rank: 40,
  menuType: 'filterOptions',
  componentOptions: {
    vueComponent: Vue.options.components['favorite-filter-action'],
  },
});

extensionRegistry.registerExtension('DocumentMenu', 'menuActionMenu', {
  id: 'openGroup',
  labelKey: 'documents.label.menu.group.open',
  align: 'center',
  sortable: true,
  cssClass: 'text-truncate',
  width: '190px',
  rank: 50,
  icon: 'fas fa-book-open',
  enabled: (file, isMobile) => {
    return  !isMobile;
  },
  enabledForMultiSelection: () => false,
  type: 'group',
  componentOptions: {
    vueComponent: Vue.options.components['group-menu-action'],
  },
});

extensionRegistry.registerExtension('DocumentMenu', 'menuActionMenu', {
  id: 'copyMoveGroup',
  labelKey: 'documents.label.menu.group.copy.move',
  align: 'center',
  sortable: true,
  cssClass: 'text-truncate',
  width: '190px',
  rank: 60,
  icon: 'fa-arrows-alt',
  enabled: (file, isMobile) => {
    return  !isMobile;
  },
  enabledForMultiSelection: () => false,
  type: 'group',
  componentOptions: {
    vueComponent: Vue.options.components['group-menu-action'],
  },
});
extensionRegistry.registerExtension('DocumentMenu', 'menuActionMenu', {
  id: 'visibilityGroup',
  labelKey: 'documents.label.menu.group.visibility',
  align: 'center',
  sortable: true,
  cssClass: 'text-truncate',
  width: '190px',
  rank: 70,
  icon: 'fas fa-shield-alt',
  enabled: (file, isMobile) => {
    return  !isMobile;
  },
  enabledForMultiSelection: () => false,
  type: 'group',
  componentOptions: {
    vueComponent: Vue.options.components['group-menu-action'],
  },
});
extensionRegistry.registerExtension('DocumentMenu', 'menuActionMenu', {
  id: 'versionsGroup',
  labelKey: 'documents.label.menu.group.versions',
  align: 'center',
  sortable: true,
  cssClass: 'text-truncate',
  width: '190px',
  rank: 80,
  icon: 'fa fa-history',
  enabled: (file, isMobile) => {
    return  !isMobile;
  },
  enabledForMultiSelection: () => false,
  type: 'group',
  componentOptions: {
    vueComponent: Vue.options.components['group-menu-action'],
  },
});

extensionRegistry.registerExtension('DocumentMenu', 'menuActionMenu', {
  id: 'copy',
  labelKey: 'documents.label.copy',
  align: 'center',
  sortable: true,
  cssClass: 'text-truncate',
  width: '190px',
  rank: 62,
  parent: 'copyMoveGroup',
  enabled: (file) => {
    return file && !file.cloudDriveFolder && file.acl.canEdit && (eXo.env.portal.spaceIdentityId === '' || file.creatorUserName!=='__system' || eXo.env.portal.isAdministrator);
  },
  enabledForMultiSelection: () => false,
  componentOptions: {
    vueComponent: Vue.options.components['copy-menu-action'],
  },
});

extensionRegistry.registerExtension('DocumentMenu', 'menuActionMenu', {
  id: 'past',
  labelKey: 'documents.label.past',
  align: 'center',
  sortable: true,
  cssClass: 'text-truncate',
  width: '190px',
  rank: 63,
  parent: 'copyMoveGroup',
  enabled: (file) => {
    return file && file.folder && !file.cloudDriveFolder && file.acl.canEdit;
  },
  enabledForMultiSelection: () => false,
  componentOptions: {
    vueComponent: Vue.options.components['past-menu-action'],
  },
});

extensionRegistry.registerExtension('DocumentMenu', 'menuActionMenu', {
  id: 'actionsGroup',
  labelKey: 'documents.label.menu.group.actions',
  align: 'center',
  sortable: true,
  cssClass: 'text-truncate',
  width: '190px',
  rank: 90,
  icon: 'fa fa-plus',
  enabled: (file, isMobile) => {
    return  !isMobile;
  },
  enabledForMultiSelection: () => false,
  type: 'group',
  componentOptions: {
    vueComponent: Vue.options.components['group-menu-action'],
  },
});


extensionRegistry.registerExtension('DocumentMenu', 'menuActionMenu', {
  id: 'post',
  labelKey: 'documents.label.post',
  align: 'center',
  sortable: true,
  cssClass: 'text-truncate',
  width: '190px',
  rank: 91,
  parent: 'actionsGroup',
  enabled: (file) => {
    return file && file.canAdd && !file.folder && !file.cloudDriveFolder;
  },
  enabledForMultiSelection: () => false,
  componentOptions: {
    vueComponent: Vue.options.components['post-menu-action'],
  },
});




