import './initComponents.js';
import {installExtensions} from './extensions.js';
import '../documents-icons-extension/extensions.js';
import * as documentsUtils from '../../js/DocumentsUtils.js';

Vue.use(Vuetify);

if (!Vue.prototype.$documentsUtils) {
  window.Object.defineProperty(Vue.prototype, '$documentsUtils', {
    value: documentsUtils,
  });
}

const vuetify = new Vuetify(eXo.env.portal.vuetifyPreset);

// getting language of the PLF
const lang = typeof eXo !== 'undefined' ? eXo.env.portal.language : 'en';

// should expose the locale resources as REST API

const url = `/documents-portlet/i18n/locale.portlet.attachments?lang=${lang}`;

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