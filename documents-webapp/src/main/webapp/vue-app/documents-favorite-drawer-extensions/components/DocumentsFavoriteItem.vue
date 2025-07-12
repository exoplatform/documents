<template>
  <v-list-item
    v-bind="url && {
      href: url,
      target: '_blank',
    }"
    @keydown.enter="openPreview"
    @auxclick="setAsViewed"
    @click="openPreview">
    <v-list-item-icon class="me-3 my-auto">
      <v-card
        :min-width="iconWidth"
        class="d-flex justify-center no-border-radius"
        color="transparent"
        flat>
        <v-icon
          :size="iconSize" 
          :color="iconColor">
          {{ iconClass }}
        </v-icon>
      </v-card>
    </v-list-item-icon>
    <v-list-item-content>
      <v-list-item-title class="text-truncate">{{ fileName }}</v-list-item-title>
      <v-list-item-subtitle v-if="expanded" class="d-flex align-center full-width overflow-hidden pt-2px">
        <template v-if="spaceGroupId">
          <space-avatar
            :space-group-id="spaceGroupId"
            :size="16"
            class="flex-grow-0 flex-shrink-1 text-truncate"
            link-style />
          <v-icon class="flex-grow-0 flex-shrink-0 mx-2" size="2">fa-circle</v-icon>
        </template>
        <template v-else-if="ownerUsername">
          <div class="d-flex align-center flex-grow-0 flex-shrink-1 text-truncate">
            <user-avatar
              :profile-id="ownerUsername"
              :size="16"
              avatar />
            {{ $t('UITopBarFavoritesPortlet.personalDrive') }}
          </div>
          <v-icon class="flex-grow-0 flex-shrink-0 mx-2" size="2">fa-circle</v-icon>
        </template>
        <date-format class="flex-grow-0 flex-shrink-0" :value="updateDate" />
        <v-icon class="flex-grow-0 flex-shrink-0 mx-2" size="2">fa-circle</v-icon>
        <user-avatar
          :identity="updater"
          :size="16"
          class="flex-grow-0 flex-shrink-1 text-truncate" />
      </v-list-item-subtitle>
    </v-list-item-content>
    <v-list-item-action>
      <documents-favorite-button
        :id="id"
        :file="file"
        :top="top"
        :right="right"
        standalone
        @added="added"
        @removed="removed" />
    </v-list-item-action>
  </v-list-item>
</template>
<script>
export default {
  props: {
    id: {
      type: String,
      default: () => null,
    },
    clickCallback: {
      type: Function,
      default: null,
    },
    expanded: {
      type: Boolean,
      default: false,
    },
  },
  data: () => ({
    file: {},
    isFavorite: true,
  }),
  computed: {
    iconWidth() {
      return this.expanded ? 40 : 30;
    },
    iconSize() {
      return this.expanded ? 34 : 24;
    },
    fileType() {
      return this.file?.mimetype || '';
    },
    isFileEditable() {
      return this.$supportedDocuments?.filter?.(doc => doc.edit && doc.mimeType === this.fileType && !this.file.cloudDrive)?.length;
    },
    isFileReadable() {
      return this.$supportedDocuments?.filter?.(doc => doc.mimeType === this.fileType)?.length;
    },
    fileName() {
      return this.$utils.htmlToText(window.decodeURIComponent(this.file?.title));
    },
    fileIcon() {
      return this.$documentsIconsExtension[0].get(this.fileType) || this.$documentsIconsExtension[0].get('file');
    },
    iconColor() {
      return this.fileIcon?.color;
    },
    iconClass() {
      return this.fileIcon?.class;
    },
    downloadUrl() {
      return this.file.downloadUrl;
    },
    updater() {
      return this.file?.updater?.profile;
    },
    updateDate() {
      return this.file?.updated || this.file?.created;
    },
    spaceGroupId() {
      return this.file?.path?.startsWith?.('/Groups/spaces/') ? this.getSpaceGroupId(this.file.path) : null;
    },
    ownerUsername() {
      return this.file?.path?.startsWith?.('/Users/') && this.file?.path?.includes?.('/Private/') ? this.getUsername(this.file.path) : null;
    },
    url() {
      if (this.isFileEditable || this.isFileReadable) {
        return `${eXo.env.portal.context}/${eXo.env.portal.metaPortalName}/oeditor?docId=${this.file.id}${this.isFileReadable && !this.isFileEditable && '&mode=view' || ''}&backTo=${window.location.pathname}`;
      } else {
        return null;
      }
    },
  },
  async created() {
    const file = await this.$attachmentService.getAttachmentById(this.id);
    this.file = {
      ...file,
      name: file.path.split('/').pop(),
      metadatas: {
        favorites: [file.id],
      },
    };
  },
  methods: {
    openPreview(event) {
      this.setAsViewed(event);
      if (!this.url) {
        document.dispatchEvent(new CustomEvent('open-attachments-preview', {detail: {
          attachments: [{
            id: this.id,
            mimetype: this.fileType,
            downloadUrl: this.downloadUrl,
            filename: this.fileName,
            source: 'documents'
          }],
          id: this.id,
        }}));
      }
    },
    added() {
      this.isFavorite = true;
      this.$emit('added');
      this.$root.$emit('refresh-favorite-list');
    },
    removed() {
      this.isFavorite = false;
      this.$emit('removed');
      this.$root.$emit('refresh-favorite-list');
    },
    getSpaceGroupId(path) {
      const parts = path.split('/').filter(t => t?.length);
      return `/${parts[1]}/${parts[2]}`;
    },
    getUsername(path) {
      console.warn('Username: ', path.split('/Private/').shift().split('/').pop());
      return path.split('/Private/').shift().split('/').pop();
    },
    setAsViewed(event) {
      if (event.which === 1 || event.which === 2) {
        this.clickCallback('file', this.id);
      }
    },
  }
};
</script>
