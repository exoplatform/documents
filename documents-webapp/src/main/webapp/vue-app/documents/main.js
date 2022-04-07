import './initComponents.js';
import './extensions.js';

import * as documentFileService from './js/DocumentFileService.js';
if (!Vue.prototype.$documentFileService) {
  window.Object.defineProperty(Vue.prototype, '$documentFileService', {
    value: documentFileService,
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

//Temporarily used to remove VuetifyApp class on old documents view
export function removeClass() {
  if ( document.getElementById('UIJcrExplorerContainer').classList.contains('VuetifyApp') ){
    document.getElementById('UIJcrExplorerContainer').classList.remove('VuetifyApp');
  }
}