import './initComponents.js';
import './extensions.js';

import * as documentFileService from './js/DocumentFileService.js';
import * as documentsUtils from './js/DocumentsUtils.js';

if (!Vue.prototype.$documentFileService) {
  window.Object.defineProperty(Vue.prototype, '$documentFileService', {
    value: documentFileService,
  });
}

if (!Vue.prototype.$documentsUtils) {
  window.Object.defineProperty(Vue.prototype, '$documentsUtils', {
    value: documentsUtils,
  });
}

// get overrided components if exists
if (extensionRegistry) {
  const components = extensionRegistry.loadComponents('Documents');
  if (components && components.length > 0) {
    components.forEach(cmp => {
      Vue.component(cmp.componentName, cmp.componentOptions);
    });
  }
}

Vue.use(Vuetify);
const vuetify = new Vuetify(eXo.env.portal.vuetifyPreset);

const appId = 'DocumentsApplication';

//getting language of the PLF
const lang = eXo && eXo.env.portal.language || 'en';

//should expose the locale ressources as REST API 
const url = `${eXo.env.portal.context}/${eXo.env.portal.rest}/i18n/bundle/locale.portlet.Documents-${lang}.json`;

Vue.prototype.$transferRulesService.getDocumentsTransferRules().then(rules => {
  Vue.prototype.$shareDocumentSuspended = rules.sharedDocumentStatus === 'true';
  Vue.prototype.$downloadDocumentSuspended = rules.downloadDocumentStatus === 'true';
});
export function init() {
  exoi18n.loadLanguageAsync(lang, url).then(i18n => {
    // init Vue app when locale ressources are ready
    Vue.createApp({
      template: `<documents-main id="${appId}" />`,
      vuetify,
      i18n
    }, `#${appId}`, 'Documents');
  });
  //Temporarily used to add VuetifyApp class on new documents view
  if ( !document.getElementById('UIJcrExplorerContainer').classList.contains('VuetifyApp') ){
    document.getElementById('UIJcrExplorerContainer').classList.add('VuetifyApp');
  }
}

export function initSwitchApp() {
  exoi18n.loadLanguageAsync(lang, url).then(i18n => {
    // init Vue app when locale ressources are ready
    Vue.createApp({
      template: '<switch-new-document id="#newAppSwitch" />',
      vuetify,
      i18n
    }, '#newAppSwitch', 'SwitchDocuments');
  });
}
