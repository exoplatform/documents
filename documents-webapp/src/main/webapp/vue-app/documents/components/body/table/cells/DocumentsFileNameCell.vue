<template>
  <div
    class="d-flex flex-nowrap">
    <a
      class="attachment d-flex flex-nowrap text-color not-clickable openPreviewDoc width-full">
      <div>
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
      <div class="width-full">
        <div
          v-if="!editNameMode"
          @click="openPreview()"
          class="document-title clickable hover-underline d-inline-flex"
          :title="title">
          <div
            v-sanitized-html="title"
            class="document-name text-truncate ms-4">
          </div>
          <div
            v-sanitized-html="fileType"
            class="document-type ms-0">
          </div>
          <v-icon
            v-if="file.sourceID"
            size="10"
            class="pe-1 iconStyle pb-1 ps-1">
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
          <documents-visibility-cell :file="file" />
        </div>
      </div>
    </a>
    <v-spacer />
    <documents-info-details-cell
      v-if="!isMobile"
      :file="file"
      :class="editNameMode ? '' : 'button-info-details'" />
    <div
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
              :file="file" />
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
export default {
  props: {
    file: {
      type: Object,
      default: null,
    },
    extension: {
      type: Object,
      default: null,
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
  }),
  computed: {
    title() {
      return decodeURI(this.fileName);
    },
    lastUpdated() {
      return this.file && (this.file.modifiedDate || this.file.createdDate) || '';
    },
    isMobile() {
      return this.$vuetify.breakpoint.name === 'xs' || this.$vuetify.breakpoint.name === 'sm';
    },
    icon() {
      if (this.file && this.file.folder){
        return {
          class: 'fas fa-folder',
          color: '#476A9C',
        };
      }
      const type = this.file && this.file.mimeType || '';
      if (type.includes('pdf')) {
        return {
          class: 'fas fa-file-pdf',
          color: '#FF0000',
        };
      } else if (type.includes('presentation') || type.includes('powerpoint')) {
        return {
          class: 'fas fa-file-powerpoint',
          color: '#CB4B32',
        };
      } else if (type.includes('sheet') || type.includes('excel') || type.includes('csv')) {
        return {
          class: 'fas fa-file-excel',
          color: '#217345',
        };
      } else if (type.includes('word') || type.includes('opendocument') || type.includes('rtf') ) {
        return {
          class: 'fas fa-file-word',
          color: '#2A5699',
        };
      } else if (type.includes('plain')) {
        return {
          class: 'fas fa-file-alt',
          color: '#385989',
        };
      } else if (type.includes('image')) {
        return {
          class: 'fas fa-file-image',
          color: '#999999',
        };
      } else if (type.includes('video') || type.includes('octet-stream') || type.includes('ogg')) {
        return {
          class: 'fas fa-file-video',
          color: '#79577A',
        };
      } else if (type.includes('zip') || type.includes('war') || type.includes('rar')) {
        return {
          class: 'fas fa-file-archive',
          color: '#717272',
        };
      } else if (type.includes('illustrator') || type.includes('eps')) {
        return {
          class: 'fas fa-file-contract',
          color: '#E79E24',
        };
      } else if (type.includes('html') || type.includes('xml') || type.includes('css')) {
        return {
          class: 'fas fa-file-code',
          color: '#6cf500',
        };
      } else {
        return {
          class: 'fas fa-file',
          color: '#476A9C',
        };
      }
    },
    fileName() {
      return this.file.name.lastIndexOf('.') >= 0 && !this.file.folder ? this.file.name.substring(0,this.file.name.lastIndexOf('.')):this.file.name;
    },
    editNameMode() {
      return this.file.id===this.fileToEditId;
    },
    fileType() {
      return this.file.name.lastIndexOf('.') >= 0 && !this.file.folder ? this.file.name.substring(this.file.name.lastIndexOf('.')):'';
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
  },
  beforeDestroy() {
    this.$root.$off('update-file-name', this.editFileName);
    this.$root.$off('cancel-edit-mode', this.cancelEditMode);
  },
  methods: {
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
    openPreview() {
      this.loading = true;
      if (this.file && this.file.folder){
        this.$root.$emit('document-open-folder', this.file);
      } else {
        let id = this.file.id;
        if (this.file.sourceID){
          id = this.file.sourceID;
        }
        this.$attachmentService.getAttachmentById(id)
          .then(attachment => {
            documentPreview.init({
              doc: {
                id: id,
                repository: 'repository',
                workspace: 'collaboration',
                title: decodeURI(attachment.title),
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
            window.history.pushState('', '', `${eXo.env.server.portalBaseURL}?documentPreviewId=${this.file.id}`);
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
    }
  }
};
</script>
