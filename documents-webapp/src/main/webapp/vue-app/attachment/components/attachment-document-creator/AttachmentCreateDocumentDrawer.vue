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
    right>
    <template slot="title">
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
      <div class="createDocumentDrawerContent pt-4 pa-4">
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
          class="attachmentsTemplatesGrid d-flex flex-wrap justify-center">
          <!-- Reused Documents-app card at the same size as the activity-stream
               attachment preview, so it reveals the file name + info icon on
               hover exactly like there. click.capture intercepts the card's own
               preview/open behavior so a plain click creates a document from that
               template, while the card's hover info icon still opens the info
               drawer. -->
          <div
            v-for="template in templates"
            :key="template.id"
            class="attachmentsTemplateCard ma-2 clickable"
            role="button"
            :aria-label="template.name"
            @click.capture="onTemplateCardClick($event, template)">
            <documents-item-card
              :file="template"
              :files="templates"
              :selected-documents="[]"
              :show-details="false"
              width="252px"
              height="210px"
              max-height="210px" />
          </div>
        </div>
        <div
          v-else
          class="attachmentsTemplatesEmpty d-flex flex-column align-center justify-center text-center py-6">
          <v-icon size="36" color="grey">far fa-clone</v-icon>
          <span class="text-sub-title mt-3">{{ templatesEmptyLabel }}</span>
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
          @click="createNewDoc()">
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
      NewDocInputHidden: true,
      extensionApp: 'attachment',
      newDocumentActionExtension: 'new-document-action',
      newDocumentActions: {},
      // Templates section: documents of the current drive's "Templates" folder.
      templates: [],
      templatesLoading: false,
      templateCreating: false,
      TEMPLATES_FOLDER_NAME: 'Templates',
      TEMPLATES_LIST_LIMIT: 100,
      MAX_DOCUMENT_TITLE_LENGTH: 510,
      titleRegex: /[<\\>:"/|?*]/,
      documentTitleRules: [title => !title || title && title.trim().length <= this.MAX_DOCUMENT_TITLE_LENGTH - this.selectedDocType.extension.length || this.newDocTitleMaxLengthLabel,
        title => !this.titleRegex.test(title)],
    };
  },
  computed: {
    cleanedNewDocumentTitle() {
      return this.newDocTitleInput && this.newDocTitleInput.trim();
    },
    newDocumentTitle() {
      return this.cleanedNewDocumentTitle && `${this.cleanedNewDocumentTitle}${this.selectedDocType.extension}` || this.untitledNewDoc;
    },
    documentTitleMaxLengthReached() {
      return this.newDocumentTitle && this.newDocumentTitle.length > this.MAX_DOCUMENT_TITLE_LENGTH;
    },
    createDisabled() {
      // Enabled only once a doc type is picked and the (optional) title is valid.
      return this.NewDocInputHidden || this.documentTitleMaxLengthReached || this.titleRegex.test(this.newDocumentTitle);
    },
    untitledNewDoc() {
      return `${this.$t('attachment.untitledDocument')}${this.selectedDocType.extension}`;
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
      return this.isSpaceContext
        ? this.$t('attachments.drawer.templates.empty.space')
        : this.$t('attachments.drawer.templates.empty.personal');
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
      this.NewDocInputHidden = false;
      this.selectedDocType = doc;
    },
    resetNewDocInput() {
      this.NewDocInputHidden = true;
      this.newDocTitleInput = '';
      // Clear the picked type too, so no card keeps its selected ring on reopen.
      this.selectedDocType = {};
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
    // Lists the drive root (ownerId only), finds the child folder named
    // "Templates", then lists its files. Enriches each file with the thumbnail /
    // download / icon fields the reused Documents-app card expects.
    loadTemplates() {
      this.templates = [];
      const ownerId = this.templatesOwnerId;
      if (!ownerId) {
        return;
      }
      this.templatesLoading = true;
      this.$documentFileService.getDocumentItems({ ownerId, listingType: 'FOLDER' }, null, null, 0, this.TEMPLATES_LIST_LIMIT, 'creator')
        .then(rootItems => {
          const templatesFolder = (rootItems || []).find(item => item.folder && item.name === this.TEMPLATES_FOLDER_NAME);
          if (!templatesFolder) {
            return [];
          }
          return this.$documentFileService.getDocumentItems({ ownerId, listingType: 'FOLDER', parentFolderId: templatesFolder.id }, null, null, 0, this.TEMPLATES_LIST_LIMIT, 'creator');
        })
        .then(files => {
          this.templates = (files || []).filter(file => !file.folder).map(file => this.enrichTemplate(file));
        })
        .catch(() => {
          this.templates = [];
        })
        .finally(() => {
          this.templatesLoading = false;
        });
    },
    // True only for the office document types that the blank-create flow itself
    // produces (Word / Spreadsheet / Presentation, incl. legacy and ODF). Those
    // open in the OnlyOffice editor exactly like a blank create. Any other type
    // (PDF, image, ...) is still copied and added/refreshed but NOT opened in the
    // editor, which would otherwise error.
    isTemplateEditable(template) {
      const mimeType = template && (template.mimeType || template.mimetype) || '';
      return /(officedocument|opendocument|msword|ms-word|ms-excel|ms-powerpoint)/i.test(mimeType);
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
    // card creates a document from that template.
    onTemplateCardClick(event, template) {
      if (event.target.closest('#attachment-info')) {
        return;
      }
      event.preventDefault();
      event.stopPropagation();
      this.createFromTemplate(template);
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
      this.templateCreating = true;
      this.$root.$emit('start-loading-attachment-drawer');
      this.resolveDestinationFolderId(ownerId)
        .then(destinationFolderId => {
          if (!destinationFolderId) {
            throw new Error('Could not resolve the destination folder');
          }
          return this.$documentFileService.duplicateDocument(template.id, destinationFolderId, ownerId);
        })
        .then(newDoc => {
          if (newDoc && newDoc.id) {
            // AbstractNodeEntity carries name/createdDate; the downstream handlers
            // expect title/created.
            newDoc.title = newDoc.title || newDoc.name;
            newDoc.created = newDoc.created || newDoc.createdDate;
            // Only open OnlyOffice for editable office types; PDFs/images copied
            // from a template are not editable and would error in the editor.
            this.manageNewCreatedDocument(newDoc, this.isTemplateEditable(template));
          } else {
            throw new Error('Error creating document from template');
          }
        })
        .catch(() => {
          this.$root.$emit('alert-message', this.newDocCreationFailedLabel, 'error');
          this.$root.$emit('end-loading-attachment-drawer');
        })
        .finally(() => {
          this.templateCreating = false;
        });
    },
    // Resolves the JCR node id of the current target folder (required by the
    // duplicate endpoint's destinationId) from the drive-root-relative path via
    // the breadcrumb endpoint. The last crumb is the target folder; at the drive
    // root (empty path) it is the drive root node.
    resolveDestinationFolderId(ownerId) {
      return this.$documentFileService.getBreadCrumbs(null, ownerId, this.destinationRelativePath)
        .then(crumbs => {
          return crumbs && crumbs.length ? crumbs[crumbs.length - 1].id : null;
        });
    }
  }
};
</script>
