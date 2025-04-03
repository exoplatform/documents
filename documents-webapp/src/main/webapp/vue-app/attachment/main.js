import './initComponents.js';
import {installExtensions} from './extensions.js';
import '../documents-icons-extension/extensions.js';

Vue.use(Vuetify);

const vuetify = new Vuetify(eXo.env.portal.vuetifyPreset);

// getting language of the PLF
const lang = typeof eXo !== 'undefined' ? eXo.env.portal.language : 'en';

// should expose the locale resources as REST API

const url = `${eXo.env.portal.context}/${eXo.env.portal.rest}/i18n/bundle/locale.portlet.attachments-${lang}.json`;

// get overridden components if exist
if (extensionRegistry) {
  const components = extensionRegistry.loadComponents('attachmentApp');
  if (components && components.length > 0) {
    components.forEach(cmp => {
      Vue.component(cmp.componentName, cmp.componentOptions);
    });
  }
}

exoi18n.loadLanguageAsync(lang, url).then(i18n => {
  new Vue({i18n});
});

Vue.prototype.$supportedDocuments = extensionRegistry.loadExtensions('documents', 'supported-document-types');
document.addEventListener('documents-supported-document-types-updated', () => {
  Vue.prototype.$supportedDocuments = extensionRegistry.loadExtensions('documents', 'supported-document-types');
});
Vue.prototype.$documentsIconsExtension = extensionRegistry.loadExtensions('documents', 'documents-icons-extension');
document.addEventListener('documents-documents-icons-extension-updated', () => {
  Vue.prototype.$documentsIconsExtension = extensionRegistry.loadExtensions('documents', 'documents-icons-extension');
});

const appId = 'attachment';
let attachmentApp;
export function init(entityId, entityType, defaultDrive, defaultFolder, spaceId) {
  installExtensions();
  // getting locale resources
  exoi18n.loadLanguageAsync(lang, url).then(i18n => {
    // init Vue app when locale resources are ready
    attachmentApp = Vue.createApp({
      data: {
        entityId: entityId || '',
        entityType: entityType || '',
        defaultDrive: defaultDrive || null,
        defaultFolder: defaultFolder || '',
        spaceId: spaceId || '',
      },
      template: `<attachment
                  :entity-id=entityId
                  :entity-type=entityType
                  :default-drive=defaultDrive
                  :default-folder=defaultFolder
                  :space-id=spaceId
                  id="${appId}" />`,
      vuetify,
      i18n
    }, `#${appId}`, 'Attachment Drawer');
  });
}

export function openAttachmentsDrawer() {
  if (attachmentApp) {
    attachmentApp.$root.$emit('open-attachments-app-drawer');
  }
}

export function destroy() {
  if (attachmentApp) {
    attachmentApp.$destroy();
  }
}