<template>
  <exo-drawer 
    ref="documentInfoDrawer"
    class="documentInfoDrawer"
    @closed="close"
    right>
    <template slot="title">
      {{ $t('documents.drawer.details.title') }}
    </template>
    <template v-if="file" slot="content">
      <div class="d-flex align-center justify-center flex-grow-1 text-center pt-2">
        <v-icon v-if="icon" :color="iconColor">{{ iconClass }}</v-icon>
        <span class="font-weight-bold text-body text-truncate ms-2 px-2">
          {{ file.name }}
        </span>
        <div class="d-flex align-center">
          <v-chip
            v-if="file.versionNumber"
            class="text-body clickable pa-2"
            color="primary"
            x-small
            label
            @click="showVersionHistory">
            V{{ file.versionNumber }}
          </v-chip>
          <documents-favorite-action
            v-if="!file.folder"
            :file="file"
            :is-mobile="isMobile" />
        </div>
      </div>

      <div v-if="showNoDescription">
        <div class="d-flex flex-row justify-center text-center pt-8">
          <v-icon size="40" class="descriptionIcon"> mdi-message-text-outline </v-icon>
        </div>
        <div class="d-flex flex-column justify-center text-center pb-8">
          <span class="descriptionText">{{ $t('documents.message.noDescription') }}</span>
          <a
            v-if="file.acl.canEdit"
            class="align-center"
            @click="openEditor">
            <span>{{ $t('documents.message.addYourDescription') }}</span>
          </a>
        </div>
      </div>
      <v-hover>
        <div slot-scope="{ hover }">
          <v-row class="py-4 px-8">
            <v-col class="px-0 py-0">
              <div
                v-show="showDescription"
                :data-text="placeholder"
                class="infoDescriptionToShow"
                :hover="hover"
                v-sanitized-html="file.description">
                {{ placeholder }}
              </div>
            </v-col>
            <v-col class="col-1 px-0 py-0">
              <v-tooltip :disabled="isMobile" bottom> 
                <template #activator="{ on, attrs }">
                  <v-icon
                    v-show="showDescription && (hover || isMobile)"
                    v-bind="attrs"
                    v-on="on"
                    class="primary--text"
                    size="16"
                    @click="openEditor">
                    {{ 'fa fa-edit' }}
                  </v-icon>
                </template>
                <span> {{ $t('documents.drawer.details.description.edit') }} </span>
              </v-tooltip>
            </v-col>
          </v-row>
        </div>
      </v-hover>
      <div v-show="displayEditor" class="py-4 px-8">
        <exo-activity-rich-editor
          ref="activityShareMessage"
          v-model="file.description"
          max-length="1300"
          :placeholder="$t('documents.alert.descriptionLimit')"
          class="flex" />
      </div>
      <v-divider dark />
      <template>
        <v-list-item>
          <v-list-item-content class="mt-4 mx-4">
            <div class="d-flex text-truncate">
              <span class="text-body font-weight-bold">{{ $t('documents.drawer.details.modified') }}:</span>
              <date-format
                :value="lastUpdated"
                :format="fullDateFormat"
                class="text-body not-clickable text-no-wrap mx-1" />
              {{ $t('documents.drawer.details.by') }}
              <exo-user-avatar
                v-if="identityModifier && !isCurrentUserModifier"
                :profile-id="identityModifier"
                avatar-class="me-2"
                size="42"
                fullname
                popover
                bold-title
                link-style
                class="text-decoration-underline text-body text-truncate font-weight-bold mt-0 mx-1"
                username-class />
              <p v-else class="text-decoration-underline primary--text not-clickable font-weight-bold mx-1">
                {{ infoDrawerModifierLabel }}
              </p>
            </div>
          </v-list-item-content>
        </v-list-item>

        <v-list-item>
          <v-list-item-content class="mx-4">
            <div
              class="d-flex text-truncate">
              <span class="text-body font-weight-bold">
                {{ $t('documents.drawer.details.created') }}:</span>
              <date-format
                :value="fileCreated"
                :format="fullDateFormat"
                class="text-body text-no-wrap mx-1" />
              {{ $t('documents.drawer.details.by') }}

              <exo-user-avatar
                v-if="identityCreated && !isCurrentUserCreator"
                :profile-id="identityCreated"
                avatar-class="me-2"
                size="42"
                fullname
                popover
                bold-title
                link-style
                extra-class="text-decoration-underline"
                class="text-decoration-underline text-body text-truncate font-weight-bold mt-0 mx-1"
                username-class />
              <p v-else class="text-decoration-underline not-clickable primary--text font-weight-bold mx-1">
                {{ infoDrawerCreatorLabel }}
              </p>
            </div>
          </v-list-item-content>
        </v-list-item>

        <v-list-item>
          <v-list-item-content class="mx-4">
            <div
              class="d-flex">
              <span class="text-center text-body font-weight-bold">{{ $t('documents.drawer.details.size') }}:</span>
              <documents-file-size-cell
                class="mx-1 text-body"
                :file="file"
                prop-name="size"
                :is-mobile="isMobile" />
            </div>
          </v-list-item-content>
        </v-list-item>
        <v-list-item>
          <v-list-item-content class="mx-4">
            <div
              class="d-flex">
              <span class="text-center text-body font-weight-bold">{{ $t('documents.drawer.details.sizeWithVersions') }}:</span>
              <documents-file-size-cell
                class="mx-1 text-body"
                :file="file"
                prop-name="sizeWithVersions"
                :is-mobile="isMobile" />
            </div>
          </v-list-item-content>
        </v-list-item>
        <v-list-item v-if="!file.folder">
          <v-list-item-content class="mx-4">
            <div
              class="d-flex">
              <span class="text-center text-body font-weight-bold">{{ $t('documents.details.view.label') }}:</span>
              <span class="ms-1">{{ $t('documents.details.views.label', {0: `${file.views}`}) }}</span>
            </div>
          </v-list-item-content>
        </v-list-item>
        <v-list-item>
          <v-list-item-content class="mx-4">
            <div
              class="d-flex">
              <span class="text-center text-body font-weight-bold">{{ $t('documents.details.view.location') }}:</span>
              <a
                class="ms-1 document-location"
                :href="fileLocationLink"
                @click="openLocation"> {{ fileLocation }} </a>
            </div>
          </v-list-item-content>
        </v-list-item>
      </template>
    </template>   
    <template slot="footer">
      <div class="d-flex">
        <v-spacer />
        <v-btn
          v-show="displayEditor"
          id="saveDescriptionButton"
          :loading="savingDescription"
          :disabled="disableButton"
          depressed
          class="primary btn no-box-shadow ms-auto"
          @click="updateDescription">
          {{ $t('documents.label.apply') }}
        </v-btn>
      </div>
    </template>
  </exo-drawer>
</template>
<script>

export default {
  props: {
    selectedView: {
      type: String,
      default: '',
    },
    isMobile: {
      type: Boolean,
      default: false
    }
  },
  data: () => ({
    fullDateFormat: {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    },
    currentUser: eXo.env.portal.userName,
    file: null,
    fileName: null,
    icon: null,
    displayEditor: false,
    showNoDescription: false,
    showDescription: false,
    firstCreateDescription: false,
    fileInitialDescription: '',
  }),
  computed: {
    iconColor(){
      return this.icon && this.icon.color;
    },
    iconClass(){
      return this.icon && this.icon.class;
    },
    lastUpdated() {
      return this.file && (this.file.modifiedDate || this.file.createdDate) || '';
    },
    fileCreated() {
      return this.file && this.file.createdDate || '';
    },
    infoDrawerCreatorLabel() {
      return this.currentUser === this.file?.creatorIdentity?.remoteId ?
        this.$t('documents.drawer.details.me') :
        this.$t('documents.drawer.details.system');
    },
    infoDrawerModifierLabel() {
      return this.currentUser === this.file?.modifierIdentity?.remoteId ?
        this.$t('documents.drawer.details.me') :
        this.$t('documents.drawer.details.system');
    },
    isCurrentUserModifier() {
      return this.currentUser === this.file?.modifierIdentity?.remoteId;
    },
    isCurrentUserCreator() {
      return this.currentUser === this.file?.creatorIdentity?.remoteId;
    },
    identityModifier(){
      return this.file?.modifierIdentity?.remoteId;
    },
    identityCreated(){
      return this.file?.creatorIdentity?.remoteId;
    },
    disableButton() {
      return this.file?.description && this.file?.description.replace( /(<([^>]+)>)/ig, '').length>1300
      || this.file?.description === this.fileInitialDescription
      || (!this.file?.description && !this.fileInitialDescription);
    },
    fileLocation() {
      let pathParts = [];
      if (this.file.path.includes('/Groups/spaces/')){
        pathParts = this.file.path.split('/Groups/spaces/')[1].split('/');
      } else if (this.file.path.includes(eXo.env.portal.userName)){
        const partToRemove = this.file.path.split(eXo.env.portal.userName)[0];
        pathParts = this.file.path.replace(partToRemove,'').split('/');
      }
      pathParts.shift();
      pathParts.pop();
      return pathParts.join('/');
    },
    fileLocationLink() {
      const realPageUrlIndex = window.location.href.toLowerCase().indexOf(eXo.env.portal.selectedNodeUri.toLowerCase()) + eXo.env.portal.selectedNodeUri.length;
      const url = new URL(window.location.href.substring(0, realPageUrlIndex));
      url.searchParams.set('folderId', this.file.parentFolderId);
      return url.toString();
    },
  },
  watch: {
    showDescription() {
      this.$refs.activityShareMessage?.initCKEditorData(this.file.description);
    }
  },
  created() {
    document.addEventListener('open-info-drawer', this.open);
    this.$root.$on('version-number-updated', (fileId) => {
      if (this.file && this.file.id === fileId) {
        this.file.versionNumber++;
      }
    });
    document.addEventListener('document-views-updated', this.handleUpdateViews);
    document.addEventListener('search-metadata-tag', this.close);
  },
  methods: {
    handleUpdateViews(event) {
      if (this.file?.id === event.detail?.file?.id) {
        this.file.views = event.detail?.views;
      }
    },
    showVersionHistory() {
      this.$root.$emit('show-version-history', this.file);
    },
    updateDescription(){
      if (this.firstCreateDescription){
        this.addDescriptionStatistics(this.file);
      } else {
        this.updateDescriptionStatistics(this.file);
      }
      const ownerId = eXo.env.portal.spaceIdentityId || eXo.env.portal.userIdentityId;
      return this.$documentFileService.updateDescription(ownerId,this.file)
        .then(() => {
          if (this.isMobile){
            this.displayAlert(this.$t('documents.alert.success.description.updated'));
          } else {
            this.$root.$emit('show-alert', {
              type: 'success',
              message: this.$t('documents.alert.success.description.updated')
            });
          }
          this.showDescription = this.file.description && this.file.description.length;
          this.showNoDescription = !this.file.description;
          this.displayEditor=false;
          this.fileInitialDescription = this.file.description;
          this.$refs.activityShareMessage?.initCKEditorData(this.file.description);
        }).catch(() => {
          this.$root.$emit('show-alert', {
            type: 'error',
            message: this.$t('documents.alert.error.description.updated')
          });
        });
    },
    open(event) {
      const fileId = event?.detail;
      this.$attachmentService.getDocumentDetails(fileId)
        .then(file => {
          this.file = file;
          this.icon = file.icon;
          this.displayEditor = false;
          this.showNoDescription = !this.file.description && !this.displayEditor;
          this.showDescription = this.file.description && this.file.description.length && !this.displayEditor;
          this.fileInitialDescription = this.file.description;      
          this.$nextTick(()=>{
            this.$refs.documentInfoDrawer.open();
            this.$refs.activityShareMessage?.initCKEditorData(this.file.description);
          });
        });
      
    },
    openEditor(){
      this.firstCreateDescription = this.showNoDescription;
      this.showNoDescription = false;
      this.showDescription = false;
      this.displayEditor=true;
      this.originDescription = this.file.description;
      if (!this.originDescription.length) {
        this.$refs.activityShareMessage?.initCKEditorData('');
      }
    },
    close() {
      this.file.description = this.fileInitialDescription;
      this.displayEditor = false;
      this.showNoDescription = false;
      this.showDescription = true;
      this.$refs.documentInfoDrawer.close();

    },
    displayAlert(message, type) {
      document.dispatchEvent(new CustomEvent('attachments-notification-alert', {
        detail: {
          messageObject: {
            message: message,
            type: type || 'success',
          }
        }
      }));
    },
    addDescriptionStatistics(file) {
      document.dispatchEvent(new CustomEvent('exo-statistic-message', {
        detail: {
          module: 'Drive',
          subModule: 'Documents',
          userId: eXo.env.portal.userIdentityId,
          userName: eXo.env.portal.userName,
          name: 'actionCreateDescription',
          operation: 'createDescription',
          parameters: {
            documentName: file.name,
            category: this.file.folder ? 'folderCategory' : 'documentCategory',
            spaceId: eXo.env.portal.spaceId,
            view: this.selectedView === 'timeline' ? 'recentView': 'folderView',
          },
          timestamp: Date.now()
        }
      }));
    },
    updateDescriptionStatistics(file) {
      document.dispatchEvent(new CustomEvent('exo-statistic-message', {
        detail: {
          module: 'Drive',
          subModule: 'Documents',
          userId: eXo.env.portal.userIdentityId,
          userName: eXo.env.portal.userName,
          name: 'actionUpdateDescription',
          operation: 'updateDescription',
          parameters: {
            documentName: file.name,
            category: this.file.folder ? 'folderCategory' : 'documentCategory',
            spaceId: eXo.env.portal.spaceId,
            view: this.selectedView === 'timeline' ? 'recentView': 'folderView',
          },
          timestamp: Date.now()
        }
      }));
    },
    openLocation() {
      this.$root.$emit('open-folder-by-id', this.file.parentFolderId);
    },
  }
};
</script>
