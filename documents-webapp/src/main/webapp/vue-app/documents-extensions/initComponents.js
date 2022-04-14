import DocumentsFavoriteItem from './components/DocumentsFavoriteItem.vue';
const components = {
  'documents-favorite-item': DocumentsFavoriteItem,
};

for (const key in components) {
  Vue.component(key, components[key]);
}