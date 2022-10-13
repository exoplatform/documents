<template>
  <exo-drawer 
    ref="folderBreadcrumb"
    class="folderBreadcrumb"
    @closed="close"
    right>
    <template slot="title">
      {{ $t('documents.drawer.tree') }}
    </template>
    <template slot="content">
      <v-treeview
        :open.sync="openLevel"
        :items="items"
        class="treeView-item my-2"
        item-key="name"
        hoverable
        activatable
        open-on-click
        transition>
        <template #label="{ item }">
          <div class="d-flex clickable" @click="openFolder(item)">
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
  </exo-drawer>
</template>
<script>

export default {
  data: () => ({
    ownerId: eXo.env.portal.spaceIdentityId || eXo.env.portal.userIdentityId,
    items: [],
  }),
  computed: {
    openLevel() {
      return this.items && this.items.length && [this.items[0].name] || [];
    },
  },
  methods: {
    open() {
      this.retrieveDocumentTree();
      this.$refs.folderBreadcrumb?.open();
    },
    close() {
      this.$refs.folderBreadcrumb?.close();
    },
    openFolder(folder){
      this.$root.$emit('open-folder', folder);
    },
    retrieveDocumentTree(){
      this.items = [];
      this.$refs.folderBreadcrumb?.startLoading();
      this.$documentFileService.getFullTreeData(this.ownerId)
        .then(data => this.items = data || [])
        .finally(() => this.$refs.folderBreadcrumb?.endLoading());
    }
  }
};
</script>
