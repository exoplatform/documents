<!--
 This file is part of the Meeds project (https://meeds.io/).

 Copyright (C) 2020 - 2025 Meeds Association contact@meeds.io

 This program is free software; you can redistribute it and/or
 modify it under the terms of the GNU Lesser General Public
 License as published by the Free Software Foundation; either
 version 3 of the License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU Lesser General Public License for more details.

 You should have received a copy of the GNU Lesser General Public License
 along with this program; if not, see <http://www.gnu.org/licenses/>.
-->
<template>
  <!-- Level-2 "Add a document" drawer rendered as a sibling of the level-1
       attachments drawer. Back arrow returns to level-1. Two sections:
       (1) New document = the pre-refactor doc-type picker + inline title input;
       (2) Templates = the documents found in the current drive's "Templates"
       folder (convention-based), rendered with the reused Documents-app grid
       card. Clicking a card copies that template into the current target folder
       and opens it, exactly like blank-create. -->
  <exo-drawer
    ref="createDocumentDrawer"
    class="createDocumentDrawer"
    right
    allow-expand
    :use-filter="showTemplateFilters"
    :filter-placeholder="$t('attachments.drawer.templates.search')"
    @filter-updated="onTemplateFilter"
    @expand-updated="drawerExpanded = $event">
    <template slot="title">
      <!-- Back arrow + title. The filter icon (→ inline header search) and the
           expand/full-screen icon are provided by exo-drawer itself (use-filter +
           allow-expand), so they sit at the platform-standard location next to the
           close button. -->
      <div class="d-flex align-center">
        <v-btn
          icon
          small
          class="ms-n2 me-1 flex-shrink-0"
          :aria-label="$t('attachments.drawer.back')"
          :title="$t('attachments.drawer.back')"
          @click="close()">
          <v-icon size="20">fa-arrow-left</v-icon>
        </v-btn>
        <span class="text-truncate">{{ $t('attachments.drawer.addDocument') }}</span>
      </div>
    </template>
    <template slot="content">
      <div
        class="createDocumentDrawerContent pt-4 pa-4"
        :class="{ 'createDocumentDrawerContent--expanded': drawerExpanded }">
        <!-- Section 1: create a blank document. Doc types are selectable cards
             (hover + selected state, keyboard-focusable). Picking one reveals
             the inline title input; the primary "Create" action lives in the
             footer. -->
        <div class="createDocumentTypes d-flex justify-center flex-wrap">
          <div
            v-for="doc in newDocumentActions"
            :key="doc.id"
            class="createDocumentTypeCard d-flex flex-column align-center justify-center text-center clickable"
            :class="{ 'createDocumentTypeCard--selected': selectedDocType.id === doc.id }"
            role="button"
            tabindex="0"
            :aria-pressed="selectedDocType.id === doc.id ? 'true' : 'false'"
            :aria-label="$t(doc.label)"
            @click="showNewDocInput(doc)"
            @keydown.enter.prevent="showNewDocInput(doc)"
            @keydown.space.prevent="showNewDocInput(doc)">
            <v-icon :color="doc.color" size="36">{{ doc.icon }}</v-icon>
            <span class="mt-2 text-sub-title">{{ $t(doc.label) }}</span>
          </div>
        </div>
        <v-text-field
          v-show="!NewDocInputHidden"
          ref="newDocInput"
          v-model="newDocTitleInput"
          :rules="documentTitleRules"
          :placeholder="$t('attachment.untitledDocument')"
          class="attachmentsCreateDocumentInput mt-4"
          outlined
          dense
          autofocus
          @keyup.enter="createNewDoc()">
          <span slot="append" class="text-color mt-1">{{ selectedDocType.extension }}</span>
        </v-text-field>
        <!-- Section 2: Templates. Elegant labelled separator introduces the
             templates area. Always shown: the documents of the current drive's
             "Templates" folder as reused Documents-app cards, or a neutral
             placeholder when that folder is absent/empty. -->
        <div class="templatesIntro d-flex align-center my-6">
          <v-divider />
          <span class="mx-4 text-sub-title text-no-wrap">{{ $t('attachments.drawer.templates.intro') }}</span>
          <v-divider />
        </div>
        <div
          v-if="templatesLoading"
          class="attachmentsTemplatesLoading d-flex justify-center py-6">
          <v-progress-circular
            indeterminate
            color="primary"
            size="28" />
        </div>
        <div
          v-else-if="templates.length"
          class="attachmentsTemplatesSection">
          <!-- Category chip bar — only in the expanded (full-screen) view, where
               there's room for it (self-hiding when templates aren't categorized).
               Free-text search lives in the drawer title bar (filter icon). -->
          <div
            v-if="drawerExpanded"
            class="attachmentsTemplatesFilters mb-3">
            <categories-filter
              v-model="selectedTemplateCategoryId"
              :space-id="templatesOwnerId"
              object-type="document"
              hide-on-empty
              class="attachmentsTemplateCategories" />
          </div>
          <!-- Full-width reused Documents-app cards, stacked. click.capture keeps
               the card's hover info icon opening the info drawer, while a plain
               click SELECTS the template and reveals an editable name field right
               under that card (option B: name-first, pre-filled with the
               template's name). The footer "Create" then creates it with that
               name. -->
          <div class="attachmentsTemplatesGrid">
            <div
              v-for="template in filteredTemplates"
              :key="template.id"
              class="attachmentsTemplateItem">
              <div
                class="attachmentsTemplateCard clickable"
                :class="{ 'attachmentsTemplateCard--selected': selectedTemplate && selectedTemplate.id === template.id }"
                role="button"
                tabindex="0"
                :aria-pressed="selectedTemplate && selectedTemplate.id === template.id ? 'true' : 'false'"
                :aria-label="template.name"
                @click.capture="onTemplateCardClick($event, template)"
                @keydown.enter.prevent="selectTemplate(template)"
                @keydown.space.prevent="selectTemplate(template)">
                <documents-item-card
                  :file="template"
                  :files="filteredTemplates"
                  :selected-documents="[]"
                  :show-details="true"
                  width="100%"
                  height="150px"
                  max-height="150px" />
              </div>
              <v-text-field
                v-if="selectedTemplate && selectedTemplate.id === template.id"
                ref="templateNameInput"
                v-model="newDocTitleInput"
                :rules="documentTitleRules"
                :placeholder="$t('attachment.untitledDocument')"
                class="attachmentsCreateDocumentInput mt-2"
                outlined
                dense
                autofocus
                @keyup.enter="submitCreate()">
                <span slot="append" class="text-color mt-1">{{ activeExtension }}</span>
              </v-text-field>
            </div>
          </div>
          <!-- The active category/search filter matched no template. -->
          <div
            v-if="!filteredTemplates.length"
            class="attachmentsTemplatesEmpty d-flex flex-column align-center justify-center text-center py-6">
            <v-icon size="36" color="grey">far fa-clone</v-icon>
            <span class="text-sub-title mt-3">{{ $t('attachments.drawer.templates.noMatch') }}</span>
          </div>
        </div>
        <div
          v-else
          class="attachmentsTemplatesEmpty d-flex flex-column align-center justify-center text-center py-6">
          <v-icon size="36" color="grey">far fa-clone</v-icon>
          <span class="text-sub-title mt-3">{{ templatesEmptyLabel }}</span>
          <!-- Bootstrap: when the Templates folder is missing and the user may
               create folders in this drive, offer a one-click creation. -->
          <v-btn
            v-if="!templatesFolderExists && canCreateTemplatesFolder"
            class="btn btn-primary mt-4"
            :loading="creatingTemplatesFolder"
            @click="createTemplatesFolder()">
            <v-icon size="16" class="me-2">fas fa-folder-plus</v-icon>
            {{ $t('attachments.drawer.templates.createFolder') }}
          </v-btn>
        </div>
      </div>
    </template>
    <template slot="footer">
      <div class="d-flex">
        <v-spacer />
        <v-btn
          class="btn me-3"
          @click="close()">
          {{ $t('attachments.drawer.cancel') }}
        </v-btn>
        <v-btn
          class="btn btn-primary"
          :disabled="createDisabled"
          @click="submitCreate()">
          {{ $t('attachments.drawer.create') }}
        </v-btn>
      </div>
    </template>
  </exo-drawer>
</template>

<script>
export default {
  props: {
    attachments: {
      type: Array,
      default: () => []
    },
    currentDrive: {
      type: Object,
      default: () => null
    },
    pathDestinationFolder: {
      type: Object,
      default: () => null
    },
    maxFilesCount: {
      type: Number,
      default: parseInt(`${eXo.env.portal.maxToUpload}`)
    },
    // Host context. 'attachment' (default) keeps the original attachment-composer
    // behavior (add to the level-1 attachments list). 'drive' is used when the
    // drawer is opened standalone by the Documents/Drive app: the created
    // document lands in the current folder and the host refreshes its file list
    // (via the emitted 'document-created' event).
    mode: {
      type: String,
      default: 'attachment'
    },
  },
  data() {
    return {
      newDocTitleInput: '',
      selectedDocType: {},
      // Name-first template create (option B): the currently picked template whose
      // editable title field is shown right under its card. Mutually exclusive with
      // a picked blank doc-type.
      selectedTemplate: null,
      NewDocInputHidden: true,
      extensionApp: 'attachment',
      newDocumentActionExtension: 'new-document-action',
      newDocumentActions: {},
      // Templates section: documents of the current drive's "Templates" folder.
      templates: [],
      templatesLoading: false,
      templateCreating: false,
      // Filters over the loaded templates (both applied client-side): the selected
      // category chip (id) + the free-text query fed by exo-drawer's built-in
      // header filter (use-filter → @filter-updated).
      selectedTemplateCategoryId: null,
      templateSearch: '',
      // True when the drawer is in its expanded (full-screen) state — drives the
      // centered side margins and shows the category chip bar. Fed by exo-drawer's
      // own @expand-updated event (same pattern as App Center's launcher drawer).
      drawerExpanded: false,
      // Bootstrap state: whether the drive's "Templates" folder exists, its parent
      // (drive root) id, and whether the current user may create a folder here.
      templatesFolderExists: false,
      rootFolderId: null,
      canCreateTemplatesFolder: false,
      creatingTemplatesFolder: false,
      TEMPLATES_FOLDER_NAME: 'Templates',
      TEMPLATES_LIST_LIMIT: 100,
      MAX_DOCUMENT_TITLE_LENGTH: 510,
      titleRegex: /[<\\>:"/|?*]/,
      documentTitleRules: [title => !title || title && title.trim().length <= this.MAX_DOCUMENT_TITLE_LENGTH - this.activeExtension.length || this.newDocTitleMaxLengthLabel,
        title => !this.titleRegex.test(title)],
    };
  },
  computed: {
    cleanedNewDocumentTitle() {
      return this.newDocTitleInput && this.newDocTitleInput.trim();
    },
    // Extension of the current create target: the picked template's own extension
    // in name-first template mode, else the picked blank doc-type's extension.
    templateExtension() {
      const name = this.selectedTemplate && this.selectedTemplate.name || '';
      const dot = name.lastIndexOf('.');
      return dot > -1 ? name.substring(dot) : '';
    },
    activeExtension() {
      return (this.selectedTemplate ? this.templateExtension : this.selectedDocType.extension) || '';
    },
    newDocumentTitle() {
      return this.cleanedNewDocumentTitle && `${this.cleanedNewDocumentTitle}${this.activeExtension}` || this.untitledNewDoc;
    },
    documentTitleMaxLengthReached() {
      return this.newDocumentTitle && this.newDocumentTitle.length > this.MAX_DOCUMENT_TITLE_LENGTH;
    },
    createDisabled() {
      // Enabled once either a blank doc type OR a template is picked and the
      // (optional) title is valid.
      const nothingPicked = this.NewDocInputHidden && !this.selectedTemplate;
      return nothingPicked || this.documentTitleMaxLengthReached || this.titleRegex.test(this.newDocumentTitle);
    },
    untitledNewDoc() {
      return `${this.$t('attachment.untitledDocument')}${this.activeExtension}`;
    },
    maxFileCountErrorLabel() {
      return this.$t('attachments.drawer.maxFileCount.error').replace('{0}', `<b> ${this.maxFilesCount} </b>`);
    },
    newDocCreationFailedLabel() {
      return this.$t('attachment.new.document.failed');
    },
    newDocTitleMaxLengthLabel() {
      return this.$t('attachment.new.document.title.max.length');
    },
    newDocTitleExistLabel() {
      return this.$t('attachment.document.title.exist');
    },
    // Identity id of the current drive owner (space or user). Kept in sync by the
    // host root ($root.ownerId) in both the attachment composer and the Drive app.
    templatesOwnerId() {
      return this.currentDrive && this.currentDrive.ownerId || this.$root.ownerId;
    },
    // Space vs personal context, for the placeholder word-swap. Prefer the drive
    // signal (drive name / spaceId), fall back to the portal space context.
    isSpaceContext() {
      const driveName = this.currentDrive && this.currentDrive.name || '';
      if (driveName) {
        return driveName.startsWith('.spaces.') || !!(this.currentDrive && this.currentDrive.spaceId);
      }
      return !!eXo.env.portal.spaceId;
    },
    templatesEmptyLabel() {
      // Folder exists but has no (readable) files yet.
      if (this.templatesFolderExists) {
        return this.$t('attachments.drawer.templates.empty.folderEmpty');
      }
      // Folder missing but the user can bootstrap it (a create button is shown).
      if (this.canCreateTemplatesFolder) {
        return this.$t('attachments.drawer.templates.empty.canCreate');
      }
      // Folder missing and the user cannot create it: neutral, context-aware text.
      return this.isSpaceContext
        ? this.$t('attachments.drawer.templates.empty.space')
        : this.$t('attachments.drawer.templates.empty.personal');
    },
    // Templates after the active category + free-text filters (both client-side).
    // Category ids are compared as strings — the chip component may emit the id as
    // a string while the item's categoryIds are numbers (or vice-versa).
    filteredTemplates() {
      let list = this.templates;
      // `!= null` catches both null and undefined — the category bar emits
      // `undefined` when you navigate back to root, which must mean "no filter".
      if (this.selectedTemplateCategoryId != null && this.selectedTemplateCategoryId !== '') {
        const selected = String(this.selectedTemplateCategoryId);
        list = list.filter(template => (template.categoryIds || []).some(id => String(id) === selected));
      }
      const query = (this.templateSearch || '').trim().toLowerCase();
      if (query) {
        list = list.filter(template => (template.name || '').toLowerCase().includes(query));
      }
      return list;
    },
    // Only surface the filter row once there are enough templates to warrant it —
    // small sets are fully visible, and the category chip bar self-hides anyway
    // when the templates aren't categorized.
    showTemplateFilters() {
      return this.templates.length > 3;
    },
    // Drive-root-relative path of the current target folder (blank-create target).
    // '' targets the drive root. Defensive against a path passed as an object.
    destinationRelativePath() {
      let path = this.pathDestinationFolder;
      if (path && typeof path === 'object') {
        path = path.path || '';
      }
      if (!path || path === '/') {
        return '';
      }
      if (path.startsWith('/')) {
        path = path.substring(1);
      }
      return path;
    },
  },
  created() {
    this.$root.$on(`${this.extensionApp}-${this.newDocumentActionExtension}-updated`, this.refreshNewDocumentsActions);
    this.$root.$on('hide-create-new-document-input', this.resetNewDocInput);
    this.refreshNewDocumentsActions();
  },
  methods: {
    open() {
      this.resetNewDocInput();
      this.loadTemplates();
      this.$refs.createDocumentDrawer.open();
    },
    close() {
      this.drawerExpanded = false;
      this.$refs.createDocumentDrawer.close();
    },
    refreshNewDocumentsActions() {
      const extensions = extensionRegistry.loadExtensions(this.extensionApp, this.newDocumentActionExtension);
      extensions.forEach(extension => {
        if (extension.id) {
          this.newDocumentActions[extension.id] = extension;
        }
      });
    },
    createNewDoc() {
      if (this.documentTitleMaxLengthReached) {
        return;
      }
      if (this.titleRegex.test(this.newDocumentTitle)) {
        this.$root.$emit('alert-message', this.$t('attachments.valid.title.error.message'), 'warning');
        return;
      }
      this.$root.$emit('start-loading-attachment-drawer');
      this.$attachmentService.createNewDoc(this.newDocumentTitle, this.selectedDocType.type, this.currentDrive.name, this.pathDestinationFolder)
        .then((resp) => {
          if (resp && resp.status && resp.status === 409) {
            this.$root.$emit('alert-message', this.newDocTitleExistLabel, 'error');
            this.$root.$emit('end-loading-attachment-drawer');
          } else {
            return resp;
          }
        })
        .then((doc) => this.manageNewCreatedDocument(doc))
        .catch(() => {
          this.$root.$emit('alert-message', this.newDocCreationFailedLabel, 'error');
          this.$root.$emit('end-loading-attachment-drawer');
        });
    },
    showNewDocInput(doc) {
      if (this.attachments.length >= this.maxFilesCount) {
        document.dispatchEvent(new CustomEvent('alert-message', {detail: {
          useHtml: true,
          alertType: 'error',
          alertMessage: this.maxFileCountErrorLabel,
        }}));
        return;
      }
      this.$refs.newDocInput.focus();
      // A blank doc-type and a template are mutually exclusive selections.
      this.selectedTemplate = null;
      this.NewDocInputHidden = false;
      this.selectedDocType = doc;
      this.newDocTitleInput = '';
    },
    // Name-first template create (option B): picking a template card selects it and
    // reveals an editable title field right under that card, pre-filled with the
    // template's base name. The footer "Create" then creates it with that name.
    selectTemplate(template) {
      // Same max-files guard as the blank-create path (attachment host only).
      if (this.mode !== 'drive' && this.attachments.length >= this.maxFilesCount) {
        document.dispatchEvent(new CustomEvent('alert-message', { detail: {
          useHtml: true,
          alertType: 'error',
          alertMessage: this.maxFileCountErrorLabel,
        } }));
        return;
      }
      // A template and a blank doc-type are mutually exclusive selections.
      this.selectedDocType = {};
      this.NewDocInputHidden = true;
      this.selectedTemplate = template;
      this.newDocTitleInput = this.templateBaseName(template);
      this.$nextTick(() => {
        const input = this.$refs.templateNameInput;
        const field = Array.isArray(input) ? input[0] : input;
        if (field && field.focus) {
          field.focus();
        }
      });
    },
    // Base name (without extension) of a template, used to pre-fill the name field.
    templateBaseName(template) {
      const name = template && template.name || '';
      const dot = name.lastIndexOf('.');
      return dot > -1 ? name.substring(0, dot) : name;
    },
    // Footer "Create": create from the picked template (name-first) or a blank doc.
    submitCreate() {
      if (this.selectedTemplate) {
        this.createFromTemplate(this.selectedTemplate);
      } else {
        this.createNewDoc();
      }
    },
    // exo-drawer built-in header filter (use-filter) → the typed query, applied
    // client-side over the loaded templates.
    onTemplateFilter(text) {
      this.templateSearch = text || '';
    },
    resetNewDocInput() {
      this.NewDocInputHidden = true;
      this.newDocTitleInput = '';
      // Clear the picked type/template too, so nothing keeps its selected ring on reopen.
      this.selectedDocType = {};
      this.selectedTemplate = null;
    },
    // openInEditor: blank-create always yields an editable office document, so it
    // defaults to true. Template create passes false for non-editable source
    // types (PDF, image, ...) which the OnlyOffice editor cannot open.
    manageNewCreatedDocument(doc, openInEditor = true) {
      if (doc && doc.id) {
        doc.drive = this.currentDrive.title;
        doc.date = doc.created;
        if (this.mode === 'drive') {
          // Drive host: let it drop the new document into the current folder
          // and refresh its own file list.
          this.$emit('document-created', doc);
        } else {
          // Attachment composer host: add it to the level-1 attachments list.
          this.$root.$emit('add-new-created-document', doc);
        }
        this.$root.$emit('alert-message', this.$t('attachments.upload.success'), 'success');
        this.resetNewDocInput();
        if (openInEditor) {
          window.open(`${eXo.env.portal.context}/${eXo.env.portal.portalName}/oeditor?docId=${doc.id}&backTo=${window.location.pathname}`, '_blank');
        }
        // Return to the level-1 attachments drawer (attachment host) or simply
        // close the standalone drawer (drive host).
        this.close();
      }
    },
    // Loads the documents of the current drive's "Templates" folder (convention).
    // Resolves the drive root (its id + the current user's edit permission, to gate
    // the bootstrap button), finds the child folder named "Templates", then lists
    // its files. Symlink templates (references to global/other-space template files)
    // are kept only when their target resolved for the current user; unreadable
    // ones are filtered out. Each file is enriched with the thumbnail / download /
    // icon fields the reused Documents-app card expects.
    loadTemplates() {
      this.templates = [];
      this.templatesFolderExists = false;
      this.rootFolderId = null;
      this.canCreateTemplatesFolder = false;
      this.selectedTemplateCategoryId = null;
      this.templateSearch = '';
      const ownerId = this.templatesOwnerId;
      if (!ownerId) {
        return;
      }
      this.templatesLoading = true;
      const rootCrumbPromise = this.$documentFileService.getBreadCrumbs(null, ownerId, '')
        .then(crumbs => (crumbs && crumbs.length ? crumbs[crumbs.length - 1] : null))
        .catch(() => null);
      Promise.all([
        rootCrumbPromise,
        this.$documentFileService.getDocumentItems({ ownerId, listingType: 'FOLDER' }, null, null, 0, this.TEMPLATES_LIST_LIMIT, 'creator')
      ])
        .then(([rootCrumb, rootItems]) => {
          this.rootFolderId = rootCrumb && rootCrumb.id || null;
          this.canCreateTemplatesFolder = !!(rootCrumb && rootCrumb.accessList && rootCrumb.accessList.canEdit);
          const templatesFolder = (rootItems || []).find(item => item.folder && item.name === this.TEMPLATES_FOLDER_NAME);
          this.templatesFolderExists = !!templatesFolder;
          if (!templatesFolder) {
            return [];
          }
          return this.$documentFileService.getDocumentItems({ ownerId, listingType: 'FOLDER', parentFolderId: templatesFolder.id }, null, null, 0, this.TEMPLATES_LIST_LIMIT, 'creator');
        })
        .then(files => {
          this.templates = (files || []).filter(file => !file.folder && this.isReadableTemplate(file)).map(file => this.enrichTemplate(file));
        })
        .catch(() => {
          this.templates = [];
        })
        .finally(() => {
          this.templatesLoading = false;
        });
    },
    // Creates the drive's "Templates" folder (bootstrap). Only reachable when the
    // user has edit permission on the drive root. Reloads the list afterwards so
    // the empty-folder state (with an upload hint) is shown.
    createTemplatesFolder() {
      const ownerId = this.templatesOwnerId;
      if (!ownerId || !this.rootFolderId || this.creatingTemplatesFolder) {
        return;
      }
      this.creatingTemplatesFolder = true;
      this.$documentFileService.createFolder(ownerId, this.rootFolderId, '', this.TEMPLATES_FOLDER_NAME)
        .then(() => {
          this.$root.$emit('alert-message', this.$t('attachments.drawer.templates.createFolder.success'), 'success');
          this.loadTemplates();
        })
        .catch(() => {
          this.$root.$emit('alert-message', this.$t('attachments.drawer.templates.createFolder.error'), 'error');
        })
        .finally(() => {
          this.creatingTemplatesFolder = false;
        });
    },
    // A template is a symlink when it carries a sourceID (the referenced file's id).
    // Symlinks to global/other-space template files are supported, but only when
    // the target actually resolved for the current user — an unreadable target
    // comes back without file characteristics (no mime type), so we drop it.
    isReadableTemplate(file) {
      if (!file.id) {
        return false;
      }
      if (!file.sourceID) {
        return true;
      }
      return !!(file.mimeType || file.mimetype);
    },
    // Delegates to the same centralized editability check DocumentsCardsView /
    // DocumentsFolderCardsView use ($root.isFileEditable, backed by the
    // 'supported-document-types' extension point) instead of a local regex —
    // it already covers every MS Office and LibreOffice/ODF type the deployed
    // OnlyOffice editor actually supports. Any other type (PDF, image, ...) is
    // still copied and added/refreshed but NOT opened in the editor, which
    // would otherwise error.
    isTemplateEditable(template) {
      return this.$root.isFileEditable(template);
    },
    // Mirrors DocumentsCardsView's file enrichment so the reused card resolves
    // its thumbnail / download URL / file icon from the $root shim.
    enrichTemplate(file) {
      file.image = this.$root.getImageUrl(file);
      file.downloadUrl = this.$root.getDownloadUrl(file);
      file.icon = this.$root.getFileIcon(file);
      return file;
    },
    // click.capture handler on the template card wrapper. Lets the card's own
    // info icon open the shared document-info-drawer; any other click on the
    // card selects that template and reveals its name field (option B).
    onTemplateCardClick(event, template) {
      if (event.target.closest('#attachment-info')) {
        return;
      }
      event.preventDefault();
      event.stopPropagation();
      this.selectTemplate(template);
    },
    // Copies the template node into the current target folder (JCR duplicate),
    // then reuses the exact blank-create post-processing (attachment list add /
    // Drive refresh + OnlyOffice open).
    createFromTemplate(template) {
      if (this.templateCreating) {
        return;
      }
      // Same max-files guard as the blank-create path (attachment host only).
      if (this.mode !== 'drive' && this.attachments.length >= this.maxFilesCount) {
        document.dispatchEvent(new CustomEvent('alert-message', { detail: {
          useHtml: true,
          alertType: 'error',
          alertMessage: this.maxFileCountErrorLabel,
        } }));
        return;
      }
      const ownerId = this.templatesOwnerId;
      // The user-provided name (option B), captured before the async chain.
      const targetName = this.newDocumentTitle;
      // When the template is a symlink to a global/other-space file, copy its
      // target (sourceID) so the new document holds the real content, not a link.
      const copySourceId = template.sourceID || template.id;
      this.templateCreating = true;
      this.$root.$emit('start-loading-attachment-drawer');
      this.resolveDestinationFolder(ownerId)
        .then(dest => {
          if (!dest || !dest.id) {
            throw new Error('Could not resolve the destination folder');
          }
          // Snapshot the destination's current file ids so we can identify the copy
          // by id-diff afterwards. The copy endpoint returns the destination folder
          // (not the new file), and matching by name is unreliable — the backend
          // de-duplicates a clashing name to "name (1)", which would make a name
          // match pick the WRONG node (e.g. the source template itself). The one
          // new id in the folder is unambiguously the copy.
          return this.listFolderFileIds(ownerId, dest.id).then(beforeIds => ({ dest, beforeIds }));
        })
        .then(({ dest, beforeIds }) => {
          // Primary path: copy the template into the target folder, then find the
          // node whose id is new. If the copy fails because a same-named file
          // already exists there (JCR "same name sibling" 500), duplicate in
          // place in the Templates folder instead (dedupe-safe there), then move
          // that copy into the actual destination with conflictAction 'keepBoth' —
          // the same JCR-side auto-dedupe ("(n)" appended to both name and title)
          // used by drag-and-drop moves elsewhere in the app — so a clash there is
          // deduped rather than leaving the file stranded in Templates.
          return this.$documentFileService.duplicateDocument(copySourceId, dest.id, ownerId)
            .then(() => this.findNewCopy(ownerId, dest.id, beforeIds))
            .catch(() => this.$documentFileService.duplicateDocument(copySourceId, null, ownerId)
              .then(newDoc => this.$documentFileService.moveDocument(ownerId, newDoc.id, dest.path, 'keepBoth')
                .then(() => newDoc)));
        })
        .then(newDoc => {
          if (!newDoc || !newDoc.id) {
            throw new Error('Error creating document from template');
          }
          // Apply the user-provided name (option B). Keep the copy as-is if the
          // rename clashes with an existing file (the user can rename in the editor).
          if (targetName && newDoc.name !== targetName) {
            return this.$documentFileService.renameDocument(ownerId, newDoc.id, targetName)
              .then(() => {
                newDoc.name = targetName;
                return newDoc;
              })
              .catch(() => newDoc);
          }
          return newDoc;
        })
        .then(newDoc => {
          newDoc.title = newDoc.name || targetName;
          newDoc.created = newDoc.created || newDoc.createdDate;
          // Only open OnlyOffice for editable office types; PDFs/images copied
          // from a template are not editable and would error in the editor.
          this.manageNewCreatedDocument(newDoc, this.isTemplateEditable(template));
        })
        .catch(() => {
          this.$root.$emit('alert-message', this.newDocCreationFailedLabel, 'error');
          this.$root.$emit('end-loading-attachment-drawer');
        })
        .finally(() => {
          this.templateCreating = false;
        });
    },
    // Resolves the current target folder (its JCR node id and absolute path) from
    // the drive-root-relative path via the breadcrumb endpoint. The last crumb is
    // the target folder; at the drive root (empty path) it is the drive root node.
    // The absolute path is used as the move destination.
    resolveDestinationFolder(ownerId) {
      return this.$documentFileService.getBreadCrumbs(null, ownerId, this.destinationRelativePath)
        .then(crumbs => {
          const last = crumbs && crumbs.length ? crumbs[crumbs.length - 1] : null;
          return last ? { id: last.id, path: last.path } : null;
        });
    },
    // Set of the current (non-folder) file ids in a folder, used to snapshot state
    // before a copy so the new node can be identified by id-diff afterwards.
    listFolderFileIds(ownerId, folderId) {
      return this.$documentFileService.getDocumentItems({ ownerId, listingType: 'FOLDER', parentFolderId: folderId }, null, null, 0, this.TEMPLATES_LIST_LIMIT, 'creator')
        .then(items => new Set((items || []).filter(item => !item.folder).map(item => item.id)))
        .catch(() => new Set());
    },
    // Locates the copy just created by the backend copy: the one file in the
    // destination folder whose id was not present before the copy. Matching by id
    // (not name) is robust against the backend de-duplicating a clashing name.
    findNewCopy(ownerId, destinationFolderId, beforeIds) {
      return this.$documentFileService.getDocumentItems({ ownerId, listingType: 'FOLDER', parentFolderId: destinationFolderId }, null, null, 0, this.TEMPLATES_LIST_LIMIT, 'creator')
        .then(items => {
          const created = (items || []).filter(item => !item.folder && !beforeIds.has(item.id));
          const byNewest = (a, b) => Number(b.modifiedDate || b.created || b.createdDate || 0) - Number(a.modifiedDate || a.created || a.createdDate || 0);
          return created.sort(byNewest)[0] || null;
        });
    }
  }
};
</script>
