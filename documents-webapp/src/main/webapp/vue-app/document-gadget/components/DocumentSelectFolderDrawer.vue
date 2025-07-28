<!--
 Copyright (C) 2025 eXo Platform SAS.

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU Affero General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU Affero General Public License for more details.

 You should have received a copy of the GNU Affero General Public License
 along with this program. If not, see <http://www.gnu.org/licenses/>.
-->
<template>
  <exo-drawer
    ref="treeViewDrawer"
    class="treeViewDrawer"
    @closed="close"
    right
    go-back-button>
    <template #title>
      {{ $t('documents.documentGadget.settings.drive.selectFolders') }}
    </template>
    <template #content>
      <v-radio-group v-model="selectedId" @change="onChange">
        <v-treeview
          :items="items"
          :open.sync="open"
          :load-children="fetchChildren"
          item-key="id"
          activatable
          open-on-click
          class="custom-tree">
          <template #prepend="{ item }">
            <v-radio :value="item.id" color="primary" />
          </template>
          <template #label="{ item }">
            {{ item.name }}
          </template>
        </v-treeview>
      </v-radio-group>
    </template>
    <template #footer>
      <div class="d-flex">
        <v-spacer />
        <v-btn
          class="btn me-2"
          @click="close">
          {{ $t('documents.label.button.back') }}
        </v-btn>
        <v-btn
          class="btn btn-primary"
          @click="apply">
          {{ $t('documents.label.apply') }}
        </v-btn>
      </div>
    </template>
  </exo-drawer>
</template>
<script>

export default {
  props: {
    spaceId: {
      type: String,
      default: null
    },
    selectedFolder: {
      type: Array,
      default: () => [],
    },
  },
  data: () => ({
    items: [],
    selectedId: null,
    space: [],
    showHidden: false,
  }),
  computed: {
    ownerId() {
      return this.space?.identityId;
    }
  },
  created(){
    this.$root.$on('openSelectFolderDrawer', this.open);
  },
  beforeDestroy() {
    this.$root.$off('openSelectFolderDrawer', this.open);
  },
  methods: {
    onChange(item) {
      this.selectedId = item;
    },
    open(showHidden) {
      this.showHidden = showHidden;
      this.retrieveDocumentTree();
      this.$refs.treeViewDrawer?.open();
    },
    close() {
      this.$refs.treeViewDrawer?.close();
    },
    async retrieveDocumentTree() {
      this.items = [];
      this.loading = true;
      await this.$spaceService.getSpaceById(this.spaceId, 'identity')
        .then((space) => {
          this.space = space;
        });
      this.$documentFileService.getFullTreeData(this.ownerId, null , '', false)
        .then(data => {
          this.items = data|| [];
          this.items = this.items.map(obj => {
            return JSON.parse(JSON.stringify(obj, (key, value) =>
            // eslint-disable-next-line no-undefined
              (value === null ? undefined : value)
            ));
          });
          this.selectedId = this.selectedFolder;
        })
        .finally(() => this.loading = false);
    },
    async fetchChildren(item) {
      const folderId = item.id;
      await this.$documentFileService
        .getFullTreeData(this.ownerId, folderId).then(data => {
          if (data) {
            const newItems = data.map(obj => {
              return JSON.parse(JSON.stringify(obj, (key, value) =>
                // eslint-disable-next-line no-undefined
                (value === null ? undefined : value)
              ));
            });
            newItems[0].spaceId = item.spaceId;
            newItems[0].children.map(child => {
              child.spaceId = item.spaceId;
            }
            );
            item.children.push(...newItems[0].children);
          }
        });
    },
    apply() {
      const rootId = this.items.length > 0 ? this.items[0].id : '';
      const result = this.selectedId === rootId ? '' : this.selectedId;
      this.$emit('apply', result);
      this.close();
    }
  }
};
</script>
