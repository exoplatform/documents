<template>
  <div v-if="renderedFiles.length" class="uploadedFiles">
    <div class="attachments-list d-flex align-center">
      <v-subheader class="text-sub-title pl-0 d-flex">
        {{ $t('attachments.drawer.title') }} ({{ renderedFiles.length }}/{{ maxFilesCount }})
      </v-subheader>
      <v-divider class="mx-2" />
      <v-btn
        v-if="renderedFiles.length"
        text
        x-small
        color="error"
        class="removeAllAttachments flex-shrink-0"
        :aria-label="$t('attachments.drawer.removeAll')"
        @click="confirmRemoveAll">
        <v-icon size="14" class="me-1">fa-trash-can</v-icon>
        {{ $t('attachments.drawer.removeAll') }}
      </v-btn>
    </div>
    <!-- we hide the possibility to select a folder for all files
    There a lot of problem with this feature and need a rework to make it work.
    To be able to release 6.4.0 we hide it -->
    <div v-if="false && attachments.length > 0" class="destinationFolder d-flex justify-space-between mb-4 ms-5">
      <div v-if="!displayMessageDestinationFolder && schemaFolder.length" class="destinationFolderBreadcrumb d-flex">
        <div
          :title="schemaFolder[0]"
          class="drive text-sub-title flex-shrink-0"
          rel="tooltip"
          data-placement="top">
          {{ schemaFolder[0] }}
        </div>
        <div class="folderLocation">
          <div v-if="schemaFolder.length > 1" class="folder">
            <div><span class="uiIconArrowRight colorIcon"></span></div>
            <div
              :title="schemaFolder[1]"
              :class="schemaFolder.length === 2 ? 'active' : 'text-sub-title' "
              class="folderName"
              rel="tooltip"
              data-placement="top">
              {{ schemaFolder[1] }}
            </div>
          </div>
          <div v-if="schemaFolder.length === 3" class="folder">
            <div><span class="uiIconArrowRight colorIcon"></span></div>
            <div
              :title="schemaFolder[2]"
              :class="schemaFolder[2] !== 'Activity Stream Documents' ? 'path' : 'active' "
              class="folderName"
              rel="tooltip"
              data-placement="top">
              {{ schemaFolder[2] }}
            </div>
          </div>
          <div v-if="schemaFolder.length > 3" class="folder">
            <div><span class="uiIconArrowRight colorIcon"></span></div>
            <div
              :title="schemaFolder[2]"
              class="folderName"
              rel="tooltip"
              data-placement="top">
              ...
            </div>
          </div>
          <div
            v-for="folder in schemaFolder.slice(schemaFolder.length-1,schemaFolder.length)"
            v-show="schemaFolder.length > 3"
            :key="folder"
            class="folder">
            <div><span class="uiIconArrowRight colorIcon"></span></div>
            <div
              :title="folder"
              :class="schemaFolder[schemaFolder.length - 1] === folder && schemaFolder[schemaFolder.length - 1].length < 11 ?'active' : 'path'"
              class="folderName"
              rel="tooltip"
              data-placement="top">
              {{ folder }}
            </div>
          </div>
        </div>
      </div>
      <div v-if="displayMessageDestinationFolder" class="messageDestination">
        <span
          :title="$t('attachments.drawer.destination.attachment.message')"
          class="text-sub-title"
          rel="tooltip"
          data-placement="top">
          {{ $t('attachments.drawer.destination.attachment.message') }}</span>
      </div>
      <button
        :disabled="displayMessageDestinationFolder"
        class="buttonSelect d-flex pl-1 flex-column-reverse"
        @click="openSelectDestinationFolderDrawer()">
        <i
          :title="!displayMessageDestinationFolder ? $t('attachments.drawer.destination.attachment') : $t('attachments.drawer.destination.attachment.access') "
          :class="displayMessageDestinationFolder ? 'disabled not-allowed' : ''"
          class="uiIconFolder "
          rel="tooltip"
          data-placement="top"></i>
      </button>
    </div>
    <div v-if="renderedFiles.length" class="uploadedFilesItems ml-5">
      <transition-group
        name="list-complete"
        tag="div"
        class="d-flex flex-column">
        <span
          v-for="attachment in renderedFiles"
          :key="attachment.id || attachment.uploadId"
          class="list-complete-item">
          <attachment-item
            :attachment="attachment"
            :can-edit="attachment.acl && attachment.acl.canEdit"
            :allow-to-preview="false"
            :current-space="currentSpace"
            :current-drive="currentDrive"
            :default-folder="defaultFolder"
            :entity-id="entityId"
            :allow-to-detach="allowToDetach"
            allow-to-edit
            @keepBoth="$emit('keep-both', attachment)"
            @createVersion="$emit('create-version', attachment)" />
        </span>
      </transition-group>
    </div>
    <exo-confirm-dialog
      ref="removeAllConfirmDialog"
      :title="$t('attachments.drawer.removeAll.confirm.title')"
      :message="$t('attachments.drawer.removeAll.confirm.message')"
      :ok-label="$t('attachments.drawer.removeAll')"
      :cancel-label="$t('attachments.cancel')"
      @ok="removeAll" />
  </div>
</template>

<script>
export default {
  props: {
    attachments: {
      type: Array,
      default: () => []
    },
    schemaFolder: {
      type: Object,
      default: () => null
    },
    maxFilesCount: {
      type: Number,
      default: null
    },
    currentSpace: {
      type: {},
      default: () => null
    },
    currentDrive: {
      type: {},
      default: () => null
    },
    newUploadedFiles: {
      type: {},
      default: () => null
    },
    entityId: {
      type: String,
      default: ''
    },
    entityType: {
      type: String,
      default: ''
    },
    defaultFolder: {
      type: String,
      default: ''
    },
    displayUploadedFiles: {
      type: Boolean,
      default: false
    }
  },
  created() {
    if (this.displayUploadedFiles) {
      this.updateUploadedFilesList();
    }
    this.$root.$on('refresh-uploaded-files-list', () => {
      this.$forceUpdate();
      this.endLoadingAttachmentDrawer();
    });
  },
  computed: {
    // Single source of truth for the rendered list AND the header count: the
    // list de-dupes newUploadedFiles by (id || uploadId) so the count and the
    // rows can no longer diverge, and rows get a stable key on id||uploadId.
    renderedFiles() {
      const seen = new Set();
      const files = [];
      (this.newUploadedFiles || []).forEach(file => {
        const key = file && (file.id || file.uploadId);
        if (key == null) {
          files.push(file);
        } else if (!seen.has(key)) {
          seen.add(key);
          files.push(file);
        }
      });
      return files;
    },
    displayMessageDestinationFolder() {
      return !this.attachments.length || this.attachments.some(val => val.uploadId != null && val.uploadId !== '');
    },
    allowToDetach() {
      return !!this.entityId && !!this.entityType;
    },
  },
  watch: {
    displayUploadedFiles(newVal) {
      if (newVal) {
        this.updateUploadedFilesList();
      }
    },
    attachments: {
      handler() {
        if (this.displayUploadedFiles) {
          this.updateUploadedFilesList();
        }
      },
      deep: true
    }
  },
  methods: {
    confirmRemoveAll() {
      this.$refs.removeAllConfirmDialog.open();
    },
    removeAll() {
      // Reuse the existing per-item removal contract (remove-attachment-item) so
      // every consumer (entity link removal / local splice) behaves as usual.
      this.renderedFiles.slice().forEach(file => this.$root.$emit('remove-attachment-item', file));
    },
    openSelectDestinationFolderDrawer() {
      this.$root.$emit('open-drive-explorer-drawer', this.currentDrive);
    },
    endLoadingAttachmentDrawer() {
      const isLoadingDrawer =  this.attachments && this.attachments.some(val => val.waitAction || val.uploadProgress < 100);
      if (!isLoadingDrawer) {
        this.$root.$emit('end-loading-attachment-drawer');
      }
    },
    updateUploadedFilesList() {
      this.attachments.forEach((attachment) => {
        if (!this.newUploadedFiles.find((item) => item.id === attachment.id)) {
          this.newUploadedFiles.push(attachment);  // Push only if the attachment is not already there
        }
      });
    },
  }
};
</script>
