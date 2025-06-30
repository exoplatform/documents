<!--
 *
 * Copyright (C) 2025 eXo Platform SAS.
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
 *
-->
<template>
  <exo-drawer 
    ref="treeViewDrawer"
    class="treeViewDrawer"
    @closed="close"
    right>
    <template slot="title">
      {{ $t('documents.drawer.tree') }}
    </template>
    <template slot="content">
      <document-tree-view
        :items="items"
        :folder-path="folderPath" />
    </template>
  </exo-drawer>
</template>
<script>

export default {
  props: {
    folderPath: {
      type: String,
      default: null
    },
  },
  data: () => ({
    ownerId: eXo.env.portal.spaceIdentityId || eXo.env.portal.userIdentityId,
    items: []
  }),
  created(){
    this.$root.$on('openTreeFolderDrawer', this.open);
  },
  beforeDestroy() {
    this.$root.$off('openTreeFolderDrawer', this.open);
  },
  methods: {
    open() {
      this.retrieveDocumentTree();
      this.$refs.treeViewDrawer?.open();
    },
    close() {
      this.$refs.treeViewDrawer?.close();
    },
    retrieveDocumentTree(){
      this.items = [];
      this.loading = true;
      this.$documentFileService.getFullTreeData(this.ownerId,null,this.folderPath)
        .then(data => {
          this.items = data|| [];
          this.items = this.items.map(obj => {
            return JSON.parse(JSON.stringify(obj, (key, value) => 
              // eslint-disable-next-line no-undefined
              (value === null ? undefined : value) 
            ));
          });

        })
        .finally(() => this.loading = false);
    },
  }
};
</script>
