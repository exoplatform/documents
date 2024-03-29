<template>
  <exo-drawer
    v-model="drawer"
    ref="documentsTreeSelectorDrawer"
    class="documentsTreeSelectorDrawer"
    @closed="cancel"
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
                  :space="space"
                  :is-mobile="isMobile" />
              </div>
            </div>
          </div>
        </v-list-item>
        <v-list-item v-if="showCurrentLocation">
          <div class="py-2 width-full">
            <span class="font-weight-bold text-color text-no-wrap pb-2">{{
              $t('documents.move.drawer.currentPosition')
            }}</span>
            <documents-breadcrumb
              :show-icon="false"
              :documents-breadcrumb="documentsBreadcrumbSource"
              :disabled-icon-tree="true"
              :is-mobile="isMobile"
              move />
          </div>
        </v-list-item>
        <v-list-item>
          <div class="py-2  width-full">
            <span class="font-weight-bold text-color text-no-wrap pb-2">{{
              $t('documents.move.drawer.destination')
            }}</span>
            <documents-breadcrumb
              :show-icon="false"
              :documents-breadcrumb="documentsBreadcrumbDestination"
              :disabled-icon-tree="true"
              :is-mobile="isMobile"
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
          :load-children="fetchChildren"
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
          @click="cancel()"
          class="btn ml-2">
          {{ $t('documents.move.drawer.button.cancel') }}
        </v-btn>
        <v-btn
          :disabled="disableButton"
          @click="changeLocationDocument()"
          :loading="isLoading"
          class="btn btn-primary ml-2">
          {{ submitButton }}
        </v-btn>
      </div>
    </template>
  </exo-drawer>
</template>
<script>

export default {
  props: {
    isMobile: {
      type: Boolean,
      default: false
    }
  },
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
    actionType: '',
    isLoading: false,
    isMultiSelection: false,
    showCurrentLocation: true
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
    this.$root.$on('current-space', data => {
      const ownerId = data ? data.identity.id : null;
      this.items = [];
      this.space = data;
      this.groupId = data.groupId;
      this.documentsBreadcrumbDestination = [{
        name: 'Documents'
      }];
      this.retrieveDocumentTree(ownerId);
    });
    this.$root.$on('open-document-tree-selector-drawer', (file, actionType, isMultiSelection, showCurrentLocation) => {
      this.actionType = actionType;
      this.isMultiSelection = isMultiSelection;
      this.showCurrentLocation = showCurrentLocation;
      if (file) {
        this.open(file);
      }
    });
    this.$root.$on('document-moved', () => {
      this.close();
    });
    this.$root.$on('shortcut-created', () => {
      this.close();
    });
    this.$root.$on('cancel-action', () => {
      this.cancel();
    });
  },
  watch: {
    isLoading() {
      if (this.isLoading) {
        this.$refs.documentsTreeSelectorDrawer.startLoading();
      } else {
        this.$refs.documentsTreeSelectorDrawer.endLoading();
      }
    },
  },
  methods: {
    fetchChildren (item) {
      this.$documentFileService
        .getFullTreeData(this.ownerId,item.id).then(data => {
          if (data) {
            item.children.push(...data[0].children);
          }
        });
    },
    open(file) {
      this.file = file;
      const ownerId = eXo.env.portal.spaceIdentityId || eXo.env.portal.userIdentityId;
      this.retrieveDocumentTree(ownerId);
      this.space = {
        displayName: this.spaceDisplayName ? this.spaceDisplayName : this.userName,
        avatarUrl: this.spaceDisplayName ? `${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/social/spaces/${this.spaceName}/avatar` :
          `${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/social/users/${this.userName}/avatar`,
      };
      const lastFolderIndex = this.file?.path.lastIndexOf('/');
      this.currentFolderPath = this.file?.path.substring(0, lastFolderIndex);
      const parentDriveFolder = eXo.env.portal.spaceName && '/Documents/' || '/Private/';
      const startIndex = this.currentFolderPath.indexOf(parentDriveFolder);
      const nodePath = startIndex >= 0 ? this.currentFolderPath.substring(startIndex + parentDriveFolder.length) : '';
      this.getDestination(null, nodePath)
        .then(breadcrumb => this.documentsBreadcrumbSource = breadcrumb.slice());
      this.$refs.documentsTreeSelectorDrawer.open();
    },
    cancel() {
      this.close();
      this.$root.$emit('cancel-alert-actions');
    },
    close() {
      this.isLoading = false;
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
        });
    },
    retrieveDocumentTree(ownerId) {
      this.$documentFileService
        .getFullTreeData(ownerId).then(data => {
          if (data) {
            this.items = [];
            this.items = data;
            if (this.items.length > 0) {
              this.folder = this.items[0];
            }
          }
        });
    },
    changeLocationDocument() {
      this.isLoading = true;
      const destinationPath = this.folder && this.folder.path ? this.folder.path : `/Groups${this.groupId}/Documents`;
      if (this.actionType === 'move') {
        if (this.isMultiSelection) {
          this.$root.$emit('documents-bulk-move', this.ownerId, destinationPath, this.folder, this.space);
          this.close();
        } else {
          this.$root.$emit('documents-move', this.ownerId, this.file, destinationPath, this.folder, this.space);
        }
      }
      if (this.actionType === 'shortcut') {
        this.$root.$emit('create-shortcut', this.file, destinationPath, this.folder, this.space);
      }
    },
  }
};
</script>
