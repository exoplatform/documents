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
       (2) Templates = a placeholder empty-state, scaffolded for a future
       templates backend (none exists yet). -->
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
             (currently empty) templates area — scaffolded for a future backend. -->
        <div class="templatesIntro d-flex align-center my-6">
          <v-divider />
          <span class="mx-4 text-sub-title text-no-wrap">{{ $t('attachments.drawer.templates.intro') }}</span>
          <v-divider />
        </div>
        <div class="attachmentsTemplatesEmpty d-flex flex-column align-center justify-center text-center py-6">
          <v-icon size="36" color="grey">far fa-clone</v-icon>
          <span class="text-sub-title mt-3">{{ $t('attachments.drawer.templates.empty') }}</span>
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
  },
  data() {
    return {
      newDocTitleInput: '',
      selectedDocType: {},
      NewDocInputHidden: true,
      extensionApp: 'attachment',
      newDocumentActionExtension: 'new-document-action',
      newDocumentActions: {},
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
  },
  created() {
    this.$root.$on(`${this.extensionApp}-${this.newDocumentActionExtension}-updated`, this.refreshNewDocumentsActions);
    this.$root.$on('hide-create-new-document-input', this.resetNewDocInput);
    this.refreshNewDocumentsActions();
  },
  methods: {
    open() {
      this.resetNewDocInput();
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
    manageNewCreatedDocument(doc) {
      if (doc && doc.id) {
        doc.drive = this.currentDrive.title;
        doc.date = doc.created;
        this.$root.$emit('add-new-created-document', doc);
        this.$root.$emit('alert-message', this.$t('attachments.upload.success'), 'success');
        this.resetNewDocInput();
        window.open(`${eXo.env.portal.context}/${eXo.env.portal.portalName}/oeditor?docId=${doc.id}&backTo=${window.location.pathname}`, '_blank');
        // Return to the level-1 attachments drawer so the freshly created
        // document is visible in the populated attachments list.
        this.close();
      }
    }
  }
};
</script>
