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
        // --- $root shim state consumed by the reused Documents-app components
        // (DocumentsBreadcrumb / DocumentItemCard / DocumentFolderCard). The
        // drive explorer drawer keeps these in sync with the currently opened
        // drive/folder so those components resolve owner, drive and path.
        ownerId: eXo.env.portal.spaceIdentityId || eXo.env.portal.userIdentityId,
        selectedDrive: null,
        selectedPath: null,
        driveView: false,
        settings: {},
      },
      computed: {
        // Used by DocumentItemCard (this.$root.isMobile) and passed to
        // DocumentsBreadcrumb.
        isMobile() {
          return this.$vuetify.breakpoint.smAndDown;
        },
      },
      methods: {
        // The getters below are copied verbatim from the Documents app root
        // (vue-app/documents/main.js) so the reused leaf cards behave
        // identically. They build thumbnail/download URLs and file icons from
        // the file object and rely on $documentsUtils / $documentsIconsExtension
        // / $supportedDocuments, all exposed on Vue.prototype by the attachment
        // bundle's initComponents.js.
        safeDecodeURIComponent(name) {
          if (!name) {
            return '';
          }
          try {
            return decodeURIComponent(name.replace(/%25/g, '%').replace(/%([^2][^5])/g, '%25$1'));
          } catch (e) {
            return name;
          }
        },
        getFileIcon(file) {
          return this.$documentsIconsExtension?.[0]?.get?.(file?.mimeType) || 'fas fa-file';
        },
        isFileEditable(file) {
          return this.$supportedDocuments?.some(doc => doc.edit && doc.mimeType === file.mimeType) ?? false;
        },
        isFileReadable(file) {
          return this.$supportedDocuments?.some(doc => doc.mimeType === file.mimeType) ?? false;
        },
        getImageUrl(file) {
          file.readable = this.isFileReadable(file);
          return this.$documentsUtils.getThumbnailUrl(file, '250x250', file.lastModified);
        },
        getDownloadUrl(file) {
          return this.$documentsUtils.getDownloadUrl(file.id, file.lastModified);
        },
        openInEditMode(file) {
          const fileId = file.sourceID ? file.sourceID : file.id;
          this.$documentsUtils.openLink(`${eXo.env.portal.context}/${eXo.env.portal.metaPortalName}/oeditor?docId=${fileId}&backTo=${window.location.pathname}`, '_blank');
        },
        openInReadOnlyMode(file) {
          const fileId = file.sourceID ? file.sourceID : file.id;
          this.$documentsUtils.openLink(`${eXo.env.portal.context}/${eXo.env.portal.metaPortalName}/oeditor?docId=${fileId}&mode=view&backTo=${window.location.pathname}`, '_blank');
        },
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