<template>
  <div
    class="d-flex flex-nowrap">
    <a
      class="attachment d-flex flex-nowrap text-color openPreviewDoc"
      @click="openPreview">
      <v-progress-circular
        v-if="loading"
        indeterminate
        size="16" />
      <v-icon
        v-else
        size="22"
        :color="icon.color">{{ icon.class }}</v-icon>
      <div>
        <div class="document-title d-inline-flex" :title="file.name">
          <div
            v-sanitized-html="fileName"
            class="document-name text-truncate hover-underline ms-4">
          </div>
          <div
            v-sanitized-html="fileType"
            class="document-type hover-underline ms-0">
          </div>
        </div>
        <documents-last-updated-cell
          v-if="isMobile"
          :file="file"
          :extension="extension" />
      </div>
    </a>
    <v-spacer />
    <div
      :id="`document-action-menu-cel-${file.id}`"
      class="position-relative">
      <v-icon
        v-show="isMobile"
        size="18"
        class="clickable text-sub-title"
        @click="menuDispalayed = true">
        mdi-dots-vertical
      </v-icon>
      <v-menu
        v-model="menuDispalayed"
        :attach="`#document-action-menu-cel-${file.id}`"
        transition="slide-x-reverse-transition"
        :content-class="isMobile ? 'documentActionMenuMobile' : 'documentActionMenu'"
        offset-y
        offset-x
        left>
        <documents-actions-menu
          :file="file" />
      </v-menu>
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
    menuDispalayed: false,
    waitTimeUntilCloseMenu: 200,
  }),
  computed: {
    isMobile() {
      return this.$vuetify.breakpoint.name === 'xs';
    },
    icon() {
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
      return this.file.name.split('.')[0];
    },
    fileType() {
      return `.${this.file.name.split('.')[1]}`;
    },
  },
  created(){
    $(document).on('mousedown', () => {
      if (this.menuDispalayed) {
        window.setTimeout(() => {
          this.menuDispalayed = false;
        }, this.waitTimeUntilCloseMenu);
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
    },
  },
};
</script>