import DocumentsUserSettings from './components/DocumentsUserSettings.vue';

const components = {
  'documents-user-settings': DocumentsUserSettings,
};

for (const key in components) {
  Vue.component(key, components[key]);
}
