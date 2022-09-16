<template>
  <exo-drawer
    v-model="drawer"
    ref="documentsTreeSelectorDrawer"
    class="documentsTreeSelectorDrawer"
    @closed="close"
    right>
    <template slot="title">
      <span :title="drawerTitle" class="text-truncate">{{ drawerTitle }}</span>
    </template>
    <template v-if="file && drawer" slot="content">
      <v-layout column class="mt-2">
        <v-list-item>
          <div class="d-flex align-center flex-grow-1">
            <div class="pr-4 d-flex flex-grow-1">
              <span class="font-weight-bold text-color text-no-wrap">{{ $t('documents.move.drawer.space') }}</span>
              <div class="flex-grow-1">
                <documents-move-spaces
                  :space="space" />
              </div>
            </div>
          </div>
        </v-list-item>
        <v-list-item>
          <div class="py-2 width-full">
            <span class="font-weight-bold text-color text-no-wrap pb-2">{{ $t('documents.move.drawer.currentPosition') }}</span>
            <documents-breadcrumb
              :show-icon="false"
              :documents-breadcrumb="documentsBreadcrumbSource"
              :disabled-icon-tree="true"
              move />
          </div>
        </v-list-item>
        <v-list-item>
          <div class="py-2  width-full">
            <span class="font-weight-bold text-color text-no-wrap pb-2">{{ $t('documents.move.drawer.destination') }}</span>
            <documents-breadcrumb
              :show-icon="false"
              :documents-breadcrumb="documentsBreadcrumbDestination"
              :disabled-icon-tree="true"
              move />
          </div>
        </v-list-item>
        <v-list-item class="position-title">
          <div class="py-2">
            <span class="font-weight-bold text-no-wrap text-color">{{ $t('documents.move.drawer.position') }}</span>
          </div>
        </v-list-item>
      </v-layout>
      <template>
        <span class="text-color body-2 text-no-wrap px-4">
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
          @click="close()"
          class="btn ml-2">
          {{ $t('documents.move.drawer.button.cancel') }}
        </v-btn>
        <v-btn
          :disabled="disableButton"
          @click="changeLocationDocument()"
          class="btn btn-primary ml-2">
          {{ submitButton }}
        </v-btn>
      </div>
    </template>
  </exo-drawer>
</template>
<script>

export default {
  data: () => ({
    ownerId: eXo.env.portal.spaceIdentityId || eXo.env.portal.userIdentityId,
    drawer: false,
    items: [],
    documentsBreadcrumbDestination: [],
    documentsBreadcrumbSource: [],
    destinationFolderPath: '',
    currentFolderPath: '',
    spaceDisplayName: eXo.env.portal.spaceDisplayName,
    spaceName: eXo.env.portal.spaceName,
    userName: eXo.env.portal.userName,
    groupId: '',
    space: [],
    file: {},
    actionType: ''
  }),
  computed: {
    openLevel() {
      return this.items && this.items.length && [this.items[0].name] || [];
    },
    disableButton() {
      return !this.space || this.space.displayName === this.spaceDisplayName && this.destinationFolderPath && this.destinationFolderPath === this.currentFolderPath;
    },
    drawerTitle() {
      return this.actionType === 'move' ? this.$t('documents.move.drawer.title', {0: this.file?.name}) : this.$t('documents.shortcut.drawer.title');
    },
    submitButton() {
      return this.actionType === 'move' ? this.$t('documents.move.drawer.button.move') : this.$t('documents.shortcut.drawer.button.create');
    }
  },
  created() {
    this.$root.$on('current-space',data => {
      const ownerId = data ? data.identity.id : null;
      this.items = [];
      this.space = data;
      this.groupId = data.groupId;
      this.documentsBreadcrumbDestination = [{
        name: 'Documents'
      }];
      this.retrieveNoteTree(ownerId);
    });
    this.$root.$on('open-document-tree-selector-drawer', (file, actionType) => {
      this.actionType = actionType;
      if (file) {
        this.open(file);
      }
    });
  },
  methods: {
    open(file) {
      this.file = file;
      const ownerId = eXo.env.portal.spaceIdentityId || eXo.env.portal.userIdentityId;
      this.retrieveNoteTree(ownerId);
      this.space = {
        displayName: this.spaceDisplayName ? this.spaceDisplayName : this.userName ,
        avatarUrl: this.spaceDisplayName ? `${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/social/spaces/${this.spaceName}/avatar` :
          `${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/social/users/${this.userName}/avatar`,
      };
      const lastFolderIndex = this.file.path.lastIndexOf('/');
      this.currentFolderPath = this.file.path.substring(0, lastFolderIndex);
      const parentDriveFolder = eXo.env.portal.spaceName && '/Documents/' || '/Private/';
      const startIndex = this.currentFolderPath.indexOf(parentDriveFolder);
      const nodePath = startIndex >= 0 ? this.currentFolderPath.substring(startIndex + parentDriveFolder.length) : '';
      this.getDestination(null, nodePath)
        .then(breadcrumb => this.documentsBreadcrumbSource = breadcrumb.slice());
      this.$refs.documentsTreeSelectorDrawer.open();
    },
    close() {
      this.$refs.documentsTreeSelectorDrawer.close();
    },
    getDestination(folder, path) {
      this.folder = folder;
      this.destinationFolderId = folder?.id;
      return this.$documentFileService.getBreadCrumbs(this.destinationFolderId, this.ownerId, !this.destinationFolderId && path || null)
        .then(breadCrumbs => {
          this.documentsBreadcrumbDestination = breadCrumbs;
          this.destinationFolderId = this.documentsBreadcrumbDestination[this.documentsBreadcrumbDestination.length - 1].id;
          this.destinationFolderPath = this.documentsBreadcrumbDestination[this.documentsBreadcrumbDestination.length - 1].path;
          return breadCrumbs;
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
    changeLocationDocument() {
      const destinationPath = this.folder && this.folder.path ? this.folder.path:`/Groups${this.groupId}/Documents`;
      if (this.actionType === 'move') {
        this.$root.$emit('documents-move', this.ownerId, this.file.id, destinationPath);
      }
      if (this.actionType === 'shortcut') {
        this.$root.$emit('create-shortcut', this.file.id, destinationPath, this.folder);
      }
      this.close();
    },
  }
};
</script>
