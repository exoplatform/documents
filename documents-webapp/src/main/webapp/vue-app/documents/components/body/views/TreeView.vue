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
  <v-treeview
    :open.sync="openLevel"
    :items="items"
    :load-children="fetchChildren"
    class="treeView-item my-2"
    item-key="id"
    hoverable
    activatable
    open-on-click
    open-all
    transition>
    <template #label="{ item }">
      <div class="d-flex clickable" @click="openFolder(item)">
        <v-icon size="24" class="primary--text">
          {{ 'fas fa-folder' }}
        </v-icon>
        <v-list-item-title 
          class="body-2 mx-2 mt-1"
          :class="idItemActive === item.id ? 'primary--text font-weight-bold' : item.hidden ? 'text-light-color' : ''">
          {{ displayName(item.name) }} 
          <v-icon
            v-if="item.hidden"
            size="13">
            fas fa-eye-slash
          </v-icon>              
        </v-list-item-title>
      </div>
    </template>
  </v-treeview>
</template>
<script>

export default {

  props: {
    folderPath: {
      type: String,
      default: null
    },
    items: {
      type: Array,
      default: () => []
    },
    showHidden: {
      type: Boolean,
      default: false
    }
  },

  data: () => ({
    ownerId: eXo.env.portal.spaceIdentityId || eXo.env.portal.userIdentityId,
    currentFolderPathTab: [],
    loading: false,
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
    this.$root.$on('folder-created',createdFolder => {
      this.addChild(this.items, createdFolder.parentFolderId, createdFolder);
    });
    this.$root.$on('confirm-document-deletion',deletedItem => {
      if (deletedItem.folder) {
        this.removeItem(this.items, deletedItem.id);
      }     
    });
    this.$root.$on('hide-element',hiddenItem => {
      if (hiddenItem.folder) {
        if (!this.showHidden){
          this.removeItem(this.items, hiddenItem.id);
        } else {
          this.setHidden(this.items,hiddenItem);
        }
        
      }     
    });
  },
  methods: {
    displayName(name) {
      if (name==='Private'){
        return this.$t('documents.label.userHomeDocuments');
      } else if (name==='Documents'){
        return this.$t('documents.label.spaceHomeDocuments');
      }
      return name;
    },   
    sortItems(items) {
      const collator = new Intl.Collator(eXo.env.portal.language, {numeric: true, sensitivity: 'base'});
      return items.sort((a, b) => collator.compare(a.name, b.name));
    },
    sortNestedItems(items) {
      this.sortItems(items);
      items.forEach(item => {
        if (item.children?.length) {
          this.sortNestedItems(item.children);
        }
      });
      return items;
    },
    openFolder(folder){
      this.$root.$emit('open-folder', folder);
    },
    fetchChildren (item) {
      this.$root.$emit('tree-loading', true);
      this.$documentFileService
        .getFullTreeData(this.ownerId,item.id).then(data => {
          if (data) {
            const newItems = data.map(obj => {
              return JSON.parse(JSON.stringify(obj, (key, value) => 
              // eslint-disable-next-line no-undefined
                (value === null ? undefined : value) 
              ));
            });
            item.children.push(...newItems[0].children);
            this.currentFolderPathTab.push(item.id);
          }
          this.$root.$emit('tree-loading', false);
        });
    },
    addChild(tree, targetId, newChild) {
      for (const node of tree) {
        if (node.id === targetId) {
          if (!Array.isArray(node.children)) {
            node.children = [];
          }
          node.children.push(newChild);
          return true;
        }
        if (Array.isArray(node.children)) {
          const added = this.addChild(node.children, targetId, newChild);
          if (added) {return true;}
        }
      }
      return false;
    },
    removeItem(nodes, idToRemove) {
      return nodes
        .map(node => {
          if (node.children) {
            node.children = this.removeItem(node.children, idToRemove);
          }
          return node;
        })
        .filter(node => node.id !== idToRemove);
    },
    setHidden(tree, folder) {
      for (const node of tree) {
        if (node.id === folder.id) {
          node.hidden = folder.hidden;
          return true;
        }
        if (Array.isArray(node.children)) {
          const found = this.setHidden(node.children, folder);
          if (found) {return true;}
        }
      }
      return false;
    }

  },
};
</script>
