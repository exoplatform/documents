import './initComponents.js';

// get overrided components if exists
if (extensionRegistry) {
  const components = extensionRegistry.loadComponents('DocumentsUserSettings');
  if (components && components.length > 0) {
    components.forEach(cmp => {
      Vue.component(cmp.componentName, cmp.componentOptions);
    });
  }
}

const appId = 'DocumentsUserSettingsApplication';
const lang = eXo?.env?.portal?.language || 'en';
const url = `/social/i18n/locale.portlet.social.UserSettings?lang=${lang}`;

document.dispatchEvent(new CustomEvent('displayTopBarLoading'));
export function init() {
  exoi18n.loadLanguageAsync(lang, url).then(i18n => {
    // init Vue app when locale ressources are ready
    Vue.createApp({
      mounted() {
        document.dispatchEvent(new CustomEvent('hideTopBarLoading'));
      },
      template: `<documents-user-settings id="${appId}" />`,
      vuetify: Vue.prototype.vuetifyOptions,
      i18n
    }, `#${appId}`, 'User Settings Agenda');
  });
}
