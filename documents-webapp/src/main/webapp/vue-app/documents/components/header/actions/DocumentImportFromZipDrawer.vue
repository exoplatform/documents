<!--
* Copyright (C) 2023 eXo Platform SAS
*
*  This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU Affero General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU Affero General Public License
* along with this program.  If not, see <gnu.org/licenses>.
-->

<template>
  <div>
    <exo-drawer
      ref="documentsUploadZipDrawer"
      class="documentsUploadZipDrawer"
      :confirm-close="uploading"
      :confirm-close-labels="confirmCloseLabels"
      @closed="close"
      show-overlay
      right>
      <template slot="title">
        {{ $t('documents.drawer.upload.zip.title') }}
      </template>
      <template slot="content">
        <div v-if="!importing">
          <template>
            <div class="d-flex align-center px-4 py-2">
              <v-subheader class="text-header-title pl-0 d-flex">
                {{ $t('documents.label.upload.zip.choice') }}
              </v-subheader>
            </div>
            <div class="caption font-weight-light grey--text px-4 mt-n5">
              {{ $t('documents.label.zip.attachments.upload.description') }}
            </div>
            <documents-zip-upload-input
              v-if="value.length === 0"
              :attachments="value" />

            <documents-zip-uploaded
              v-else
              :attachments="value" />
                  
            <div class="d-flex align-center px-4 py-2">
              <v-subheader class="text-header-title pl-0 d-flex">
                {{ $t('documents.label.upload.zip.rules') }}
              </v-subheader>
            </div>
            <div class="caption font-weight-light grey--text px-4 mt-n5">
              {{ $t('documents.label.upload.zip.rules.description') }}
            </div>
            <div class="radio-group-container ps-3">
              <v-radio-group
                v-model="selected">
                <v-radio
                  :label="$t('documents.label.upload.zip.rules.duplicate')"
                  value="duplicate" />
                <v-radio
                  :label="$t('documents.label.upload.zip.rules.ignore')"
                  value="ignore" />
              </v-radio-group>
            </div>
          </template>
        </div>
        <div v-else>
          <v-row
            class="ma-0"
            align-content="center"
            justify="center">
            <v-col
              v-if="status!==''"
              class="text-subtitle-1 text-center"
              :class="status==='cannot_unzip_file'||status==='failed'?'error--text':''"
              cols="12">
              {{ $t(`documents.import.status.${status}`) }}
            </v-col>
            <v-col cols="9" v-if="status==='unzipping'">
              <v-progress-linear
                indeterminate
                rounded
                height="20"
                class="disable-pointer-event" />
            </v-col>
            <v-col cols="11" v-else>
              <v-progress-linear
                v-if="status!=='cannot_unzip_file'"
                v-model="progress"
                rounded
                height="20"
                class="disable-pointer-event">
                <strong v-if="status!=='cannot_unzip_file'">{{ Math.ceil(progress) }}%</strong>
              </v-progress-linear>
            </v-col>
            <v-col cols="11" v-if="status==='creating_documents'">
              {{ importData.importedFilesCount }}/{{ totalNumber }}: {{ importData.documentInProgress }}
            </v-col>
            <v-col cols="12" v-if="status==='done_successfully' ||status==='done_with_errors' || status==='failed'">
              <v-list-item>
                <v-list-item-content>
                  <v-list-item-title>
                    <div class="d-flex flex-nowrap pa-1">
                      <v-icon
                        v-if="showCreatedFiles"
                        color="grey"
                        size="16"
                        class="fas fa-chevron-up pa-1 chevron-icon" 
                        @click="showCreatedFiles=!showCreatedFiles" />
                      <v-icon
                        v-else
                        color="grey"
                        size="16"
                        class="fas fa-chevron-down pa-1 chevron-icon"
                        @click="showCreatedFiles=!showCreatedFiles" />
                      <span class="px-2 my-auto">{{ importData.createdFiles.length }} {{ $t('documents.label.upload.zip.more.created') }}</span>
                      <v-divider class="my-auto" />
                    </div>
                  </v-list-item-title>
                  <v-list-item-subtitle
                    v-if="showCreatedFiles"
                    v-sanitized-html="importData.createdFiles.join('<br>')"
                    class="ps-6" />
                </v-list-item-content>
              </v-list-item> 
              <v-list-item>
                <v-list-item-content>
                  <v-list-item-title>
                    <div class="d-flex flex-nowrap pa-1">
                      <v-icon
                        v-if="showIgnoredFiles"
                        color="grey"
                        size="16"
                        class="fas fa-chevron-up pa-1 chevron-icon" 
                        @click="showIgnoredFiles=!showIgnoredFiles" />
                      <v-icon
                        v-else
                        color="grey"
                        size="16"
                        class="fas fa-chevron-down pa-1 chevron-icon"
                        @click="showIgnoredFiles=!showIgnoredFiles" />
                      <span class="px-2 my-auto">{{ importData.ignoredFiles.length }} {{ $t('documents.label.upload.zip.more.ignored') }}</span>
                      <v-divider class="my-auto" />
                    </div>
                  </v-list-item-title>
                  <v-list-item-subtitle
                    v-if="showIgnoredFiles"
                    v-sanitized-html="importData.ignoredFiles.join('<br>')"
                    class="ps-6" />
                </v-list-item-content>
              </v-list-item>  
              <v-list-item>
                <v-list-item-content>
                  <v-list-item-title>
                    <div class="d-flex flex-nowrap pa-1">
                      <v-icon
                        v-if="showDuplicatedFiles"
                        color="grey"
                        size="16"
                        class="fas fa-chevron-up pa-1 chevron-icon" 
                        @click="showDuplicatedFiles=!showDuplicatedFiles" />
                      <v-icon
                        v-else
                        color="grey"
                        size="16"
                        class="fas fa-chevron-down pa-1 chevron-icon"
                        @click="showDuplicatedFiles=!showDuplicatedFiles" />
                      <span class="px-2 my-auto">{{ importData.duplicatedFiles.length }} {{ $t('documents.label.upload.zip.more.duplicated') }}</span>
                      <v-divider class="my-auto" />
                    </div>
                  </v-list-item-title>
                  <v-list-item-subtitle
                    v-if="showDuplicatedFiles"
                    v-sanitized-html="importData.duplicatedFiles.join('<br>')"
                    class="ps-6" />
                </v-list-item-content>
              </v-list-item> 
              <v-list-item>
                <v-list-item-content>
                  <v-list-item-title>
                    <div class="d-flex flex-nowrap pa-1">
                      <v-icon
                        v-if="showFailedFiles"
                        color="grey"
                        size="16"
                        class="fas fa-chevron-up pa-1 chevron-icon" 
                        @click="showFailedFiles=!showFailedFiles" />
                      <v-icon
                        v-else
                        color="grey"
                        size="16"
                        class="fas fa-chevron-down pa-1 chevron-icon"
                        @click="showFailedFiles=!showFailedFiles" />
                      <span class="px-2 my-auto">{{ importData.failedFiles.length }} {{ $t('documents.label.upload.zip.more.failed') }}</span>
                      <v-divider class="my-auto" />
                    </div>
                  </v-list-item-title>
                  <v-list-item-subtitle
                    v-if="showFailedFiles"
                    v-sanitized-html="importData.failedFiles.join('<br>')"
                    class="ps-6" />
                </v-list-item-content>
              </v-list-item>
            </v-col>
          </v-row>
        </div>
      </template>
      <template slot="footer">
        <div class="d-flex">
          <v-spacer />
          <v-btn
            class="btn me-2"
            @click="cancel">
            <template>
              {{ $t('documents.label.button.close') }}
            </template>
          </v-btn>
          <v-btn
            v-show="status!=='done_successfully' && status!=='done_with_errors' && status!=='failed' && !importing"
            :disabled="uploadButtonDisabled"
            class="btn btn-primary"
            @click="uploadDocuments">
            <template>
              {{ $t('documents.label.zip.upload') }}
            </template>
          </v-btn>
        </div>
      </template>
    </exo-drawer>
  </div>
</template>
<script>
export default {
  data() {
    return {
      stepper: 1,
      selected: 'ignore',
      value: [],
      showImportOptionsList: false,
      folderPath: '',
      importing: false,
      ownerId: eXo.env.portal.spaceIdentityId || eXo.env.portal.userIdentityId,
      settings: {},
      status: '',
      totalNumber: 0,
      progress: 0,
      importData: {},
      showCreatedFiles: false, 
      showIgnoredFiles: false, 
      showDuplicatedFiles: false, 
      showFailedFiles: false, 
      folderId: '', 
    };
  },
  computed: {
    uploadButtonDisabled(){
      return !(this.selected!==''  &&  this.value && this.value[0] && this.value[0].uploadId && this.value[0].uploadProgress === 100 && !this.importing);
    },
    uploading(){
      return (this.value && this.value[0] && this.value[0].uploadId && !this.importing);
    },
    enableOptionList() {
      return this.showImportOptionsList;
    },
    confirmCloseLabels() {
      return {
        title: this.$t('documents.import.confirmCancel.title'),
        message: this.$t('documents.import.confirmCancel.message'),
        ok: this.$t('documents.button.yes'),
        cancel: this.$t('documents.button.no'),
      };
    },
  },
  created() {
    this.$root.$on('open-upload-zip-drawer', () => {
      this.open();
    });
    this.$root.$on('set-import-status', (actionData) => {
      this.handleProgress(actionData);
    });
    this.$root.$on('set-progress', (progress) => {
      this.progress=progress;
    });
    this.$root.$on('set-total-number', (totalNumber) => {
      this.totalNumber=totalNumber;
    });
    this.$root.$on('delete-uploaded-file', () => {
      this.value = [];
    });
  },
  methods: {
    open() {
      this.getDocumentDataFromUrl();
      this.$refs.documentsUploadZipDrawer.open();
    },
    close() {
      if (this.uploading || this.status==='done_successfully' || this.status==='done_with_errors' || this.status==='failed' || this.status==='cannot_unzip_file'){
        this.value = [];
        this.selected = 'ignore';
        this.stepper = 1;
        this.importing=false;
        this.status= '';
        this.importData.documentInProgress= '';
        this.totalNumber= 0;
        this.progress= 0;
        this.importData= {};
        this.showCreatedFiles= false;
        this.showIgnoredFiles= false; 
        this.showDuplicatedFiles= false; 
        this.showFailedFiles= false; 
        this.$root.$emit('set-action-loading', false,'import');
      }
      this.$refs.documentsUploadZipDrawer.close();
    },

    cancel() {
      this.$refs.documentsUploadZipDrawer.close();
    },

    getDocumentDataFromUrl() {
      const currentUrlSearchParams = window.location.search;
      const queryParams = new URLSearchParams(currentUrlSearchParams);
      const path = window.location.pathname;
      const pathParts  = path.split( `${eXo.env.portal.selectedNodeUri.toLowerCase()}/`);
      if (pathParts.length > 1) {
        this.folderPath = pathParts[1];
      }
      if (queryParams.has('folderId')) {
        this.folderId = queryParams.get('folderId');

      } else {
        if (queryParams.has('view')) {
          const view = queryParams.get('view');
          if (view.toLowerCase() !== 'folder'){
            this.folderPath = '';
          }
        }
        if (this.selectedView === 'folder') {
          this.getFolderPath(this.folderPath);
        }
      }
      return this.$nextTick();
    },
    getFolderPath(path){
      if (!path){
        path = window.location.pathname;
      }
      if (eXo.env.portal.spaceName){
        if (path.includes('/documents/')){
          this.folderPath = path.substring(path.indexOf('/documents/') + '/documents/'.length);
          this.selectedView = 'folder';
        }
      } else if (path.includes('Private/')) {
        this.folderPath = path.substring(path.indexOf('Private/') + 'Private/'.length);
        this.selectedView = 'folder';
      }
    },

    uploadDocuments(){
      this.importing=true;
      this.uploadId=this.value[0].uploadId;
      this.$root.$emit('documents-zip-import', this.uploadId,this.selected);
    },

    handleProgress(importData) {
      if (this.uploadId===parseInt(importData.actionId)){
        this.importData=importData;
        this.status=importData.status.toLowerCase();
        if (importData.files && importData.files.length>0){
          this.totalNumber=importData.files.length;
          this.progress= (importData.importedFilesCount*100)/this.totalNumber;
          this.$root.$emit('set-progress', this.progress);
        }
        if (this.status==='cannot_unzip_file'){
          this.$root.$emit('show-alert', {type: 'error', message: this.$t('documents.import.cannotUnzipFile')});
        }
        if (this.status==='failed'){
          this.$root.$emit('show-alert', {type: 'error', message: this.$t('documents.import.message.failed')});
        }
        if (this.status==='done_with_errors'){
          this.$root.$emit('documents-refresh-files');
          this.$root.$emit('show-alert', {type: 'warning', message: this.$t('documents.import.message.doneWithErrors')});
        }
        if (this.status==='done_successfully'){
          this.$root.$emit('documents-refresh-files');
          this.$root.$emit('show-alert', {type: 'success', message: this.$t('documents.import.message.doneSuccessfully', {0: importData.importedFilesCount})});
        }
      }
    },
  }
};
</script>