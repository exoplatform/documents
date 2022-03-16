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

        <v-icon
          v-else
          size="22"
          :color="icon.color">{{ icon.class }}</v-icon>
      </div>
      <div class="width-full">
        <div
          v-if="!editNameMode"
          @click="openPreview"
          class="document-title clickable hover-underline d-inline-flex"
          :title="file.name">
          <div
            v-sanitized-html="fileName"
            class="document-name text-truncate ms-4">
          </div>
          <v-icon
            v-if="file.symlinkID"
            size="10"
            class="pe-1 iconStyle pb-1 ps-1">
            mdi-link-variant
          </v-icon>
          <div
            v-sanitized-html="fileType"
            class="document-type ms-0">
          </div>
        </div>

        <documents-file-edit-name-cell
          v-if="editNameMode"
          :file="file"
          :file-name="fileName"
          :file-type="fileType"
          :is-mobile="isMobile"
          :edit-name-mode="editNameMode" />

        <documents-last-updated-cell
          v-if="isMobile && !editNameMode"
          :file="file"
          :extension="extension" />
      </div>
    </a>
    <v-spacer />
    <documents-info-details-cell
      v-if="!isMobile && !drawerDetails"
      :file="file"
      :class="editNameMode ? '' : 'button-info-details'"
      @open-info-drawer="openInfoDetailsDrawer" />
    <div
      :id="`document-action-menu-cel-${file.id}`"
      v-if="displayAction">
      <v-tooltip bottom>
        <template v-slot:activator="{ on, attrs }">
          <v-btn
            icon
            small
            v-bind="attrs"
            v-on="on">
            <v-icon
              v-show="isMobile || menuDisplayed"
              :size="isMobile ? 14 : 18"
              class="clickable text-sub-title"
              :class="editNameMode || drawerDetails ? '' : 'button-document-action'"
              @click="displayActionMenu">
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
    <documents-info-drawer
      ref="documentInfoDrawer"
      :file="file"
      :file-name="fileName"
      :file-type="fileType"
      :is-mobile="isMobile"
      :icon="icon" />
    <documents-actions-menu-mobile ref="documentActionsBottomMenu" :file="file" />
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
    drawerDetails: false,
    fileToEditId: -1
  }),
  computed: {
    isMobile() {
      return this.$vuetify.breakpoint.name === 'xs';
    },
    displayAction(){
      return !this.file.folder || (this.file.folder && eXo.env.portal.folderActionEnabled);
    },
    icon() {
      if (this.file && this.file.folder){
        return {
          class: 'fas fa-folder',
          color: '#578DC9',
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
          color: '#578DC9',
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
  created(){
    $(document).on('mousedown', () => {
      if (this.menuDisplayed) {
        window.setTimeout(() => {
          $(`#document-action-menu-cel-${this.file.id}`).parent().parent().parent().parent().css('background', '#fff');
          this.menuDisplayed = false;
        }, this.waitTimeUntilCloseMenu);
      }
    });
    this.$root.$on('update-file-name', file => {
      if (this.file.id=== file.id){
        this.fileToEditId=file.id;
      }
    });
    this.$root.$on('close-file-action-menu', () => {
      this.$refs.documentActionsBottomMenu.close();
    });
    this.$root.$on('cancel-edit-mode', file => {
      if (this.file.id=== file.id) {
        this.fileToEditId=-1;
      }
    });
    this.$root.$on('open-info-drawer', fileId => {
      if (this.file.id=== fileId) {
        this.openInfoDetailsDrawer();
        this.$refs.documentActionsBottomMenu.close();
      }
    });
    this.$root.$on('close-info-drawer', fileId => {
      if (this.file.id=== fileId) {
        this.drawerDetails=false;
      }
    });
  },
  methods: {
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
        this.$attachmentService.getAttachmentById(this.file.id)
          .then(attachment => {
            documentPreview.init({
              doc: {
                id: this.file.id,
                repository: 'repository',
                workspace: 'collaboration',
                title: attachment.title,
                downloadUrl: attachment.downloadUrl,
                openUrl: attachment.openUrl,
                breadCrumb: attachment.previewBreadcrumb,
                fileInfo: this.fileInfo(),
                size: attachment.size,
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
        this.$refs.documentActionsBottomMenu.open();
      } else {
        this.menuDisplayed = true;
        $(`#document-action-menu-cel-${this.file.id}`).parent().parent().parent().parent().css('background', '#eee');
      }
    },
    openInfoDetailsDrawer(){
      this.drawerDetails=true;
      this.$refs.documentInfoDrawer.open();
    }
  },
};
</script>