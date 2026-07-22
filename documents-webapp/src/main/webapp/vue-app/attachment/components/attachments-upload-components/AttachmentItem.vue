<template>
  <v-main>
    <div :class="[allowToPreview && 'clickable', attachment.uploadFailed && 'attachment--failed']" class="attachment d-flex">
      <v-list-item-avatar
        :class="smallAttachmentIcon ? 'me-0' :'me-3'"
        class="border-radius"
        @click="openFile()">
        <div v-if="attachment.uploadFailed" class="fileType attachmentFailedIcon">
          <v-icon size="36" color="error">fa-triangle-exclamation</v-icon>
        </div>
        <div v-else-if="attachmentInProgress" class="fileProgress">
          <v-progress-circular
            :rotate="-90"
            :size="40"
            :width="4"
            :value="attachment.uploadProgress"
            role="progressbar"
            :aria-valuenow="attachment.uploadProgress"
            aria-valuemin="0"
            aria-valuemax="100"
            color="primary">
            {{ attachment.uploadProgress }}
          </v-progress-circular>
        </div>
        <v-img
          v-else-if="showThumbnail"
          :src="thumbnailUrl"
          :alt="$t('attachments.item.thumbnail.alt').replace('{0}', attachmentTitle)"
          class="attachmentThumbnail border-radius"
          height="40"
          width="40"
          @error="thumbnailError = true" />
        <div
          v-else
          :class="smallAttachmentIcon && 'smallAttachmentIcon'"
          class="fileType">
          <v-icon
            size="41"
            :color="icon.color">
            {{ icon.class }}
          </v-icon>
        </div>
      </v-list-item-avatar>
      <v-list-item-content @click="openFile()">
        <v-list-item-title class="uploadedFileTitle" :title="attachmentTitle">
          {{ attachmentTitle || notAccessibleAttachmentTitle }}
        </v-list-item-title>
        <v-list-item-subtitle class="d-flex align-center flex-wrap v-messages uploadedFileMeta">
          <span v-if="humanizedSize" class="attachmentMetaSize">{{ humanizedSize }}</span>
          <span v-if="humanizedSize && fileExtension" class="attachmentMetaSep mx-1">·</span>
          <span v-if="fileExtension" class="attachmentMetaType">{{ fileExtension }}</span>
          <v-chip
            v-if="attachmentSourceLabel"
            x-small
            label
            outlined
            class="attachmentSourceBadge ms-2 px-2">
            {{ attachmentSourceLabel }}
          </v-chip>
        </v-list-item-subtitle>
        <v-progress-linear
          v-if="attachmentInProgress"
          :value="attachment.uploadProgress"
          rounded
          height="4"
          class="mt-1 attachmentProgressLinear"
          color="primary" />
        <v-list-item-subtitle v-if="attachment.uploadFailed" class="error--text attachmentFailedMessage">
          {{ $t('attachments.item.upload.failed') }}
        </v-list-item-subtitle>
        <v-list-item-subtitle v-if="canMoveAttachment" class="d-flex v-messages uploadedFileSubTitle">
          <v-chip
            v-if="attachment.pathDestinationFolderForFile"
            close
            small
            class="attachment-location px-2"
            @click:close="$root.$emit('remove-destination-for-file', attachment.id)"
            @click="openSelectDestinationFolderForFile(attachment)">
            {{ attachment.pathDestinationFolderForFile }}
          </v-chip>
          <v-tooltip v-if="!attachment.pathDestinationFolderForFile" top>
            <template #activator="{ on, attrs }">
              <a
                v-bind="attrs"
                class="attachmentDestinationPath primary--text"
                v-on="on"
                @click="openSelectDestinationFolderForFile(attachment)">{{ $t('attachments.ChangeLocation') }}</a>
            </template>
            <span>{{ $t('attachments.ChangeLocation') }}</span>
          </v-tooltip>
        </v-list-item-subtitle>
      </v-list-item-content>
      <v-list-item-action class="d-flex flex-row align-center">
        <v-tooltip v-if="attachment.isSelectedFromDrives && fromAnotherSpaceAttachment || fromAnotherDriveAttachment" top>
          <template #activator="{ on, attrs }">
            <v-icon
              v-bind="attrs"
              size="14"
              color="primary"
              depressed
              v-on="on">
              fa-info-circle
            </v-icon>
          </template>
          <span>{{ attachmentPrivacyLabel }}</span>
        </v-tooltip>
        <v-tooltip v-if="!canAccess" top>
          <template #activator="{ on, attrs }">
            <v-icon
              v-bind="attrs"
              size="14"
              color="primary"
              depressed
              v-on="on">
              fa-info-circle
            </v-icon>
          </template>
          <span>{{ notAccessibleAttachmentTooltip }}</span>
        </v-tooltip>
        <v-btn
          v-if="attachment.uploadFailed"
          class="attachmentRetryBtn me-1"
          x-small
          outlined
          color="primary"
          :aria-label="$t('attachments.item.retry')"
          @click="retryUpload(attachment)">
          <v-icon size="12" class="me-1">fa-rotate-right</v-icon>
          {{ $t('attachments.item.retry') }}
        </v-btn>
        <v-btn
          v-if="attachmentInProgress"
          class="d-flex align-end me-1"
          icon
          x-small
          height="18"
          width="18"
          :aria-label="$t('attachments.item.remove')"
          @click="detachFile(attachment)">
          <i class="uiIconCloseCircled d-flex mx-auto error--text"></i>
        </v-btn>
        <v-tooltip v-if="(canRemoveFromList && canAccess) || attachment.uploadFailed" top>
          <template #activator="{ on, attrs }">
            <div
              :class="!canDetachAttachment && 'not-allowed'"
              class="remove-button"
              v-bind="attrs"
              v-on="on">
              <v-btn
                :disabled="!canDetachAttachment"
                :aria-label="$t('attachments.item.remove')"
                class="d-flex"
                icon
                x-small
                height="24"
                width="24"
                @click="detachFile(attachment)">
                <v-icon
                  :class="!canDetachAttachment && 'grey--text' || 'error--text'"
                  small
                  class="fas fa-unlink" />
              </v-btn>
            </div>
          </template>
          <span>{{ !canDetachAttachment && $t('attachments.remove.notAuthorize') || $t('attachment.detach') }}</span>
        </v-tooltip>
      </v-list-item-action>
    </div>
    <div
      v-if="attachment.actions && attachment.actions.length"
      class="attachmentConflictActions d-flex ms-4 mt-1">
      <v-btn
        v-for="action in attachment.actions"
        :key="action"
        x-small
        outlined
        color="primary"
        class="me-2"
        @click="$emit(`${action}`, attachment)">
        {{ $t(`attachments.upload.action.${action}`) }}
      </v-btn>
    </div>
  </v-main>
</template>
<script>
export default {
  props: {
    attachments: {
      type: Array,
      default: () => []
    },
    attachment: {
      type: Object,
      default: () => null
    },
    allowToDetach: {
      type: Boolean,
      default: true
    },
    allowToEdit: {
      type: Boolean,
      default: true
    },
    allowToPreview: {
      type: Boolean,
      default: false
    },
    openInEditor: {
      type: Boolean,
      default: false
    },
    isFileEditable: {
      type: Boolean,
      default: false
    },
    isFileFillable: {
      type: Boolean,
      default: false
    },
    isFileReadable: {
      type: Boolean,
      default: false
    },
    canEdit: {
      type: Boolean,
      default: false
    },
    entityId: {
      type: String,
      default: ''
    },
    canAccess: {
      type: Boolean,
      default: true
    },
    smallAttachmentIcon: {
      type: Boolean,
      default: false
    },
    currentSpace: {
      type: {},
      default: () => null
    },
    currentDrive: {
      type: {},
      default: () => null
    },
    defaultFolder: {
      type: String,
      default: ''
    },
  },
  data() {
    return {
      BYTES_IN_KB: 1024,
      BYTES_IN_MB: 1048576,
      BYTES_IN_GB: 1073741824,
      MB_IN_GB: 10,
      measure: 'bytes',
      thumbnailError: false,
    };
  },
  computed: {
    isImageAttachment() {
      const type = this.attachment && this.attachment.mimetype || '';
      return type.startsWith('image/');
    },
    thumbnailUrl() {
      if (!this.isImageAttachment) {
        return null;
      }
      if (this.attachment.downloadUrl) {
        return this.attachment.downloadUrl;
      }
      if (this.attachment.id && this.$documentsUtils) {
        return this.$documentsUtils.getDownloadUrl(this.attachment.id, this.attachment.lastModified);
      }
      return null;
    },
    showThumbnail() {
      return this.isImageAttachment && !this.thumbnailError && !!this.thumbnailUrl;
    },
    humanizedSize() {
      const size = Number(this.attachment && this.attachment.size);
      if (!size || size <= 0) {
        return '';
      }
      if (size < this.BYTES_IN_KB) {
        return `${size} ${this.$t('attachments.composer.file.size.bytes')}`;
      }
      if (size < this.BYTES_IN_MB) {
        return `${(size / this.BYTES_IN_KB).toFixed(1)} ${this.$t('attachments.composer.file.size.kilo')}`;
      }
      if (size < this.BYTES_IN_GB) {
        return `${(size / this.BYTES_IN_MB).toFixed(1)} ${this.$t('attachments.composer.file.size.mega')}`;
      }
      return `${(size / this.BYTES_IN_GB).toFixed(1)} ${this.$t('attachments.composer.file.size.giga')}`;
    },
    fileExtension() {
      const title = this.attachmentTitle || '';
      if (!title.includes('.')) {
        return '';
      }
      return title.split('.').pop().toUpperCase();
    },
    attachmentSourceLabel() {
      const source = this.attachment && this.attachment.source;
      if (source === 'documents' || this.attachment && this.attachment.isSelectedFromDrives) {
        return this.$t('attachments.source.documents');
      }
      if (source === 'created') {
        return this.$t('attachments.source.created');
      }
      if (source === 'device') {
        return this.$t('attachments.source.device');
      }
      return '';
    },
    fromAnotherSpaceAttachment() {
      return this.attachmentSpaceId && this.attachmentSpaceId !== this.currentSpaceId && this.attachmentSpaceId || false;
    },
    fromAnotherDriveAttachment() {
      return this.attachmentCurrentDriveName && this.currentDriveName !== this.attachmentCurrentDriveName && !this.attachmentSpaceId || false;
    },
    selectedFromOtherDriveLabel() {
      return this.$t(`attachments.alert.sharing.${this.otherDriveType}`);
    },
    otherDriveType() {
      return this.fromAnotherSpaceAttachment ? 'space' : this.fromAnotherDriveAttachment ? 'otherDrive' : '';
    },
    attachmentSpaceDisplayName() {
      return this.attachment && this.attachment.space && this.attachment.space.title;
    },
    attachmentCurrentDriveName() {
      return this.attachment && this.attachment.fileDrive && this.attachment.fileDrive.title;
    },
    currentSpaceId() {
      return this.currentSpace && this.currentSpace.groupId && this.currentSpace.groupId.split('/spaces/')[1];
    },
    currentDriveName() {
      return this.currentDrive && this.currentDrive.title;
    },
    attachmentSpaceId() {
      return this.attachment && this.attachment.space && this.attachment.space.name && this.attachment.space.name.split('.spaces.')[1];
    },
    attachedFromOtherDrivesLabel() {
      return `${this.$t('attachments.alert.sharing.attachedFrom')} ${this.selectedFromOtherDriveLabel} ${this.fromAnotherSpaceAttachment && this.attachmentSpaceDisplayName || this.fromAnotherDriveAttachment && this.attachmentCurrentDriveName || ''}.`;
    },
    attachmentsWillBeDisplayedForLabel() {
      return this.$t('attachments.alert.sharing.availableFor');
    },
    attachmentPrivacyLabel() {
      return `${this.attachedFromOtherDrivesLabel} ${this.attachmentsWillBeDisplayedForLabel}`;
    },
    canDetachAttachment() {
      return this.attachmentHasPermission && this.attachmentHasPermission.canDetach || !this.attachment.id || this.attachment.isSelectedFromDrives || !this.entityId;
    },
    canRemoveFromList() {
      // Show the remove button both when detaching from a bound entity
      // (allowToDetach) AND while composing a not-yet-persisted list (no entity),
      // where items are always removable locally.
      return this.allowToDetach || !this.entityId;
    },
    canMoveAttachment() {
      return this.canEdit && this.allowToEdit && !this.attachment.isSelectedFromDrives;
    },
    attachmentHasPermission() {
      return this.attachment && this.attachment.acl;
    },
    notAccessibleAttachmentTitle() {
      return !this.canAccess && this.$t('attachment.notAccessible.title') || '';
    },
    notAccessibleAttachmentTooltip() {
      return this.$t('attachment.notAccessible.tooltip');
    },
    attachmentInProgress() {
      return !this.attachment.uploadFailed && this.attachment.uploadProgress < 100;
    },
    attachmentTitle() {
      return this.attachment && this.attachment.title && unescape(this.attachment.title);
    },
    icon() {
      return this.getFileIcon(this.attachment);
    },
    attachmentDefaultFolderName() {
      return this.defaultFolder || null ;
    }
  },
  watch: {
    attachmentInProgress(newVal) {
      if (!newVal) {
        this.$root.$emit('end-loading-attachment-drawer');
      }
    },
  },
  methods: {
    markDocumentAsViewed() {
      document.dispatchEvent(new CustomEvent('mark-attachment-as-viewed', {detail: {file: this.attachment}}));
    },
    detachFile() {
      if (this.canDetachAttachment) {
        this.$root.$emit('remove-attachment-item', this.attachment);
      }
    },
    retryUpload(attachment) {
      this.$root.$emit('retry-upload-file', attachment);
    },
    openSelectDestinationFolderForFile(attachment) {
      if (attachment && !attachment.fileDrive && this.currentDrive && this.attachmentDefaultFolderName) {
        attachment.fileDrive = this.currentDrive;
        attachment.pathDestinationFolderForFile = this.attachmentDefaultFolderName;
      }
      this.$root.$emit('change-attachment-destination-path', attachment);
    },
    absoluteDateModified(options) {
      const lang = eXo && eXo.env && eXo.env.portal && eXo.env.portal.language || 'en';
      return new Date(this.attachment.date).toLocaleString(lang, options).split('/').join('-');
    },
    fileInfo() {
      return `${this.$t('documents.preview.updatedOn')} ${this.absoluteDateModified()} ${this.$t('documents.preview.updatedBy')} ${this.attachment.lastEditor} ${this.attachment.size}`;
    },
    openFileInEditor(mode) {
      if (this.attachment && this.attachment.id) {
        const url = this.$documentsUtils.getEditorUrl(this.attachment,mode);
        window.open(url, '_blank');
      }
    },
    openFile() {
      if (this.openInEditor && this.isFileFillable && this.attachment.acl?.canEdit) {
        this.openFileInEditor('fillform');
      } else if (this.openInEditor && this.isFileEditable && this.attachment.acl?.canEdit) {
        this.openFileInEditor();
      } else if (this.openInEditor && this.isFileReadable)  {
        this.openFileInEditor('view');
      } else {
        const files = [];
        this.attachments.forEach((item) => {
          if (!this.isSupported(item)) {
            files.push({'id': item.id,'filename': item.title,'mimetype': item.mimetype,'downloadUrl': item.downloadUrl,'icon': this.getFileIcon(item)});
          }
        }
        );
        document.dispatchEvent(new CustomEvent('open-attachments-preview', {detail: {'attachments': files,'id': this.attachment.id }}));
      }
      this.markDocumentAsViewed();
    },
    isSupported(attachment) {
      const type = attachment && attachment.mimetype || '';
      return this.$supportedDocuments && this.$supportedDocuments.filter(doc => doc.mimeType === type).length > 0;
    },
    getFileIcon(attachment) {
      const extensions = this.$documentsIconsExtension;
      let extension = extensions[0].get(attachment?.mimetype);
      if (!extension) {
        extension = extensions[0].get('file');
      }
      return extension;
    },

  }
};
</script>
