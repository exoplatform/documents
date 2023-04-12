/*
 * Copyright (C) 2023 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <gnu.org/licenses>.
 */

<template>
  <exo-drawer 
    ref="documentDownloadDrawer"
    class="documentDownloadDrawer"
    :confirm-close="downloading"
    :confirm-close-labels="confirmCloseLabels"
    @closed="close"
    right>
    <template slot="title">
      {{ $t('documents.drawer.download.title') }}
    </template>
    <template slot="content">
      <v-card flat class="pa-2 mt-10">            
        <v-list>
          <v-list-item>
            <template>
              <v-list-item-action class="mr-3">
                <v-icon
                  color="success"
                  size="18"
                  v-if="started">
                  fa-check
                </v-icon>
                <v-progress-circular
                  v-else
                  color="primary"
                  indeterminate
                  size="18" />
              </v-list-item-action>
              <v-list-item-content>
                <v-list-item-title>{{ $t('documents.bulk.download.status.label.started') }}</v-list-item-title>
              </v-list-item-content>
            </template>
          </v-list-item>      
          <v-list-item>
            <template>
              <v-list-item-action class="mr-3">
                <v-icon
                  color="success"
                  size="18"
                  v-if="zipFileCreated">
                  fa-check
                </v-icon>
                <v-progress-circular
                  v-if="!zipFileCreated && started"
                  color="primary"
                  indeterminate
                  size="18" />
              </v-list-item-action>
              <v-list-item-content>
                <v-list-item-title v-if="zipFileCreated">{{ $t('documents.bulk.download.status.zipFileCreated') }}</v-list-item-title>
                <v-list-item-title v-else>{{ $t('documents.bulk.download.status.zipFileCreation') }}</v-list-item-title>
                
              </v-list-item-content>
            </template>
          </v-list-item>       
          <v-list-item>
            <template>
              <v-list-item-action class="mr-3">
                <v-icon
                  color="success"
                  size="18"
                  v-if="doneSuccsussfully">
                  fa-check
                </v-icon>
                <v-progress-circular
                  v-if="!doneSuccsussfully && zipFileCreated && started"
                  color="primary"
                  indeterminate
                  size="18" />
              </v-list-item-action>
              <v-list-item-content>
                <v-list-item-title>{{ $t('documents.bulk.download.doneSuccessfully') }}</v-list-item-title>
              </v-list-item-content>
            </template>
          </v-list-item>          
        </v-list>
      </v-card>
    </template>
    <template slot="footer">
      <div class="d-flex">
        <v-spacer />
        <v-btn
          @click="close"
          class="btn ml-2">
          {{ $t('documents.button.cancel') }}
        </v-btn>
      </div>
    </template>
  </exo-drawer>
</template>
<script>

export default {

  data: () => ({
   
    downloadStatus: 'started',
    started: true,
    zipFileCreated: false,
    doneSuccsussfully: false,
    actionId: 0,
    downloading: true,
    cancelling: true,
  }),

  computed: {
    confirmCloseLabels() {
      return {
        title: this.$t('documents.bulk.download.confirmCancel.title'),
        message: this.$t('documents.bulk.download.confirmCancel.message'),
        ok: this.$t('documents.button.yes'),
        cancel: this.$t('documents.button.no'),
      };
    },
  },

  created() {
    
    this.$root.$on('open-download-drawer', (actionId) => {
      this.actionId=actionId;
      this.open();
    });
    this.$root.$on('close-download-drawer', this.close);
    this.$root.$on('set-download-status', (downloadStatus) => {
      this.downloadStatus=downloadStatus.toLowerCase();
      if (this.downloadStatus==='zip_file_creation'){
        this.started = true;
      }
      if (this.downloadStatus==='zip_file_created'){
        this.zipFileCreated = true;
      }
      if (this.downloadStatus==='done_succsussfully'){
        this.doneSuccsussfully =  true;
        this.downloading=false;
        window.setTimeout(() => {
          this.close();
        }, 500);
      }
    });
  },
  methods: {
    open() {
      this.downloading=true;
      this.started = true;
      this.zipFileCreated = false;
      this.doneSuccsussfully = false;
      this.$refs.documentDownloadDrawer.open();
    },

    close() {
      if (this.downloading){
        this.cancelling=true;
        this.$root.$emit('cancel-bulk-Action',this.actionId);
      }
      this.$refs.documentDownloadDrawer.close();
    },
  }
};
</script>
