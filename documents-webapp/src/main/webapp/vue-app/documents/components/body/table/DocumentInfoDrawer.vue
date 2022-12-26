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
      <v-list-item>
        <v-list-item-content class="ma-1">
          <a class="text-center not-clickable d-flex align-center">
            <v-spacer />
            <v-icon :color="iconColor">{{ iconClass }}</v-icon>
            <span
              class="fileName font-weight-bold text-color ms-2 px-2">
              {{ file.name }}
            </span>
            <span
              v-if="file.versionNumber"
              @click="showVersionHistory"
              class="item-version text-caption border-radius primary pa-0 px-1 clickable">
              V{{ file.versionNumber }}
            </span>
            <documents-favorite-action v-if="!file.folder" :file="file" />
            <v-spacer />
          </a>
        </v-list-item-content>
      </v-list-item>
      <div v-if="showNoDescription" class="d-flex flex-column justify-center text-center pa-8">
        <v-icon size="40" class="descriptionIcon"> mdi-message-text-outline </v-icon>
        <span class="descriptionText">{{ $t('documents.message.noDescription') }}</span>
        <a class="align-center" @click="openEditor">
          <span>{{ $t('documents.message.addYourDescription') }}</span>
        </a>
      </div>
      <div
        v-show="showDescription"
        :data-text="placeholder"
        :title="$t('tooltip.clickToEdit')"
        class="py-4 px-8 cursor-text"
        @click="openEditor"
        v-sanitized-html="file.description">
        {{ placeholder }}
      </div>
      <div v-show="displayEditor" class="py-4 px-8">
        <exo-activity-rich-editor
          ref="activityShareMessage"
          v-model="file.description"
          max-length="1300"
          :placeholder="$t('documents.alert.descriptionLimit')"
          class="flex" />
        <v-btn
          id="saveDescriptionButton"
          :loading="savingDescription"
          :disabled="disableButton"
          depressed
          outlined
          class="btn mt-2 ml-auto d-flex px-2 btn-primary v-btn v-btn--contained theme--light v-size--default"
          @click="updateDescription">
          {{ $t('documents.label.apply') }}
        </v-btn>
      </div>
      <v-divider dark />
      <template>
        <v-list-item>
          <v-list-item-content class="mt-4 mx-4">
            <v-list-item-title>
              <a
                class="fileDetails text-color d-flex">
                <span class="text-center not-clickable font-weight-bold">{{ $t('documents.drawer.details.modified') }}:</span>
                <date-format
                  :value="lastUpdated"
                  :format="fullDateFormat"
                  class="document-date not-clickable text-no-wrap mx-1" />
                {{ $t('documents.drawer.details.by') }}
                <exo-user-avatar
                  v-if="identityModifier && !isCurrentUserModifier"
                  :identity="identityModifier"
                  avatar-class="me-2"
                  size="42"
                  fullname
                  popover
                  bold-title
                  link-style
                  class="text-decoration-underline text-truncate font-weight-bold mx-1"
                  username-class />
                <p v-else class="text-decoration-underline primary--text not-clickable font-weight-bold mx-1">
                  {{ infoDrawerModifierLabel }}
                </p>
              </a>
            </v-list-item-title>
          </v-list-item-content>
        </v-list-item>

        <v-list-item>
          <v-list-item-content class="mx-4">
            <v-list-item-title>
              <a
                class="fileDetails text-color d-flex ">
                <span class="text-center not-clickable font-weight-bold">
                  {{ $t('documents.drawer.details.created') }}:</span>
                <date-format
                  :value="fileCreated"
                  :format="fullDateFormat"
                  class="document-date not-clickable text-no-wrap mx-1" />
                {{ $t('documents.drawer.details.by') }}

                <exo-user-avatar
                  v-if="identityCreated && !isCurrentUserCreator"
                  :identity="identityCreated"
                  avatar-class="me-2"
                  size="42"
                  fullname
                  popover
                  bold-title
                  link-style
                  extra-class="text-decoration-underline"
                  class="text-decoration-underline text-truncate font-weight-bold mx-1"
                  username-class />
                <p v-else class="text-decoration-underline not-clickable primary--text font-weight-bold mx-1">
                  {{ infoDrawerCreatorLabel }}
                </p>
              </a>
            </v-list-item-title>
          </v-list-item-content>
        </v-list-item>

        <v-list-item>
          <v-list-item-content class="mx-4">
            <v-list-item-title>
              <a
                class="fileDetails not-clickable text-color d-flex">
                <span class="text-center font-weight-bold">{{ $t('documents.drawer.details.size') }}:</span>
                <documents-file-size-cell class="mx-1 text-color" :file="file" />
              </a>
            </v-list-item-title>
          </v-list-item-content>
        </v-list-item>
      </template>
    </template>
  </exo-drawer>
</template>
<script>

export default {
  props: {
    selectedView: {
      type: String,
      default: '',
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
    fileType: null,
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
      return this.file?.modifierIdentity;
    },
    identityCreated(){
      return this.file?.creatorIdentity;
    },
    isMobile() {
      return this.$vuetify.breakpoint.name === 'xs' || this.$vuetify.breakpoint.name === 'sm';
    },
    disableButton() {
      return this.file.description && this.file.description.replace( /(<([^>]+)>)/ig, '').length>1300
      || this.file.description === this.fileInitialDescription 
      || (!this.file.description && !this.fileInitialDescription);
    },
  },
  created() {
    this.$root.$on('open-info-drawer', this.open);
    this.$root.$on('close-info-drawer', this.close);
    this.$root.$on('version-number-updated', (fileId) => {
      if (this.file && this.file.id === fileId) {
        this.file.versionNumber++;
      }
    });
    document.addEventListener('search-metadata-tag', this.close);
  },
  methods: {
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
      this.$documentFileService.updateDescription(ownerId,this.file)
        .then(() => {
          this.displayAlert(this.$t('documents.alert.success.description.updated'));
          this.showDescription = this.file.description && this.file.description.length;
          this.showNoDescription = !this.showDescription;
          this.displayEditor=false;
          this.fileInitialDescription = this.file.description;
        });
    },
    open(file, fileName, fileType, icon) {
      this.file = file;
      this.fileName = fileName;
      this.fileType = fileType;
      this.icon = icon;
      this.displayEditor = false;
      this.showNoDescription = !this.file.description && !this.displayEditor;
      this.showDescription = this.file.description && this.file.description.length && !this.displayEditor;
      this.fileInitialDescription = this.file.description;      
      this.$refs.documentInfoDrawer.open();
    },
    openEditor(){
      this.firstCreateDescription = this.showNoDescription;
      this.showNoDescription = false;
      this.showDescription = false;
      this.displayEditor=true;
      this.originDescription = this.file.description;
    },
    close() {
      this.file = null;
      this.displayEditor = false;
      this.showNoDescription = false;
      this.showDescription = true;
      this.displayEditor=true;
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
  }
};
</script>
