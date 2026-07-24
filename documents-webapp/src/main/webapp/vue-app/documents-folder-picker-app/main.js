/*
 * Copyright (C) 2025 eXo Platform SAS.
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

// Standalone host of the reusable Documents folder picker (EXO-88830).
//
// Lazily required (SHARED/DocumentsFolderPickerApp) by the site-wide bootstrap the
// first time 'open-documents-folder-picker' fires on a page. It mounts JUST the ONE
// existing folders-only drive explorer (attachments-drive-explorer-drawer, revamped
// in EXO-88721) on its own small Vue root, replicating the $root shim that root
// normally gets from the attachment app (vue-app/attachment/main.js): ownerId /
// selectedDrive / selectedPath / driveView / spaceId / settings + isMobile. The
// compose/staging attachment flow and its $root events are untouched — this is a
// second, tiny root around the same single component implementation.
import './initComponents.js';
// Provides Vue.prototype.$documentsIconsExtension the explorer uses for file icons.
import '../documents-icons-extension/extensions.js';

Vue.use(Vuetify);

const lang = typeof eXo !== 'undefined' ? eXo.env.portal.language : 'en';
// Same bundle the attachment app loads: all attachments.* keys the explorer,
// nav tree and breadcrumb resolve.
const i18nUrl = `/documents-portlet/i18n/locale.portlet.attachments?lang=${lang}`;

const APP_ID = 'documentsFolderPicker';

// Singleton root + memoised i18n so repeated opens reuse the mounted picker.
let pickerApp = null;
let i18nPromise = null;

/**
 * Public entry point, called by the bootstrap module with the detail of an
 * 'open-documents-folder-picker' event. Mounts the picker root on first use, then
 * opens the explorer drawer at the requested drive/folder.
 *
 * @param {Object} detail the open request: {defaultDrive?, defaultFolder?, spaceId?}
 * @returns {void}
 */
export function open(detail) {
  if (!i18nPromise) {
    i18nPromise = exoi18n.loadLanguageAsync(lang, i18nUrl);
  }
  i18nPromise.then(i18n => {
    if (!pickerApp) {
      pickerApp = createPickerApp(i18n);
    }
    pickerApp.openPicker(detail || {});
  });
}

/**
 * Creates the mount element under #vuetify-apps (so vuetify overlays/drawers get
 * the platform application styles) and builds the small root hosting the explorer.
 *
 * @param {Object} i18n the loaded vue-i18n instance
 * @returns {Vue} the mounted root instance
 */
function createPickerApp(i18n) {
  if (!document.getElementById(APP_ID)) {
    const appElement = document.createElement('div');
    appElement.id = APP_ID;
    const parent = document.querySelector('#vuetify-apps') || document.body;
    parent.appendChild(appElement);
  }
  return Vue.createApp({
    data: {
      // Open request state (fed to the explorer as props)
      defaultDrive: null,
      defaultFolder: '',
      spaceId: '',
      // Whether the current open ended with a confirmed selection (drives the
      // selected-vs-cancelled outcome when the drawer closes).
      picked: false,
      // --- $root shim replicated from vue-app/attachment/main.js: state the
      // reused explorer / DocumentsBreadcrumb / FolderTreeViewDrawer / TreeView
      // read and write on their $root.
      ownerId: eXo.env.portal.spaceIdentityId || eXo.env.portal.userIdentityId,
      selectedDrive: null,
      selectedPath: null,
      driveView: false,
      settings: {},
    },
    computed: {
      // Part of the replicated shim (breadcrumb mobile/desktop branch).
      isMobile() {
        return this.$vuetify.breakpoint.smAndDown;
      },
    },
    mounted() {
      // The explorer confirms a destination by emitting on its $root — this root.
      this.$on('select-destination-path-for-all', this.onDestinationSelected);
      // The inner exo-drawer emits 'closed' whenever the drawer closes (confirm,
      // cancel button, back arrow or overlay click) — the cancel signal.
      const drawer = this.$refs.explorer && this.$refs.explorer.$refs.driveExplorerDrawer;
      if (drawer) {
        drawer.$on('closed', this.onExplorerClosed);
      }
    },
    methods: {
      /**
       * Opens the explorer drawer for one picking round.
       *
       * @param {Object} detail {defaultDrive?, defaultFolder?, spaceId?}
       * @returns {void}
       */
      openPicker(detail) {
        this.picked = false;
        this.defaultDrive = detail.defaultDrive || null;
        this.defaultFolder = detail.defaultFolder || '';
        this.spaceId = detail.spaceId || '';
        this.$nextTick(() => {
          const explorer = this.$refs.explorer;
          if (!explorer) {
            return;
          }
          if (this.defaultFolder && !this.spaceId) {
            // Open the requested drive directly AT the requested folder in a
            // single navigation (the component's own open event always starts at
            // the drive root; navigating again afterwards would race it).
            explorer.modeFolderSelection = true;
            explorer.modeFolderSelectionForFile = false;
            explorer.resetSearch();
            explorer.resetSelection();
            explorer.currentDrive = this.requestedDrive();
            explorer.parentFolderId = null;
            explorer.folderPath = this.defaultFolder;
            explorer.navigate();
            explorer.$refs.driveExplorerDrawer.open();
          } else {
            // No initial folder: reuse the component's own bootstrap (handles
            // spaceId, defaultDrive and the My-Documents fallback).
            this.$emit('open-drive-explorer-drawer');
          }
        });
      },
      /**
       * The drive to open when an initial folder was requested: the caller's
       * defaultDrive completed with the current user's identity, falling back to
       * the personal drive.
       *
       * @returns {Object} a drive descriptor in the explorer's currentDrive shape
       */
      requestedDrive() {
        const drive = this.defaultDrive || {};
        return {
          name: drive.name || 'Personal Documents',
          title: drive.title || this.$t('attachments.drawer.myDocuments'),
          ownerId: drive.ownerId || eXo.env.portal.userIdentityId,
          spaceId: drive.spaceId || null,
          avatarUrl: drive.avatarUrl || null,
        };
      },
      /**
       * A destination folder was confirmed: republishes the explorer's legacy
       * payload as the public DOM event, enriched with the /v1/documents folder id
       * (breadcrumb tail) and the drive owner, so any caller can use whichever
       * addressing it needs (documents id OR legacy drive + relative path).
       *
       * @param {String} relativePath drive-root-relative node path ('' at the root)
       * @param {String} folderPath same value, kept by the legacy emit contract
       * @param {String} schemaFolder display path (drive title / folder titles)
       * @param {Object} drive the legacy drive descriptor {name, title, ...}
       * @returns {void}
       */
      onDestinationSelected(relativePath, folderPath, schemaFolder, drive) {
        const explorer = this.$refs.explorer;
        const breadcrumb = explorer && explorer.breadcrumb || [];
        const last = breadcrumb.length ? breadcrumb[breadcrumb.length - 1] : null;
        const currentDrive = explorer && explorer.currentDrive || {};
        this.picked = true;
        document.dispatchEvent(new CustomEvent('documents-folder-picker-selected', {
          detail: {
            // /v1/documents id of the picked folder (drive root included)
            folderId: last && last.id || null,
            folderName: last && last.name || null,
            // legacy ECMS addressing, exactly what the explorer computed
            driveName: drive && drive.name || currentDrive.name || null,
            drive: {name: drive && drive.name || currentDrive.name || null, title: drive && drive.title || currentDrive.title || null},
            relativePath: relativePath || '',
            path: relativePath || '',
            schemaFolder: schemaFolder || '',
            // owner identity of the picked drive (space identity for space drives)
            ownerId: currentDrive.ownerId || null,
            spaceId: currentDrive.spaceId || null,
          },
        }));
      },
      /**
       * The drawer closed: when no selection was confirmed in this round, tell the
       * caller the pick was abandoned.
       *
       * @returns {void}
       */
      onExplorerClosed() {
        if (this.picked) {
          this.picked = false;
          return;
        }
        document.dispatchEvent(new CustomEvent('documents-folder-picker-cancelled', {detail: {}}));
      },
    },
    // Same VuetifyApp/v-application wrapper markup the platform's portlet mount
    // points use (e.g. html/attachment.html), so the drawer gets the standard
    // vuetify application styling context.
    template: `<div id="${APP_ID}" class="VuetifyApp">
                <div data-app="true" class="v-application v-application--ltr">
                  <attachments-drive-explorer-drawer
                    ref="explorer"
                    :default-drive="defaultDrive"
                    :default-folder="defaultFolder"
                    :space-id="spaceId"
                    :create-entity-type-folder="false" />
                </div>
              </div>`,
    vuetify: new Vuetify(eXo.env.portal.vuetifyPreset),
    i18n,
  }, `#${APP_ID}`, 'Documents Folder Picker');
}
