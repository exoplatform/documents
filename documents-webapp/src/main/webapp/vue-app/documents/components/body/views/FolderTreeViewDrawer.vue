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
        item-key="id"
        hoverable
        activatable
        open-on-click
        transition>
        <template #label="{ item }">
          <div class="d-flex clickable" @click="openFolder(item)">
            <v-icon size="24" class="primary--text">
              {{ 'fas fa-folder' }}
            </v-icon>
            <v-list-item-title 
              class="body-2 mx-2 mt-1"
              :class="idItemActive === item.id ? 'primary--text font-weight-bold' : ''">
              {{ item.name === 'Documents' ? $t('documents.label.spaceHomeDocuments') : item.name }}               
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
    currentFolderPathTab: [],
  }),
  computed: {
    openLevel() {
      return this.items && this.items.length ? this.currentFolderPathTab : [];
    },
    idItemActive() {
      return this.currentFolderPathTab && this.currentFolderPathTab.length ? this.currentFolderPathTab[this.currentFolderPathTab.length-1] : [];
    }
  },
  created() {
    this.$root.$on('documentsBreadcrumb',documentsBreadcrumb => {
      const tab = [];
      documentsBreadcrumb.forEach(element => tab.push(element.id));
      this.currentFolderPathTab = tab;
    });
  },
  methods: {
    sortItems(items) {
      const collator = new Intl.Collator(eXo.env.portal.language, {numeric: true, sensitivity: 'base'});
      return items.sort((a, b) => collator.compare(a.name, b.name));
    },
    sortNestedItems(items) {
      this.sortItems(items);
      items.forEach(item => {
        if (item.children.length) {
          this.sortNestedItems(item.children);
        }
      });
      return items;
    },
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
        .then(data => {
          this.items = data && this.sortNestedItems(data) || [];
        })
        .finally(() => this.$refs.folderBreadcrumb?.endLoading());
    }
  }
};
</script>
