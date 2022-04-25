<template>
  <exo-drawer 
    ref="documentsMoveDrawer"
    class="documentsMoveDrawer"
    @closed="close"
    right>
    <template slot="title">
      {{ moveTitle }}
    </template>
    <template slot="content">
      <v-layout column>
        <v-list-item>
          <div class="d-flex align-center">
            <div class="pr-4 d-flex">
              <span class="font-weight-bold text-color">{{ $t('documents.move.drawer.space') }}</span>
              <div class="">
                <documents-move-spaces
                  :space="space" />
              </div>
            </div>
          </div>
        </v-list-item>
        <v-list-item>
          <div class="py-2 width-full">
            <span class="font-weight-bold text-color  pb-2">{{ $t('documents.move.drawer.currentPosition') }}</span>
            <documents-breadcrumb
              :show-icon="false"
              move />
          </div>
        </v-list-item>
        <v-list-item>
          <div class="py-2  width-full">
            <span class="font-weight-bold text-color pb-2">{{ $t('documents.move.drawer.destination') }}</span>
            <documents-breadcrumb
              :show-icon="false"
              :documents-breadcrumb="documentsBreadcrumbDestination"
              move />
          </div>
        </v-list-item>
        <v-list-item class="position-title">
          <div class="py-2">
            <span class="font-weight-bold text-color">{{ $t('documents.move.drawer.position') }}</span>
          </div>
        </v-list-item>
      </v-layout>
      <template>
        <span class="text-color body-2 px-4">
          {{ $t('documents.move.drawer.folder') }}
        </span>
        <v-treeview
          :open.sync="openLevel"
          :items="items"
          class="treeView-item my-2"
          item-key="name"
          hoverable
          activatable
          transition>
          <template #label="{ item }">
            <div class="d-flex clickable" @click="getDestination(item)">
              <v-icon size="24" class="primary--text">
                {{ 'fas fa-folder' }}
              </v-icon>
              <v-list-item-title class="body-2 mx-2 mt-1">
                {{ item.name }}
              </v-list-item-title>
            </div>
          </template>
        </v-treeview>
      </template>
    </template>
    <template slot="footer">
      <div class="d-flex">
        <v-spacer />
        <v-btn
          @click="close"
          class="btn ml-2">
          {{ $t('documents.move.drawer.button.cancel') }}
        </v-btn>
        <v-btn
          :disabled="saving"
          @click="moveDocument()"
          class="btn btn-primary ml-2">
          {{ $t('documents.move.drawer.button.move') }}
        </v-btn>
      </div>
    </template>
  </exo-drawer>
</template>
<script>

export default {
  data: () => ({
    ownerId: eXo.env.portal.spaceIdentityId || eXo.env.portal.userIdentityId,
    items: [],
    documentsBreadcrumbDestination: [],
    destinationFolderPath: '',
    currentFolderPath: '',
    spaceDisplayName: eXo.env.portal.spaceDisplayName,
    spaceName: eXo.env.portal.spaceName,
    userName: eXo.env.portal.userName,
    fileName: '',
    space: []
  }),
  computed: {
    openLevel() {
      return this.items && this.items.length && [this.items[0].name];
    },
    saving() {
      return !this.space || this.space.displayName === this.spaceDisplayName && this.destinationFolderPath && this.destinationFolderPath === this.currentFolderPath;
    },
    moveTitle(){
      return this.$t('documents.move.drawer.title').replace('{0}', this.fileName);
    },
  },
  created() {
    this.$root.$on('set-current-folder', data => {
      this.currentFolderPath = data && data.path;
    });
    this.$root.$on('current-space',data => {
      const ownerId = data ? data.identity.id : null;
      this.items = [];
      this.space = data;
      this.documentsBreadcrumbDestination = [{
        name: 'Documents'
      }];
      this.retrieveNoteTree(ownerId);
    });
  },
  methods: {
    open(file, fileName) {
      this.file = file;
      this.fileName = fileName;
      const  ownerId = eXo.env.portal.spaceIdentityId || eXo.env.portal.userIdentityId;
      this.retrieveNoteTree(ownerId);
      this.space = {
        displayName: this.spaceDisplayName ? this.spaceDisplayName : this.userName ,
        avatarUrl: this.spaceDisplayName ? `${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/social/spaces/${this.spaceName}/avatar` :
          `${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/social/users/${this.userName}/avatar`,
      };
      this.$refs.documentsMoveDrawer.open();
    },
    close() {
      this.$refs.documentsMoveDrawer.close();
    },
    getDestination(folder) {
      this.folder=folder;
      this.folderPath='';
      this.destinationFolderId=folder.id;
      return this.$documentFileService
        .getBreadCrumbs(this.destinationFolderId,this.ownerId,this.folderPath)
        .then(breadCrumbs => {this.documentsBreadcrumbDestination = breadCrumbs;
          this.destinationFolderId = this.documentsBreadcrumbDestination[this.documentsBreadcrumbDestination.length-1].id;
          this.destinationFolderPath = this.documentsBreadcrumbDestination[this.documentsBreadcrumbDestination.length-1].path;
        })
        .finally(() => this.loading = false);
    },
    retrieveNoteTree(ownerId){
      this.$documentFileService
        .getFullTreeData(ownerId).then(data => {
          if (data) {
            this.items = [];
            this.items = data;
          }
        });
    },
    moveDocument(){
      this.$root.$emit('documents-move', this.ownerId, this.file.id, this.folder.path);
      this.close();
    },
  }
};
</script>
