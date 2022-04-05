export function initDocumentsExtensions() {
  extensionRegistry.registerComponent('favorite-file', 'favorite-drawer-item', {
    id: 'file',
    vueComponent: Vue.options.components['documents-favorite-item'],
  });
}