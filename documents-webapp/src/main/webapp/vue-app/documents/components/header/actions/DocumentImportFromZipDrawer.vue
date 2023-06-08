<template>
  <div>
    <exo-drawer
      ref="documentsUploadZipDrawer"
      class="documentsUploadZipDrawer"
      @closed="close"
      show-overlay
      right>
      <template slot="title">
        {{ $t('documents.drawer.upload.zip.title') }}
      </template>
      <template slot="content">
        <div v-if="!importing">
          <template>
            <v-stepper
              v-model="stepper"
              vertical
              flat
              class="ma-0 me-4"> 
              <v-stepper-step
                :complete="stepper > 1"
                step="1">
                {{ $t('documents.label.upload.zip.choice') }}
                <span v-if=" stepper!==1 && value " class="text-light-color caption">{{ $t('documents.label.zip.choice.sub.title') }}</span>
              </v-stepper-step>

              <v-stepper-content step="1">
                <documents-zip-upload-input
                  :attachments="value" />

                <documents-zip-uploaded
                  :attachments="value" />

                <v-card-actions class="px-0">
                  <v-spacer />
                  <v-btn
                    :disabled="continueButtonDisabled"
                    class="btn btn-primary"
                    outlined
                    @click="stepper = 2">
                    {{ $t('documents.label.button.continue') }}
                    <v-icon size="18" class="ms-2">
                      {{ $vuetify.rtl && 'fa-caret-left' || 'fa-caret-right' }}
                    </v-icon>
                  </v-btn>
                </v-card-actions>
              </v-stepper-content>

              <v-stepper-step
                :complete="stepper > 2"
                step="2">
                {{ $t('documents.label.upload.zip.rules') }}
              </v-stepper-step>

              <v-stepper-content step="2" class="py-0 ps-0">
                <div class="radio-group-container ps-4">
                  <v-radio-group
                    v-model="selected">
                    <v-radio
                      :label="$t('documents.label.upload.zip.rules.update')"
                      value="updateAll" />
                    <v-radio
                      :label="$t('documents.label.upload.zip.rules.duplicate')"
                      value="duplicate" />
                    <v-radio
                      :label="$t('documents.label.upload.zip.rules.ignore')"
                      value="ignore" />
                  </v-radio-group>
                </div>

                <v-card-actions class="mt-4 px-0">
                  <v-btn
                    class="btn"
                    @click="stepper = 1">
                    <v-icon size="18" class="me-2">
                      {{ $vuetify.rtl && 'fa-caret-right' || 'fa-caret-left' }}
                    </v-icon>
                    {{ $t('documents.label.button.back') }}
                  </v-btn>
                </v-card-actions>
              </v-stepper-content>
            </v-stepper>
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
                height="20" />
            </v-col>
            <v-col cols="11" v-else>
              <v-progress-linear
                v-model="progress"
                rounded
                height="20">
                <strong v-if="status!=='cannot_unzip_file'">{{ Math.ceil(progress) }}%</strong>
              </v-progress-linear>
            </v-col>
            <v-col cols="11" v-if="status==='creating_documents'">
              {{ importData.importedFilesCount }}/{{ totalNumber }}: {{ importData.documentInProgress }}
            </v-col>
            <v-col cols="12" v-if="status==='done_successfully' || status==='failed'">
              <v-list-item>
                <v-list-item-content>
                  <v-list-item-title>
                    <span>{{ importData.createdFiles.length }} {{ $t('documents.label.upload.zip.more.created') }}</span>
                  </v-list-item-title>
                  <v-list-item-subtitle v-if="showCreatedFiles" v-sanitized-html="importData.createdFiles.join('<br>')" />
                </v-list-item-content>
                <v-list-item-action v-if="importData.createdFiles.length>0">
                  <v-tooltip bottom>
                    <template #activator="{ on, attrs }">
                      <v-btn
                        icon 
                        v-bind="attrs"
                        v-on="on" 
                        @click="showCreatedFiles=!showCreatedFiles">
                        <v-icon color="grey lighten-1">mdi-information</v-icon>
                      </v-btn>
                    </template>
                    <span>
                      {{ $t('documents.import.show.details') }}
                    </span>
                  </v-tooltip>
                </v-list-item-action>
              </v-list-item> 
              <v-list-item>
                <v-list-item-content>
                  <v-list-item-title>
                    <span>{{ importData.ignoredFiles.length }} {{ $t('documents.label.upload.zip.more.ignored') }}</span>
                  </v-list-item-title>
                  <v-list-item-subtitle v-if="showIgnoredFiles" v-sanitized-html="importData.ignoredFiles.join('<br>')" />
                </v-list-item-content>
                <v-list-item-action v-if="importData.ignoredFiles.length>0">
                  <v-tooltip bottom>
                    <template #activator="{ on, attrs }">
                      <v-btn
                        icon 
                        v-bind="attrs"
                        v-on="on" 
                        @click="showIgnoredFiles=!showIgnoredFiles">
                        <v-icon color="grey lighten-1">mdi-information</v-icon>
                      </v-btn>
                    </template>
                    <span>
                      {{ $t('documents.import.show.details') }}
                    </span>
                  </v-tooltip>
                </v-list-item-action>
              </v-list-item>
              <v-list-item>
                <v-list-item-content>
                  <v-list-item-title>
                    <span>{{ importData.duplicatedFiles.length }} {{ $t('documents.label.upload.zip.more.duplicated') }}</span>
                  </v-list-item-title>
                  <v-list-item-subtitle v-if="showDuplicatedFiles" v-sanitized-html="importData.duplicatedFiles.join('<br>')" />
                </v-list-item-content>
                <v-list-item-action v-if="importData.duplicatedFiles.length>0">
                  <v-tooltip bottom>
                    <template #activator="{ on, attrs }">
                      <v-btn
                        icon 
                        v-bind="attrs"
                        v-on="on" 
                        @click="showDuplicatedFiles=!showDuplicatedFiles">
                        <v-icon color="grey lighten-1">mdi-information</v-icon>
                      </v-btn>
                    </template>
                    <span>
                      {{ $t('documents.import.show.details') }}
                    </span>
                  </v-tooltip>
                </v-list-item-action>
              </v-list-item>
              <v-list-item>
                <v-list-item-content>
                  <v-list-item-title>
                    <span>{{ importData.updatedFiles.length }} {{ $t('documents.label.upload.zip.more.updated') }}</span>
                  </v-list-item-title>
                  <v-list-item-subtitle v-if="showUpdatedFiles" v-sanitized-html="importData.updatedFiles.join('<br>')" />
                </v-list-item-content>
                <v-list-item-action v-if="importData.updatedFiles.length>0">
                  <v-tooltip bottom>
                    <template #activator="{ on, attrs }">
                      <v-btn
                        icon 
                        v-bind="attrs"
                        v-on="on" 
                        @click="showUpdatedFiles=!showUpdatedFiles">
                        <v-icon color="grey lighten-1">mdi-information</v-icon>
                      </v-btn>
                    </template>
                    <span>
                      {{ $t('documents.import.show.details') }}
                    </span>
                  </v-tooltip>
                </v-list-item-action>
              </v-list-item>
              <v-list-item>
                <v-list-item-content>
                  <v-list-item-title>
                    <span>{{ importData.failedFiles.length }} {{ $t('documents.label.upload.zip.more.failed') }}</span>
                  </v-list-item-title>
                  <v-list-item-subtitle v-if="showFailedFiles" v-sanitized-html="importData.failedFiles.join('<br>')" />
                </v-list-item-content>
                <v-list-item-action v-if="importData.failedFiles.length>0">
                  <v-tooltip bottom>
                    <template #activator="{ on, attrs }">
                      <v-btn
                        icon 
                        v-bind="attrs"
                        v-on="on" 
                        @click="showFailedFiles=!showFailedFiles">
                        <v-icon color="grey lighten-1">mdi-information</v-icon>
                      </v-btn>
                    </template>
                    <span>
                      {{ $t('documents.import.show.details') }}
                    </span>
                  </v-tooltip>
                </v-list-item-action>
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
            @click="close">
            <template>
              {{ $t('documents.label.button.close') }}
            </template>
          </v-btn>
          <v-btn
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
      showUpdatedFiles: false,
      showFailedFiles: false, 
      folderId: '', 
    };
  },
  computed: {
    continueButtonDisabled(){
      if (this.value && this.value[0] && this.value[0].uploadId){
        return false;
      } else {
        return true;
      }
    },
    uploadButtonDisabled(){
      if (this.selected!=='' && this.stepper===2 && this.value && this.value[0] && this.value[0].uploadId && !this.importing)
      {
        return false;
      } else {
        return true;
      }
    },
    enableOptionList() {
      return this.showImportOptionsList;
    }
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
  },
  methods: {
    open() {
      this.getDocumentDataFromUrl();
      this.$refs.documentsUploadZipDrawer.open();
    },
    close() {
      if (this.status==='done_successfully' || this.status==='failed' || this.status==='cannot_unzip_file'){
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
        this.showUpdatedFiles= false;
        this.showFailedFiles= false; 
        this.$root.$emit('set-action-loading', false,'import');
      }
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
          this.$root.$emit('show-alert', {type: 'error', message: this.$t('documents.import.doneWithErrors')});
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