<template>
  <exo-drawer 
    ref="documentInfoDrawer"
    class="documentInfoDrawer"
    @closed="close"
    right>
    <template slot="title">
      {{ $t('documents.drawer.details.title') }}
    </template>
    <template #titleIcons>
      <favorite-button
        :id="file.id"
        :space-id="spaceId"
        :favorite="isFavorite"
        type="file"
        type-label="Documents"
        :small="false"
        @removed="favoriteRemoved"
        @remove-error="removeFavoriteError"
        @added="favoriteAdded"
        @add-error="addFavoriteError" />
      <v-tooltip bottom>
        <template #activator="{on, bind}">
          <div v-on="on" v-bind="bind">
            <v-btn
              icon
              @click="copyLink">
              <v-icon size="18">fas fa-link</v-icon>
            </v-btn>
          </div>
        </template>
        <span>$t('documents.label.copy.link')</span>
      </v-tooltip>
      <v-tooltip bottom>
        <template #activator="{on, bind}">
          <v-btn
            :href="downloadUrl"
            icon
            v-on="on"
            v-bind="bind"
            download>
            <v-icon size="18">fas fa-download</v-icon>
          </v-btn>
        </template>
        <span>{{ $t('documents.label.download') }}</span>
      </v-tooltip>
    </template>
    <template v-if="file" slot="content">
      <v-card
        class="d-flex flex-column elevation-0 pt-2">
        <v-card
          class="d-flex flex-column"
          elevation="0">
          <v-card-text class="font-weight-bold pb-4 pt-0 py-auto">
            <v-icon
              size="20"
              :class="icon.class"
              :color="icon.color" />
            <span>{{ file.name }}</span>
          </v-card-text>
        </v-card>
        <v-hover v-slot="{hover}">
          <v-card
            :id="id"
            :elevation="hover ? 4 : 0"
            :class="{ 'border-color': !hover }"
            :loading="loading"
            max-height="250px"
            max-width="250px"
            class="overflow-hidden d-flex flex-column clickable border-box-sizing mx-auto no-border-radius mb-3"
            @click="openPreview">
            <v-card-text
              class="d-flex flex-grow-1 pa-0">
              <img
                v-if="!showIcon"
                :src="thumbnailUrl"
                class="ma-auto"
                alt="No thumbnail"
                @load="loading = false"
                @error="loading = false; showIcon = true">
                <v-icon
                v-else
                size="80px"
                :class="icon.class"
                :color="icon.color"
                class="ma-auto" />  
            </v-card-text>
          </v-card>
        </v-hover>
        <v-divider dark />
        <v-list-item>
          <v-list-item-content class="py-1">
            <v-list-item-title class="text-title py-0">
              {{ $t('documents.drawer.details.details') }}
            </v-list-item-title>
          </v-list-item-content>
        </v-list-item>
        <v-list-item dense two-line>
          <v-list-item-content class="pt-0 pb-2">
            <v-list-item-title>{{ $t('documents.drawer.details.type') }}</v-list-item-title>
            <v-list-item-subtitle> {{ $t(`documents.label.type.${icon.type}`) }} </v-list-item-subtitle>
          </v-list-item-content>
        </v-list-item>
        <v-list-item dense two-line>
          <v-list-item-content class="pt-0 pb-2">
            <v-list-item-title>{{ $t('documents.drawer.details.size') }}</v-list-item-title>
            <v-list-item-subtitle>
              {{ fileSize.value }} {{ $t(`document.size.label.unit.${fileSize.unit}`) }} ({{ fileWithVersionsSize.value }} {{ $t(`document.size.label.unit.${fileWithVersionsSize.unit}`) }} {{ $t('documents.drawer.details.sizeWithVersions') }})              
            </v-list-item-subtitle>
          </v-list-item-content>
        </v-list-item>
        <v-list-item dense two-line>
          <v-list-item-content class="pt-0 pb-2">
            <v-list-item-title>{{ $t('documents.drawer.details.created') }}</v-list-item-title>
            <v-list-item-subtitle class="d-flex flex-row">
              <date-format
                :value="lastUpdated"
                :format="fullDateFormat" />
              <span class="mx-1">{{ $t('documents.drawer.details.by') }}</span>
              <exo-user-avatar
                v-if="identityCreated && !isCurrentUserCreator"
                :profile-id="identityCreated"
                avatar-class="me-2"
                size="42"
                fullname
                popover
                bold-title
                link-style
                class="text-decoration-underline text-truncate font-weight-bold mt-0"
                username-class />
              <span v-else class="text-decoration-underline primary--text not-clickable font-weight-bold mx-1">
                {{ infoDrawerCreatorLabel }}
              </span>
            </v-list-item-subtitle>
          </v-list-item-content>
        </v-list-item>
        <v-list-item dense two-line>
          <v-list-item-content class="pt-0 pb-2">
            <v-list-item-title>{{ $t('documents.drawer.details.modified') }}</v-list-item-title>
            <v-list-item-subtitle class="d-flex flex-row">
              <date-format
                :value="lastUpdated"
                :format="fullDateFormat" />
              <span class="mx-1">{{ $t('documents.drawer.details.by') }}</span>
              <exo-user-avatar
                v-if="identityModifier && !isCurrentUserModifier"
                :profile-id="identityModifier"
                avatar-class="me-2"
                size="42"
                fullname
                popover
                bold-title
                link-style
                class="text-decoration-underline text-truncate font-weight-bold mt-0"
                username-class />
              <span v-else class="text-decoration-underline primary--text font-weight-bold mx-1">
                {{ infoDrawerModifierLabel }}
              </span>
            </v-list-item-subtitle>
          </v-list-item-content>
        </v-list-item>
        <v-list-item dense two-line>
          <v-list-item-content class="pt-0 pb-2">
            <v-list-item-title>{{ $t('documents.details.view.label') }}</v-list-item-title>
            <v-list-item-subtitle>{{ $t('documents.details.views.label', {0: `${file.views}`}) }}</v-list-item-subtitle>
          </v-list-item-content>
        </v-list-item>
        <v-list-item dense two-line>
          <v-list-item-content class="pt-0 pb-2">
            <v-list-item-title>{{ $t('documents.drawer.details.location') }}</v-list-item-title>
            <v-list-item-subtitle>
              <a
                class="document-location"
                :href="fileLocationLink"
                @click="openLocation"> {{ fileLocation }} </a>
            </v-list-item-subtitle>
          </v-list-item-content>
        </v-list-item>
        <v-list-item>
          <v-list-item-content class="py-1">
            <v-list-item-title class="text-title py-0">
              {{ $t('documents.drawer.details.description') }}
            </v-list-item-title>
          </v-list-item-content>
        </v-list-item>
        <v-list-item>
          <v-list-item-content class="pt-0 pb-4">
            <div v-if="showNoDescription" class="documentNoDescription">
              <div class="d-flex flex-row justify-center text-center pt-4">
                <v-icon size="40" class="descriptionIcon"> fas fa-file-alt </v-icon>
              </div>
              <div class="d-flex flex-column justify-center text-center pt-2 pb-8">
                <a
                  v-if="file.acl.canEdit"
                  class="align-center font-weight-bold"
                  @click="openEditor">
                  <span>{{ $t('documents.message.addYourDescription') }}</span>
                </a>
              </div>
            </div>
            <v-hover>
              <div slot-scope="{ hover }">
                <v-row class="px-4">
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
            <div v-show="displayEditor">
              <exo-activity-rich-editor
                ref="activityShareMessage"
                v-model="file.description"
                max-length="1300"
                :placeholder="$t('documents.alert.descriptionLimit')"
                class="flex" />
            </div>
          </v-list-item-content>
        </v-list-item>
      </v-card>
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
    displayEditor: false,
    showNoDescription: false,
    showDescription: false,
    loading: true,
    firstCreateDescription: false,
    fileInitialDescription: '',
    isFavorite: false,
    showIcon: false,
  }),
  computed: {
    
    spaceId() {
      return eXo.env.portal.spaceId || 0;
    },
    icon(){
      return Vue.prototype.$documentsIconsExtension[0]?.get(this.file?.mimeType);
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
      let url = new URL(window.location.href);
      const nodeUriIndex = window.location.href.toLowerCase().indexOf(eXo.env.portal.selectedNodeUri.toLowerCase());
      if (nodeUriIndex !== -1) {
        const realPageUrlIndex = nodeUriIndex + eXo.env.portal.selectedNodeUri.length;
        url = new URL(window.location.href.substring(0, realPageUrlIndex));
      }
      url.searchParams.set('folderId', this.file.parentFolderId);
      return url.toString();
    },
    isFileEditable() {
      return  this.$supportedDocuments && this.$supportedDocuments.filter(doc => doc.edit && doc.mimeType === this.file.mimeType ).length > 0;
    },
    isFileReadable() {
      return this.$supportedDocuments && this.$supportedDocuments.filter(doc => doc.mimeType === this.file.mimeType).length > 0;
    },
    downloadUrl() {
      return this.$documentsUtils.getDownloadUrl(this.file.id,this.file.modifiedDate);
    },
    thumbnailUrl() {
      const file = this.file;
      file.readable = this.isFileReadable;
      return this.$documentsUtils.getThumbnailUrl(file,'250x250',this.lastUpdated);
    },
    fileSize() {
      return this.$documentsUtils.getSize(this.file.size);
    },
    fileWithVersionsSize() {
      return this.$documentsUtils.getSize(this.file.sizeWithVersions);
    },
  },
  watch: {
    showDescription() {
      this.$refs.activityShareMessage?.initCKEditorData(this.file.description);
    }
  },
  created() {
    document.addEventListener('open-info-drawer', this.open);
    document.addEventListener('document-views-updated', this.handleUpdateViews);
    document.addEventListener('search-metadata-tag', this.close);
    document.addEventListener('click', (event) => {
      if (event.target.closest('.documentInfoDrawer') || event.target.closest('.documentNoDescription')) {return;}
      this.close();
    });
  },
  methods: {
    handleUpdateViews(event) {
      if (this.file?.id === event.detail?.file?.id) {
        this.file.views = event.detail?.views;
      }
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
      const expand =  'modifier,creator,owner,metadatas';
      this.$attachmentService.getDocumentDetails(fileId,expand)
        .then(file => {
          this.file = file;
          this.icon = file.icon;
          this.displayEditor = false;
          this.showNoDescription = !this.file.description && !this.displayEditor;
          this.showDescription = this.file.description && this.file.description.length && !this.displayEditor;
          this.fileInitialDescription = this.file.description;    
          this.isFavorite = this.file && this.file.metadatas && this.file.metadatas.favorites && this.file.metadatas.favorites.length;  
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
      const url = this.$documentsUtils.getParentFolderUrl(this.file);
      window.open(url,'_self');
    },
    openPreview() {
      this.loading = true;
      this.close();
      if (this.isFileEditable)  {
        if (this.file?.acl?.canEdit){
          this.openFileInEditor();
        } else {
          this.openFileInEditor('view');
        }
      } else if (this.isFileReadable)  {
        this.openFileInEditor('view');
      } else {
        const attachments = [];
        attachments.push({
          id: this.file.id,
          downloadUrl: this.downloadUrl,
          name: this.file.name,
          filename: this.file.name,
          mimetype: this.file.mimeType,
          icon: this.icon,
          editable: this.isFileEditable,
          readable: this.isFileReadable,
          path: this.file.path,
          source: 'documents'
        });
        document.dispatchEvent(new CustomEvent('open-attachments-preview', {detail: {'attachments': attachments,'id': this.file.id }}));
      }
      document.dispatchEvent(new CustomEvent('mark-attachment-as-viewed', {detail: {file: this.file}}));
      this.loading = false;
    },
    openFileInEditor(mode) {
      if (this.file && this.file.id) {
        const url = this.$documentsUtils.getEditorUrl(this.file,mode);
        window.open(url,'_self');
      }
    },
    favoriteRemoved() {
      this.isFavorite = !this.isFavorite;
      this.displayAlert(this.$t('Favorite.tooltip.SuccessfullyDeletedFavorite', {0: this.$t('file.label')}));
    },
    removeFavoriteError() {
      this.displayAlert(this.$t('Favorite.tooltip.ErrorDeletingFavorite', {0: this.$t('file.label')}), 'error');
    },
    favoriteAdded() {
      this.isFavorite = !this.isFavorite;
      this.displayAlert(this.$t('Favorite.tooltip.SuccessfullyAddedAsFavorite', {0: this.$t('file.label')}));
    },
    addFavoriteError() {
      this.displayAlert(this.$t('Favorite.tooltip.ErrorAddingAsFavorite', {0: this.$t('file.label')}), 'error');
    },
    copyLink() {
      this.loading = true;
      let path;
      if (this.isFileEditable)  {
        if (this.file?.acl?.canEdit){
          path =  `${window.location.host}${this.$documentsUtils.getEditorUrl(this.file,null)}`;
        } else {
          path =  `${window.location.host}${this.$documentsUtils.getEditorUrl(this.file,'view')}`;
        }
      } else if (this.isFileReadable)  {
        path =  `${window.location.host}${this.$documentsUtils.getEditorUrl(this.file,'view')}`;
      } else {
        path = `${window.location.host}${this.$documentsUtils.getParentFolderUrl(this.file)}?documentPreviewId=${this.file.id}`;
      }
      const input = document.createElement('input');
      input.value = path;
      document.body.appendChild(input);
      input.select();
      document.execCommand('copy');
      document.body.removeChild(input);
      this.loading = false;
    }
  }
};
</script>
