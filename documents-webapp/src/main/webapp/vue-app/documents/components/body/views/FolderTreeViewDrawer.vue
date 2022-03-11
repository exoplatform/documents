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
        activatable
        open-on-click
        transition
        item-key="name"
        dense>
        <template v-slot:label="{ item }">
          <v-list-item-title @click="openFolder(item)" class="body-2 clickable">
            <v-icon size="24" class="primary--text">
              {{ 'fas fa-folder' }}
            </v-icon>
            <span class="mx-2">{{ item.name }}</span>
          </v-list-item-title>
        </template>
      </v-treeview>
    </template>
  </exo-drawer>
</template>
<script>

export default {
  props: {
  },
  data: () => ({
    ownerId: eXo.env.portal.spaceIdentityId || eXo.env.portal.userIdentityId,
    items: [],
  }),
  computed: {
    openLevel() {
      return this.items && this.items.length && [this.items[0].name];
    },
  },
  methods: {
    open() {
      this.retrieveNoteTree();
      this.$refs.folderBreadcrumb.open();
    },
    close() {
      this.$refs.folderBreadcrumb.close();
    },
    openFolder(folder){
      this.$emit('open-folder', folder);
    },
    retrieveNoteTree(){
      this.$documentFileService
        .getFullTreeData(this.ownerId).then(data => {
          if (data) {
            this.items = [];
            this.items = data;
          }
        });
    }
  }
};
</script>
