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
          color="primary"
          indeterminate
          size="16" />
        <i
          v-else-if="file.cloudDriveFolder "
          class="fas fa-folder driveFolderIcon">
          <i 
            class="fa-hdd driveFolderContentIcon"></i>
        </i>
        <v-list-item-avatar 
          v-else-if="file.drive"
          size="24"
          class="mx-0"
          tile>
          <img
            :src="file.avatarUrl"
            alt=""
            class="rounded"
            width="24"
            height="24">
        </v-list-item-avatar>
        <v-icon
          v-else
          :size="isMobile && 32 || 22"
          :color="icon.color">{{ icon.class }}</v-icon>
      </div>
      <div class="mt-auto mb-auto width-document-title">
        <div
          v-if="!editNameMode"
          class="document-title clickable hover-underline d-inline-flex"
          :class="file.hidden ? 'text-light-color' : ''"
          :title="file.hidden ? `${file.name} (${$t('documents.label.hidden')})`: `${file.name}`"
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
          <v-icon
            v-if="file.hidden"
            size="13"
            class="pe-1 iconStyle ms-1">
            fas fa-eye-slash
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
            class="mt-n1"
            :file="file"
            :is-mobile="isMobile"
            :selected-view="selectedView" />
        </div>
      </div>
    </a>
    <v-spacer />
    <documents-info-details-cell
      v-if="!isMobile && !file.drive"
      :file="file"
      :is-mobile="isMobile"
      :class="editNameMode ? '' : 'button-info-details'" />
    <div
      v-if="!file.drive"
      class="ma-auto"
      :id="`document-action-menu-cel-${file.id}`">
      <v-tooltip
        :disabled="menuDisplayed"
        :open-delay="500"
        bottom>
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
              @click="displayActionMenu($event)">
              mdi-dots-vertical
            </v-icon>
          </v-btn>
          <v-menu
            v-model="menuDisplayed"
            transition="slide-x-reverse-transition"
            :content-class="isMobile ? 'documentActionMenuMobile' : 'documentActionMenu'"
            :position-x="menuX"
            :position-y="menuY"
            :nudge-right="30"
            :nudge-top="25"
            offset-y
            offset-x
            close-on-click
            absolute>
            <documents-actions-menu
              :file="file"
              :current-view="selectedView"
              :is-search-result="isSearchResult"
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
    isSearchResult: {
      type: Boolean,
      default: false
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
    menuX: 0,
    menuY: 0,
  }),
  computed: {
    isFileEditable() {
      const fileType = this.file && this.file.mimeType || '';
      return this.$supportedDocuments && this.$supportedDocuments.filter(doc => doc.edit && doc.mimeType === fileType && !this.file.cloudDriveFile).length > 0;
    },
    isFileOnlyReadable() {
      const fileType = this.file && this.file.mimeType || '';
      return this.$supportedDocuments && this.$supportedDocuments.filter(doc => !doc.edit && doc.mimeType === fileType && !this.file.cloudDriveFile).length > 0;
    },
    documentMultiSelectionActive() {
      return this.$vuetify.breakpoint.width >= 600 && !this.file.drive;
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
    canEdit() {
      return this.file?.acl?.canEdit;
    },
  },
  created() {
    $(document).on('mousedown', (event) => {
      if (!event.target.closest('.group-menu-action') && this.menuDisplayed) {
        window.setTimeout(() => {
          $(`#document-action-menu-cel-${this.file.id}`).parent().parent().parent().parent().removeAttr('style');
          this.menuDisplayed = false;
        }, this.waitTimeUntilCloseMenu);
      }
    });
    this.$root.$on('update-file-name', this.editFileName);
    this.$root.$on('cancel-edit-mode', this.cancelEditMode);
    this.$root.$on('document-set-icon-loading', this.setIconLoading);
    this.getFileIcon();
  },
  beforeDestroy() {
    this.$root.$off('update-file-name', this.editFileName);
    this.$root.$off('cancel-edit-mode', this.cancelEditMode);
    this.$root.$off('document-set-icon-loading', this.setIconLoading);
  },
  methods: {
    setIconLoading(fileId,status) {
      this.loading = this.file.id === fileId && status;
    },
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
      const extensions = this.$documentsIconsExtension;
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
      const fileId = file.sourceID? file.sourceID: file.id;
      this.$documentsUtils.openLink(`${eXo.env.portal.context}/${eXo.env.portal.metaPortalName}/oeditor?docId=${fileId}&backTo=${window.location.pathname}`, '_blank');
    },
    openInReadOnlyMode(file) {
      const fileId = file.sourceID? file.sourceID: file.id;
      this.$documentsUtils.openLink(`${eXo.env.portal.context}/${eXo.env.portal.metaPortalName}/oeditor?docId=${fileId}&mode=view&backTo=${window.location.pathname}`, '_blank');
    },
    openPreview() {
      this.loading = true;
      if (this.file?.folder) {
        this.$root.$emit('document-open-folder', this.file);
      }
      if (this.file?.drive) {
        this.$root.ownerId = this.file.identityId;
        this.$root.spaceId = this.file.spaceId;
        this.$root.$emit('document-open-folder', this.file);
      } else if (this.isFileEditable)  {
        if (this.canEdit) {
          this.openInEditMode(this.file);
        } else {
          this.openInReadOnlyMode(this.file);
        }        
      } else if (this.isFileOnlyReadable) {
        this.openInReadOnlyMode(this.file);
      } else {
        this.$root.$emit('documents-preview', this.file);
      }
      this.loading = false;
      this.$root.$emit('mark-document-as-viewed', this.file);
    },
    displayActionMenu(event) {
      if (this.isMobile) {
        this.$root.$emit('open-file-action-menu', this.file);
      } else {
        const rect = event.currentTarget.getBoundingClientRect();
        this.menuX = rect.left;
        this.menuY = rect.bottom;

        this.menuDisplayed = true;

        $(`#document-action-menu-cel-${this.file.id}`)
          .parent().parent().parent().parent()
          .css('background', '#eee');
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
