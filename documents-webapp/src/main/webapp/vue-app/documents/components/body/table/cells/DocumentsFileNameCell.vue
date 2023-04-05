<template>
  <div
    class="d-flex flex-nowrap"
    @touchstart="touchStart"
    @touchend="cancelTouch"
    @touchmove="cancelTouch">
    <div
      v-if="documentMultiSelectionActive"
      class="ma-auto">
      <documents-selection-cell
        class="ms-2 me-2"
        v-if="isMobile"
        :file="file"
        :select-all-checked="selectAllChecked"
        :selected-documents="selectedDocuments" />
    </div>
    <a
      class="attachment d-flex flex-nowrap text-color not-clickable text-decoration-none openPreviewDoc width-document-title">
      <div class="mt-auto mb-auto">
        <v-progress-circular
          v-if="loading"
          indeterminate
          size="16" />
        <i
          v-else-if="file.cloudDriveFolder "
          class="fas fa-folder driveFolderIcon">
          <i 
            class="fa-hdd driveFolderContentIcon"></i>
        </i>
        <v-icon
          v-else
          :size="isMobile && 32 || 22"
          :color="icon.color">{{ icon.class }}</v-icon>
      </div>
      <div class="mt-auto mb-auto width-document-title">
        <div
          v-if="!editNameMode"
          class="document-title clickable hover-underline d-inline-flex"
          :title="title"
          @click="openPreview()">
          <div
            v-sanitized-html="title"
            class="document-name ms-4"
            :class="title.includes('</b>') ? '' : 'text-truncate'">
          </div>
          <div
            v-sanitized-html="fileType"
            class="document-type ms-0">
          </div>
          <v-icon
            v-if="file.sourceID"
            size="13"
            class="pe-1 iconStyle ms-1">
            mdi-link-variant
          </v-icon>
        </div>
        <documents-file-edit-name-cell
          v-if="editNameMode"
          :file="file"
          :file-name="fileName"
          :file-type="fileType"
          :is-mobile="isMobile"
          :edit-name-mode="editNameMode" />
        <div v-if="isMobile" class="d-flex">
          <date-format
            v-if="!editNameMode"
            :value="lastUpdated"
            :format="fullDateFormat"
            class="document-time text-light-color text-no-wrap" />
          <documents-visibility-cell
            :file="file"
            :is-mobile="isMobile"
            :selected-view="selectedView" />
        </div>
      </div>
    </a>
    <v-spacer />
    <documents-info-details-cell
      v-if="!isMobile"
      :file="file"
      :is-mobile="isMobile"
      :class="editNameMode ? '' : 'button-info-details'" />
    <div
      class="ma-auto"
      :id="`document-action-menu-cel-${file.id}`">
      <v-tooltip bottom>
        <template #activator="{ on, attrs }">
          <v-btn
            icon
            small
            v-bind="attrs"
            v-on="on">
            <v-icon
              v-show="isMobile || menuDisplayed"
              :size="isMobile ? 14 : 18"
              class="clickable text-sub-title"
              :class="editNameMode ? '' : 'button-document-action'"
              @click="displayActionMenu()">
              mdi-dots-vertical
            </v-icon>
          </v-btn>
          <v-menu
            v-model="menuDisplayed"
            :attach="`#document-action-menu-cel-${file.id}`"
            transition="slide-x-reverse-transition"
            :content-class="isMobile ? 'documentActionMenuMobile' : 'documentActionMenu'"
            offset-y
            offset-x
            close-on-click
            absolute>
            <documents-actions-menu
              :file="file"
              :is-mobile="isMobile" />
          </v-menu>
        </template>
        <span>
          {{ menuActionTooltip }}
        </span>
      </v-tooltip>
    </div>
  </div>
</template>
<script>
import ntFileExtension from '../../../../json/NtFileExtension.json';
export default {

  props: {
    file: {
      type: Object,
      default: null,
    },
    query: {
      type: String,
      default: null,
    },
    extension: {
      type: Object,
      default: null,
    },
    isMobile: {
      type: Boolean,
      default: false
    },
    selectedView: {
      type: String,
      default: null
    },
    selectedDocuments: {
      type: Array,
      default: () => []
    },
    selectAllChecked: {
      type: Boolean,
      default: false,
    },
  },
  data: () => ({
    loading: false,
    menuDisplayed: false,
    waitTimeUntilCloseMenu: 200,
    fileToEditId: -1,
    fullDateFormat: {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    },
    accentMap: {
      ae: '(ae|æ|ǽ|ǣ)',
      a: '(a|á|ă|ắ|ặ|ằ|ẳ|ẵ|ǎ|â|ấ|ậ|ầ|ẩ|ẫ|ä|ǟ|ȧ|ǡ|ạ|ȁ|à|ả|ȃ|ā|ą|ᶏ|ẚ|å|ǻ|ḁ|ⱥ|ã)',
      c: '(c|ć|č|ç|ḉ|ĉ|ɕ|ċ|ƈ|ȼ)',
      e: '(e|é|ĕ|ě|ȩ|ḝ|ê|ế|ệ|ề|ể|ễ|ḙ|ë|ė|ẹ|ȅ|è|ẻ|ȇ|ē|ḗ|ḕ|ⱸ|ę|ᶒ|ɇ|ẽ|ḛ)',
      i: '(i|í|ĭ|ǐ|î|ï|ḯ|ị|ȉ|ì|ỉ|ȋ|ī|į|ᶖ|ɨ|ĩ|ḭ)',
      n: '(n|ń|ň|ņ|ṋ|ȵ|ṅ|ṇ|ǹ|ɲ|ṉ|ƞ|ᵰ|ᶇ|ɳ|ñ)',
      o: '(o|ó|ŏ|ǒ|ô|ố|ộ|ồ|ổ|ỗ|ö|ȫ|ȯ|ȱ|ọ|ő|ȍ|ò|ỏ|ơ|ớ|ợ|ờ|ở|ỡ|ȏ|ō|ṓ|ṑ|ǫ|ǭ|ø|ǿ|õ|ṍ|ṏ|ȭ)',
      u: '(u|ú|ŭ|ǔ|û|ṷ|ü|ǘ|ǚ|ǜ|ǖ|ṳ|ụ|ű|ȕ|ù|ủ|ư|ứ|ự|ừ|ử|ữ|ȗ|ū|ṻ|ų|ᶙ|ů|ũ|ṹ|ṵ)'
    },
    icon: null,
    touchTimer: null,
  }),
  computed: {
    isFileEditable() {
      const type = this.file && this.file.mimeType || '';
      return this.$supportedDocuments && this.$supportedDocuments.filter(doc => doc.edit && doc.mimeType === type && !this.file.cloudDriveFile).length > 0;
    },
    documentMultiSelectionActive() {
      return eXo?.env?.portal?.documentMultiSelection && this.$vuetify.breakpoint.width >= 600;
    },
    title() {
      let docTitle = this.fileName;
      try {
        docTitle = decodeURI(this.fileName);
      } catch (error) {
        // Nothing to do, title contains % character but it represent not an encoded character
        // No need to decode the title
      }
      if (this.query){
        docTitle = this.highlightSearchResult(docTitle,this.query);
      }
      return docTitle;
    },
    lastUpdated() {
      return this.file && (this.file.modifiedDate || this.file.createdDate) || '';
    },
    fileName() {
      return this.file.name.lastIndexOf('.') >= 0 && !this.file.folder ? this.file.name.substring(0,this.file.name.lastIndexOf('.')):this.file.name;
    },
    editNameMode() {
      return this.file.id===this.fileToEditId;
    },
    fileType() {
      //get extension from the filetypeextension if file name haven't extention 
      let fileType = this.file.name.lastIndexOf('.') >= 0 && !this.file.folder ? this.file.name.substring(this.file.name.lastIndexOf('.')) : ntFileExtension[this.file.mimeType] || '' ;
      if (this.query && !this.extendedSearch){
        fileType = this.highlightSearchResult(fileType,this.query);      
      }
      return fileType;
    },
    menuActionTooltip() {
      return this.$t('documents.label.menu.action.tooltip');
    },
  },
  created() {
    $(document).on('mousedown', () => {
      if (this.menuDisplayed) {
        window.setTimeout(() => {
          $(`#document-action-menu-cel-${this.file.id}`).parent().parent().parent().parent().css('background', '#fff');
          this.menuDisplayed = false;
        }, this.waitTimeUntilCloseMenu);
      }
    });
    this.$root.$on('update-file-name', this.editFileName);
    this.$root.$on('cancel-edit-mode', this.cancelEditMode);
    this.getFileIcon();
  },
  beforeDestroy() {
    this.$root.$off('update-file-name', this.editFileName);
    this.$root.$off('cancel-edit-mode', this.cancelEditMode);
  },
  methods: {
    touchStart() {
      if (!this.documentMultiSelectionActive) {
        return;
      }
      this.touchTimer = setTimeout(() => {
        this.touchTimer = null;
        this.$root.$emit('show-selection-input', this.file, true);
      }, 600);
    },
    cancelTouch() {
      clearTimeout(this.touchTimer);
    },
    getFileIcon() {
      const extensions = extensionRegistry.loadExtensions('documents', 'documents-icons-extension');
      if (this.file?.folder) {
        this.icon = extensions[0].get('folder');
      } else {
        let extension = extensions[0].get(this.file?.mimeType);
        if (!extension) {
          extension = extensions[0].get('file');
        }
        this.icon = extension;
      }
    },
    editFileName(file) {
      if (this.file.id === file.id){
        this.fileToEditId = file.id;
      }
    },
    cancelEditMode(file) {
      if (this.file.id === file.id) {
        this.fileToEditId = -1;
      }
    },
    fileInfo() {
      return `${this.$t('documents.preview.updatedOn')} ${this.absoluteDateModified()} ${this.$t('documents.preview.updatedBy')} ${this.file.lastEditor} ${this.file.size}`;
    },
    absoluteDateModified(options) {
      const lang = eXo && eXo.env && eXo.env.portal && eXo.env.portal.language || 'en';
      return new Date(this.file.date).toLocaleString(lang, options).split('/').join('-');
    },
    openInEditMode(file) {
      window.open(`${eXo.env.portal.context}/${eXo.env.portal.portalName}/oeditor?docId=${file.id}&source=peview`, '_blank');
    },
    openPreview() {
      this.loading = true;
      if (this.file && this.file.folder) {
        this.$root.$emit('document-open-folder', this.file);
      } else if (this.isFileEditable && this.file.acl.canEdit)  {
        this.openInEditMode(this.file);
        this.loading = false;
      } else {
        const id = this.file.id;
        this.$attachmentService.getAttachmentById(id)
          .then(attachment => {
            documentPreview.init({
              doc: {
                id: id,
                repository: 'repository',
                workspace: 'collaboration',
                //concat the file type if attachement title haven't extension on preview mode
                title: decodeURI(attachment.title).lastIndexOf('.') >= 0 ? decodeURI(attachment.title) : decodeURI(attachment.title).concat(this.fileType),
                downloadUrl: attachment.downloadUrl,
                openUrl: attachment.openUrl,
                breadCrumb: attachment.previewBreadcrumb,
                fileInfo: this.fileInfo(),
                size: attachment.size,
                isCloudDrive: attachment.cloudDrive
              },
              author: attachment.updater,
              version: {
                number: attachment.version
              },
              showComments: false,
              showOpenInFolderButton: false,
            });
          })
          .catch(e => console.error(e))
          .finally(() => {
            window.history.pushState('', '', `${window.location.pathname}?documentPreviewId=${this.file.id}`);
            this.loading = false;
          });
      }
    },
    displayActionMenu() {
      if (this.isMobile){
        this.$root.$emit('open-file-action-menu', this.file);
      } else {
        this.menuDisplayed = true;
        $(`#document-action-menu-cel-${this.file.id}`).parent().parent().parent().parent().css('background', '#eee');
      }
    },
    escapeRegExp(string) {
      return string.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    },
    highlightSearchResult(str, queries) {
      queries = queries.split(' ');
      const accentRegex = new RegExp(Object.keys(this.accentMap).join('|'), 'g');
      const queryRegex = new RegExp(queries.map(q => {
        return this.escapeRegExp(q).toLowerCase().replace(accentRegex, m => {
          return this.accentMap[m] || m;
        });
      }).join('|'), 'gi');
      return str.toString().replace(queryRegex, function(matchedTxt){
        return ( `<b>${matchedTxt}</b>`);
      });
    },
  }
};
</script>
