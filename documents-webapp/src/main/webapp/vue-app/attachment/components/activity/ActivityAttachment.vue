<template>
  <v-hover v-slot="{hover}" :class="marginClass">
    <v-card
      :id="id"
      :elevation="hover ? 4 : 0"
      :class="{ 'border-color': !hover }"
      :loading="loading"
      height="210px"
      max-height="210px"
      width="252px"
      max-width="100%"
      class="activity-attachment overflow-hidden d-flex flex-column clickable border-box-sizing">
      <v-card-text
        class="activity-attachment-thumbnail d-flex flex-grow-1 pa-0"
        :class="isMediaFile && 'black'"
        @click="openPreview">
        <video
          v-if="showPlayer"
          :src="`${attachment.downloadUrl}#t=0.001`"
          controls="controls"
          autoplay
          class="black mx-auto full-height full-width position-absolute"
          @error="playError($event)">
        </video>
        <div
          v-else-if="isMediaFile"
          class="ma-auto black"></div>
        <img
          v-else-if="image"
          :src="image"
          class="ma-auto"
          loading="lazy"
          @error="image = image!==attachment.downloadUrl?attachment.downloadUrl:null">
        <v-icon
          v-else
          :class="fileIconClass"
          :color="fileIconColor"
          class="ma-auto d-flex"
          size="80px" />
      </v-card-text>
      <v-card-text v-if="!image && !isMediaFile && !hover" class="activity-attachment-title d-flex font-weight-bold border-top-color py-2">
        <div
          :title="attachment.name"
          class="text-color text-wrap text-break mx-0 my-auto text-truncate-2"
          v-text="attachment.name"></div>
      </v-card-text>
      <v-expand-transition>
        <v-card
          v-if="isMediaFile && !showPlayer"
          class="d-flex flex-column transition-fast-in-fast-out light-grey-background v-card--reveal"
          elevation="0"
          style="height: 100%;"
          @click="openPreview">
          <v-card-text class="pb-0 d-flex flex-row">
            <div class="absolute-all-center align-center" @click="playMedia($event)">
              <v-icon color="white" size="60px">mdi-play-circle-outline</v-icon>
            </div>
          </v-card-text>
        </v-card>
      </v-expand-transition>
      <v-expand-transition>
        <v-card
          v-if="hover && !loading && !invalid && !showPlayer"
          class="d-flex flex-column transition-fast-in-fast-out mask-color v-card--reveal"
          elevation="0"
          style="height: 30%;">
          <v-card-text class="activity-attachment-title d-flex font-weight-bold  py-2">
            <div
              :title="attachment.name"
              class="white--text text-wrap text-break mx-0 my-auto text-truncate-2"
              v-text="attachment.name">
            </div>
          </v-card-text>
          <v-card-actions class="pt-0 position-absolute b-0 r-0  ma-0 pa-0">
            <v-btn
              id="attachment-info"
              @click="showInfo()"
              :title="$t('attachments.label.details')"
              small
              icon
              class="white--text ma-0 pa-0">
              <v-icon size="16">fa-info-circle</v-icon>
            </v-btn>
            <v-btn
              id="attachment-download"
              :href="attachment.downloadUrl"
              :download="attachment.name"
              :title="$t('attachments.label.download')"
              small
              icon
              class="white--text ma-0 pa-0">
              <v-icon size="16">fa-download</v-icon>
            </v-btn>
          </v-card-actions>
        </v-card>
      </v-expand-transition>
      <v-expand-transition>
        <v-card
          v-if="invalid"
          class="d-flex flex-column transition-fast-in-fast-out disabled-background v-card--reveal"
          elevation="0"
          style="height: 100%;">
          <v-card-text class="pb-0 d-flex flex-row">
            <v-icon color="error">fa-exclamation-circle</v-icon>
            <p class="my-auto ms-2 font-weight-bold text-truncate-2">
              {{ playErrorLabel }}
            </p>
          </v-card-text>
          <v-card-text class="flex-grow-1">
            <p class="text-truncate-2">{{ playErrorDescription }}</p>
          </v-card-text>
          <v-card-actions>
            <v-btn
              v-if="showDownloadButton"
              :href="attachment.downloadUrl"
              :download="attachment.name"
              :title="$t('attachments.label.download')"
              medium
              icon
              class="my-auto">
              <v-icon size="20">fa-download</v-icon>
            </v-btn>
            <v-spacer/>
            <v-btn
              text
              color="primary"
              @click="closeErrorBox">
              {{ $t('attachments.close') }}
            </v-btn>
          </v-card-actions>
        </v-card>
      </v-expand-transition>
    </v-card>
  </v-hover>
</template>

<script>
export default {
  props: {
    attachment: {
      type: Object,
      default: null,
    },
    attachments: {
      type: Array,
      default: () => []
    },
    activity: {
      type: Object,
      default: null,
    },
    index: {
      type: Number,
      default: 0,
    },
    count: {
      type: Number,
      default: 0,
    },
    previewHeight: {
      type: String,
      default: () => '152px',
    },
    previewWidth: {
      type: String,
      default: () => '250px',
    },
  },
  data: () => ({
    loading: false,
    invalid: false,
    showPlayer: false,
    showDownloadButton: false,
    image: false,
    playErrorLabel: '',
    playErrorDescription: '',
    dateFormat: {
      year: 'numeric',
      month: 'long',
      day: 'numeric',
      hour: 'numeric',
      minute: 'numeric',
      second: 'numeric',
    },
  }),
  computed: {
    id() {
      return `PreviewAttachment_${this.previewActivity.id}_${this.index}`;
    },
    nextId() {
      return (this.index + 1) < this.count && `#PreviewAttachment_${this.previewActivity.id}_${this.index + 1}` || '';
    },
    previousId() {
      return this.index && `#PreviewAttachment_${this.previewActivity.id}_${this.index - 1}` || '';
    },
    marginClass() {
      if (this.count === 1) {
        return 'mx-auto';
      }
      const lastIndex = (this.count - 1) === this.index;
      return this.index && (lastIndex && 'ms-2' || 'mx-2') || 'me-2';
    },
    username() {
      return this.previewActivity && this.previewActivity.identity.profile && this.previewActivity.identity.profile.username || '';
    },
    fullname() {
      return this.previewActivity && this.previewActivity.identity.profile && this.previewActivity.identity.profile.fullname || '';
    },
    avatarUrl() {
      return this.previewActivity && this.previewActivity.identity.profile && this.previewActivity.identity.profile.avatar || '';
    },
    profileUrl() {
      return `${eXo.env.portal.context}/${eXo.env.portal.portalName}/profile/${this.username}`;
    },
    author() {
      return {
        username: this.username,
        fullname: this.fullname,
        avatarUrl: this.avatarUrl,
        profileUrl: this.profileUrl,
      };
    },

    fileIconClass() {
      return this.attachment.icon?.class || 'fas fa-file';
    },
    fileIconColor() {
      return this.attachment.icon?.color || 'secondary';
    },
    previewActivity() {
      return this.activity && this.activity.parentActivity || this.activity;
    },
    activityDate() {
      return this.previewActivity && this.previewActivity.createDate && new Date(this.previewActivity.createDate);
    },
    relativePostTimeLabel() {
      return this.activityDate && this.$dateUtil.getRelativeTimeLabelKey(this.activityDate) || '';
    },
    relativePostTimeDate() {
      return this.activityDate && this.$dateUtil.getRelativeTimeValue(this.activityDate) || 1;
    },
    relativePostTime() {
      return this.activityDate && this.$t(this.relativePostTimeLabel, {0: this.relativePostTimeDate}) || '';
    },
    spaceURL() {
      return this.previewActivity && this.previewActivity.activityStream && this.previewActivity.activityStream.space && this.previewActivity.activityStream.space.groupId.replace('/spaces/', '');
    },
    isCommentActivity() {
      return this.activity && this.activity.activityId;
    },
    isMediaFile() {
      return this.attachment && this.attachment.mimetype && (this.attachment.mimetype.includes('video/')|| this.attachment.mimetype.includes('audio/'));
    }
  },
  created() {
    this.image = this.attachment && this.attachment.image;
  },
  methods: {
    markDocumentAsViewed() {
      document.dispatchEvent(new CustomEvent('mark-attachment-as-viewed', {detail: {file: this.attachment}}));
    },
    closeErrorBox(event) {
      if (event) {
        event.preventDefault();
        event.stopPropagation();
      }
      window.setTimeout(() => {
        this.invalid = false;
      }, 50);
      this.showDownloadButton = false;
    },
    openPreview() {
      if (this.invalid) {
        return;
      }
      this.loading = true;
      if (this.attachment.editable)  {
        this.$attachmentService.getDocumentDetails(this.attachment.id)
          .then(document => {
            if (document?.acl?.canEdit){
              this.openFileInEditor();
            } else {
              this.openFileInEditor('view');
            }
          });
      } else if (this.attachment.readable)  {
        this.openFileInEditor('view');
      } else {
        document.dispatchEvent(new CustomEvent('open-attachments-preview', {detail: {'attachments': this.attachments.filter(doc => !doc.readable),'id': this.attachment.id }}));
      }
      this.markDocumentAsViewed();
      this.loading = false;
    },
    openFileInEditor(mode) {
      if (this.attachment && this.attachment.id) {
        let url = `${eXo.env.portal.context}/${eXo.env.portal.portalName}/oeditor?docId=${this.attachment.id}&backTo=${window.location.pathname}`;
        if (mode) {
          url += `&mode=${mode}`;
        }
        window.open(url, '_blank');
      }
    },
    showInfo(event) {
      if (event) {
        event.preventDefault();
        event.stopPropagation();
      }
      document.dispatchEvent(new CustomEvent('open-document-info-drawer', {detail: this.attachment.id}));
    },
    playMedia(event) {
      if (event) {
        event.preventDefault();
        event.stopPropagation();
      }
      this.showPlayer = true;
    },
    playError(event){
      const video = event.target;
      if (video.error && video.error.code === 3 || video.error.code === 4) {
        this.playErrorLabel= this.$t('attachments.errorVideoFormatNotSupported');
        this.playErrorDescription=this.$t('attachments.alert.videoFormatNotSupported');
        this.showDownloadButton = true;
      } else {
        this.playErrorLabel= this.$t('attachments.errorAccessingFile');
        this.playErrorDescription=this.$t('attachments.alert.unableToAccessFile');
      }
      this.invalid = true;
      this.showPlayer = false;
    }
  },
};
</script>
