<template>
  <exo-drawer 
    ref="documentInfoDrawer"
    v-model="drawer"
    class="documentInfoDrawer"
    @closed="close"
    right>
    <template #title>
      {{ $t('documents.drawer.details.title') }}
    </template>
    <template v-if="drawer && file" #titleIcons>
      <extension-registry-components
        :params="params"
        name="DocumentsInfo"
        type="documents-info-header"
        parent-element="div"
        element="div" />
      <documents-favorite-button
        :id="fileId"
        :file="file"
        :small="false"
        @added="favoriteAdded"
        @removed="favoriteRemoved" />
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
        <span>{{ $t('documents.label.copy.link') }}</span>
      </v-tooltip>
      <v-tooltip
        v-if="!$downloadDocumentSuspended"
        bottom>
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
    <template v-if="drawer && file" #content>
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
            height="210px"
            max-height="210px"
            width="252px"
            max-width="100%"
            class="overflow-hidden d-flex flex-column  border-box-sizing mx-auto no-border-radius mb-3"
            @click="openPreview">
            <v-card-text
              class="d-flex flex-grow-1 pa-0">
              <img
                v-if="!showIcon"
                :src="getThumbnailUrl()"
                class="ma-auto"
                alt="No thumbnail"
                @load="loading = false"
                @error="handleError">
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
        <v-list-item dense two-line>
          <v-list-item-content class="pt-0 pb-2">
            <v-list-item-subtitle>{{ $t('documents.drawer.details.type') }}</v-list-item-subtitle>
            <v-list-item-title> {{ $t(`documents.label.type.${icon.type}`) }} </v-list-item-title>
          </v-list-item-content>
        </v-list-item>
        <v-list-item
          v-if="!file.folder"
          dense
          two-line>
          <v-list-item-content class="pt-0 pb-2">
            <v-list-item-subtitle>{{ $t('documents.drawer.details.size') }}</v-list-item-subtitle>
            <v-list-item-title class="text-body">
              {{ fileSize.value }} {{ $t(`document.size.label.unit.${fileSize.unit}`) }} ({{ fileWithVersionsSize.value }} {{ $t(`document.size.label.unit.${fileWithVersionsSize.unit}`) }} {{ $t('documents.drawer.details.sizeWithVersions') }})              
            </v-list-item-title>
          </v-list-item-content>
        </v-list-item>
        <v-list-item dense two-line>
          <v-list-item-content class="pt-0 pb-2">
            <v-list-item-subtitle>{{ $t('documents.drawer.details.created') }}</v-list-item-subtitle>
            <v-list-item-title class="d-flex flex-row">
              <date-format
                :value="fileCreated"
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
                class="text-truncate font-weight-bold mt-0"
                username-class />
              <span v-else class="primary--text not-clickable font-weight-bold mx-1">
                {{ infoDrawerCreatorLabel }}
              </span>
            </v-list-item-title>
          </v-list-item-content>
        </v-list-item>
        <v-list-item dense two-line>
          <v-list-item-content class="pt-0 pb-2">
            <v-list-item-subtitle>{{ $t('documents.drawer.details.modified') }}</v-list-item-subtitle>
            <v-list-item-title class="d-flex flex-row">
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
                class="text-truncate font-weight-bold mt-0"
                username-class />
              <span v-else class="primary--text font-weight-bold mx-1">
                {{ infoDrawerModifierLabel }}
              </span>
            </v-list-item-title>
          </v-list-item-content>
        </v-list-item>
        <v-list-item
          v-if="!file.folder"
          dense
          two-line>
          <v-list-item-content class="pt-0 pb-2">
            <v-list-item-subtitle>{{ $t('documents.details.view.label') }}</v-list-item-subtitle>
            <v-list-item-title>{{ $t('documents.details.views.label', {0: `${file.views}`}) }}</v-list-item-title>
          </v-list-item-content>
        </v-list-item>
        <v-list-item
          v-if="file.ownerIdentity"
          dense
          two-line>
          <v-list-item-content class="pt-0 pb-2">
            <v-list-item-subtitle>{{ $t('documents.details.drive.label') }}</v-list-item-subtitle>
            <div class="d-flex">
              <v-list-item-avatar 
                size="20"
                class="mx-0 my-1"
                :class="file.ownerIdentity.providerId === 'space' && 'spaceAvatar' || 'userAvatar'"
                tile>
                <v-avatar :size="20">
                  <img
                    :src="file.ownerIdentity.avatar"
                    alt=""
                    class="rounded"
                    width="20"
                    height="20">
                </v-avatar>
              </v-list-item-avatar>
              <v-list-item-title class="ms-3">
                <a :href="getDrivePath()" class="font-weight-bold">{{ file.ownerIdentity.providerId === 'space' && file.ownerIdentity.name || $t('documents.label.userHomeDocuments') }}</a>
              </v-list-item-title>
            </div>
          </v-list-item-content>
        </v-list-item>
        

        <v-list-item dense two-line>
          <v-list-item-content class="pt-1 pb-2">
            <v-list-item-subtitle>{{ $t('documents.drawer.details.location') }}</v-list-item-subtitle>
            <v-list-item-title>
              <breadcrumb :folder-id="file.parentFolderId" :is-mobile="isMobile" />
            </v-list-item-title>
          </v-list-item-content>
        </v-list-item>
        <v-list-item dense two-line>
          <v-list-item-content class="py-0 pt-1">
            <v-list-item-subtitle>{{ $t('documents.drawer.details.category') }}</v-list-item-subtitle>
            <v-list-item-title class="my-1">
              <document-categories v-if="file" :file="file" />
            </v-list-item-title>
          </v-list-item-content>
        </v-list-item>
        <v-list-item>
          <v-list-item-content class="py-1">
            <v-list-item-subtitle>
              {{ $t('documents.drawer.details.description') }}
            </v-list-item-subtitle>
          </v-list-item-content>
        </v-list-item>
        <v-list-item>
          <v-list-item-content class="pt-0 pb-4">
            <div v-if="showNoDescription" class="d-flex documentDescription">
              <v-icon size="20" class="descriptionIcon"> fas fa-file-alt </v-icon>
              <a
                v-if="file.acl.canEdit"
                class="font-weight-bold ps-4 clickable py-auto documentDescription"
                @click="openEditor">
                <span>{{ $t('documents.message.addYourDescription') }}</span>
              </a>
            </div>
            <v-hover>
              <div class="documentDescription" slot-scope="{ hover }">
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
                          size="20"
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
            <div v-if="displayEditor" class="documentDescription d-flex flex-row">
              <rich-editor
                id="descriptionMessageInput"
                ref="descriptionMessage"
                v-model="file.description"
                :max-length="1300"
                :placeholder="$t('documents.alert.descriptionLimit')"
                autofocus
                class="flex" />
            </div>
          </v-list-item-content>
        </v-list-item>
      </v-card>
    </template>   
    <template v-if="drawer" #footer>
      <div v-if="displayEditor" class="d-flex">
        <v-spacer />
        <v-btn
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
    drawer: false,
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
    fileId() {
      return this.file?.id;
    },
    modifiedDate() {
      return this.file?.modifiedDate;
    },
    spaceId() {
      return eXo.env.portal.spaceId || 0;
    },
    icon(){
      const fileIcon =  this.$documentsIconsExtension[0]?.get(this.file?.mimeType);
      return fileIcon ? fileIcon : this.file.folder ? this.$documentsIconsExtension[0]?.get('folder') : this.$documentsIconsExtension[0]?.get('file');
    },
    lastUpdated() {
      return this.file && (this.modifiedDate || this.file.createdDate) || '';
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
    isFileEditable() {
      return  this.$supportedDocuments && this.$supportedDocuments.filter(doc => doc.edit && doc.mimeType === this.file.mimeType ).length > 0;
    },
    isFileReadable() {
      return this.$supportedDocuments && this.$supportedDocuments.filter(doc => doc.mimeType === this.file.mimeType).length > 0;
    },
    downloadUrl() {
      return this.$documentsUtils.getDownloadUrl(this.fileId, this.modifiedDate);
    },
    fileSize() {
      return this.$documentsUtils.getSize(this.file.size);
    },
    fileWithVersionsSize() {
      return this.$documentsUtils.getSize(this.file.sizeWithVersions);
    },
    params() {
      return {
        file: this.file,
      };
    },
  },
  watch: {
    showDescription() {
      this.$refs.descriptionMessage?.initCKEditorData(this.file.description);
    },
    displayEditor() {
      this.$refs.descriptionMessage?.initCKEditorData(this.file.description);
    }
  },
  created() {
    this.$root.$on('open-folder', this.openFolder);
    document.addEventListener('open-info-drawer', this.open);
    document.addEventListener('document-views-updated', this.handleUpdateViews);
    document.addEventListener('search-metadata-tag', this.close);
    document.addEventListener('document-category-selected', this.close);
    document.addEventListener('click', (event) => {
      if (event.target.closest('.documentInfoDrawer') || event.target.closest('.documentDescription') || event.target.closest('.documentCategories')) {return;}
      this.close();
    });
  },
  methods: {
    handleError() {
      this.showIcon=true;
      this.loading = false;
    },
    getThumbnailUrl() {
      if (this.file.folder) {return null;}
      const file = this.file;
      file.readable = this.isFileReadable;
      const thumb = this.$documentsUtils.getThumbnailUrl(file,'250x250',this.lastUpdated);
      if (!thumb) {
        this.loading = false;
        this.showIcon = true;
      }
      return thumb;
    },
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
            this.$root.$emit('alert-message',  this.$t('documents.alert.success.description.updated'), 'success');
          }
          this.showDescription = this.file.description && this.file.description.length;
          this.showNoDescription = !this.file.description;
          this.displayEditor=false;
          this.fileInitialDescription = this.file.description;
          this.$refs.descriptionMessage?.initCKEditorData(this.file.description);
        }).catch(() => {
          this.$root.$emit('alert-message',  this.$t('documents.alert.error.description.updated'), 'error');
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
          this.showIcon = false;
          this.showNoDescription = !this.file.description && !this.displayEditor;
          this.showDescription = this.file.description && this.file.description.length && !this.displayEditor;
          this.fileInitialDescription = this.file.description;    
          this.isFavorite = this.file && this.file.metadatas && this.file.metadatas.favorites && this.file.metadatas.favorites.length; 
          if (file.folder){
            this.file.mimeType = 'folder';
            this.showIcon = true;
            this.loading = false;
          } 
          this.$root.$emit('update-breadcrumb', this.file.parentFolderId);
          this.$nextTick(()=>{
            this.$refs.documentInfoDrawer.open();
          });
        });
      
    },
    openEditor(){
      this.firstCreateDescription = this.showNoDescription;
      this.showNoDescription = false;
      this.showDescription = false;
      this.displayEditor=true;
      this.$nextTick(()=>{
        this.$refs.descriptionMessage?.initCKEditorData(this.file.description);
        document.getElementById('descriptionMessageInput').scrollIntoView();
      });
    },
    close() {
      this.file = null;
      this.displayEditor = false;
      this.showNoDescription = false;
      this.showDescription = true;
      this.showIcon = false;
      this.loading = true;
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
    openFolder(folder) {
      let folderPath = '';
      const pathParts = folder.path.split('/');
      const spaceIndex = pathParts.indexOf('spaces');
      if (spaceIndex !== -1){
        if (pathParts[spaceIndex+2]=== 'Documents'){
          pathParts[spaceIndex+2] = 'documents';
        }
        folderPath = pathParts.slice(spaceIndex + 1, pathParts.length ).join('/');
        folderPath = `${eXo.env.portal.context}/g/:spaces:${folderPath}`;          
      } else if (pathParts.indexOf('Users') !== -1){
        const parentIndex = pathParts.indexOf('Private');
        if (parentIndex!== -1){
          folderPath = pathParts.slice(parentIndex, pathParts.length ).join('/');
          folderPath = `${eXo.env.portal.context}/${eXo.env.portal.portalName}/drives/${folderPath}`;   
        }
      }
      window.open(folderPath,'_self');
    },
    openPreview() {
      if (this.file.folder) {
        this.openFolder(this.file);
        return;
      }
      this.loading = true;
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
          id: this.fileId,
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
        document.dispatchEvent(new CustomEvent('open-attachments-preview', {detail: {'attachments': attachments,'id': this.fileId }}));
      }
      document.dispatchEvent(new CustomEvent('mark-attachment-as-viewed', {detail: {file: this.file}}));
      this.close();
      this.loading = false;
    },
    openFileInEditor(mode) {
      if (this.file && this.fileId) {
        const url = this.$documentsUtils.getEditorUrl(this.file,mode);
        window.open(url,'_blank');
      }
    },
    favoriteRemoved() {
      this.isFavorite = false;
    },
    favoriteAdded() {
      this.isFavorite = true;
    },
    copyLink() {
      this.loading = true;
      let path;
      if (this.isFileEditable)  {
        if (this.file?.acl?.canEdit){
          path =  `${window.location.origin}${this.$documentsUtils.getEditorUrl(this.file,null)}`;
        } else {
          path =  `${window.location.origin}${this.$documentsUtils.getEditorUrl(this.file,'view')}`;
        }
      } else if (this.isFileReadable)  {
        path =  `${window.location.origin}${this.$documentsUtils.getEditorUrl(this.file,'view')}`;
      } else {
        path = `${window.location.origin}${this.$documentsUtils.getParentFolderUrl(this.file)}?documentPreviewId=${this.fileId}`;
      }
      const input = document.createElement('input');
      input.value = path;
      document.body.appendChild(input);
      input.select();
      document.execCommand('copy');
      document.body.removeChild(input);
      this.loading = false;
    },
    getDrivePath() {
      if (this.file?.ownerIdentity?.providerId === 'space') {
        const spaceId = this.file?.ownerIdentity?.avatar.split('social/spaces/')[1].split('/avatar')[0];
        return `${eXo.env.portal.context}/s/${spaceId}/documents`;
      } 
      return `${eXo.env.portal.context}/${eXo.env.portal.portalName}/documents`;
    },
  },
};
</script>
