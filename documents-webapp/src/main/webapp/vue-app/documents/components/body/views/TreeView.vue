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
      <div class="d-flex clickable" v-if="item.isLoadMore">
        <v-btn
          class="white mx-auto no-border primary--text no-box-shadow"
          @click="loadMoreDrives(item.query,item.offset,item.limit)">
          {{ $t('documents.loadMore') }}
        </v-btn>
      </div>
      <div
        v-else
        class="d-flex clickable"
        @click="openFolder(item)">
        <v-list-item-avatar 
          v-if="item.avatarUrl"
          size="24"
          class="mx-0"
          :class="item.spaceId && 'spaceAvatar' || 'userAvatar'"
          tile>
          <v-avatar :size="24">
            <img
              :src="item.avatarUrl"
              alt=""
              class="rounded"
              width="24"
              height="24">
          </v-avatar>
        </v-list-item-avatar>
        <v-icon
          v-else
          size="24"
          :class="item.icon? '' : 'primary--text'">
          {{ item.icon ? item.icon : 'fas fa-folder' }}
        </v-icon>
        <v-list-item-title 
          class="body-1 mx-2 mt-1"
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
    currentFolderPathTab: [],
    loading: false,
    pageSize: 20,
    offset: 0,
    limit: 0,
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
    this.$root.$on('documentsBreadcrumb',this.documentsBreadcrumbUpdated);
    this.$root.$on('folder-created',this.folderCreated);
    this.$root.$on('confirm-document-deletion',this.confirmDocumentDeletion);
    this.$root.$on('hide-element',this.hideElement);
    this.$root.$on('load-more-drives', this.loadMoreDrives);
  },
  beforeDestroy() {
    this.$root.$off('documentsBreadcrumb',this.documentsBreadcrumbUpdated);
    this.$root.$off('folder-created',this.folderCreated);
    this.$root.$off('confirm-document-deletion',this.confirmDocumentDeletion);
    this.$root.$off('hide-element', this.hideElement);
    this.$root.$off('load-more-drives', this.loadMoreDrives);
  },
  methods: {
    documentsBreadcrumbUpdated(documentsBreadcrumb) {
      const tab = [];
      documentsBreadcrumb.forEach(element => tab.push(element.id));
      this.currentFolderPathTab = tab;
    },
    folderCreated(createdFolder) {
      this.addChildren(this.items, createdFolder.parentFolderId, createdFolder);
    },
    confirmDocumentDeletion(deletedItem) {
      if (deletedItem.folder) {
        this.removeItem(this.items, deletedItem.id);
      } 
    },
    hideElement(hiddenItem) {
      if (hiddenItem.folder) {
        if (!this.showHidden){
          this.removeItem(this.items, hiddenItem.id);
        } else {
          this.setHidden(this.items,hiddenItem);
        } 
      } 
    },
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
      this.currentFolderPathTab.push(folder.id);
      if (folder.drives) {
        this.$root.$emit('document-show-drives', this.getNodeChildrenById('space_drives'));
      } else if (folder.drive) {
        this.$root.ownerId = folder.identityId;
        this.$root.spaceId = folder.spaceId;
        this.$root.$emit('open-folder', folder);
      } else {
        if (folder.name ==='Private' && eXo.env.portal.userIdentityId) {
          this.$root.ownerId = eXo.env.portal.userIdentityId;
          this.$root.spaceId = null;
          this.$root.driveView = false;
        } else if (folder.ownerId) {
          this.$root.ownerId = folder.ownerId;
        }
        this.$root.$emit('open-folder', folder);
      }
    },
    fetchChildren (item) {
      this.$root.$emit('tree-loading', true);
      const folderId = item.identityId ? null : item.id;
      this.$documentFileService
        .getFullTreeData(item.identityId,folderId).then(data => {
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
            this.currentFolderPathTab.push(item.id);
          }
          this.$root.$emit('tree-loading', false);
        });
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
    },
    loadMoreDrives(query,offset, limit) {
      this.offset = offset ? offset + this.pageSize : this.offset +this.pageSize;
      this.limit = limit ? limit + this.pageSize : this.limit +this.pageSize;
      this.removeItem(this.items, 'load_more');
      this.$documentFileService.getUserSpaces(query,this.offset,this.limit).then(data => {
        const spaces = data.spaces || [];
        if (spaces.length > 0) {
          const newChildren  =spaces.map(space => ({
            id: space.id,
            spaceId: space.id,
            groupId: space.groupId,
            identityId: space.identityId,
            name: space.displayName,
            avatarUrl: space.avatarUrl,
            drive: true,
            children: [],
          }));
          this.$root.$emit('add-drives', newChildren);
          if (data.size > this.limit) {
            newChildren.push({
              id: 'load_more',
              name: this.$t('documents.loadMore'),
              isLoadMore: true,
              offset: this.offset,
              limit: this.limit,
            });
          }  
          this.addChildren(this.items, 'space_drives', newChildren);
        }
        this.$root.$emit('document-show-drives', this.getNodeChildrenById('space_drives'));
      });    
    },
    addChildren(tree, targetId, newChildren) {
      for (const node of tree) {
        if (node.id === targetId) {
          if (!Array.isArray(node.children)) {
            node.children = [];
          }
          if (Array.isArray(newChildren)) {
            node.children.push(...newChildren);
          } else {
            node.children.push(newChildren);
          }
          return true;
        }
        if (Array.isArray(node.children)) {
          const added = this.addChildren(node.children, targetId, newChildren);
          if (added) {return true;}
        }
      }
      return false;
    },
    getNodeChildrenById(id, nodes = this.items) {
      for (const node of nodes) {
        if (node.id === id) {
          return node.children || [];
        }
        if (node.children && node.children.length > 0) {
          const result = this.getNodeChildrenById(id, node.children);
          if (result) {return result;}
        }
      }
      return null; // Node not found
    },
    
    getDriveByIdentityId(id, nodes = this.items) {
      const drivesNode = nodes.find(item => item.drives === true);
      if (drivesNode) {
        const drive = drivesNode.children.find(item => item.drive === true && item.identityId === id);
        return drive || null;
      } else {
        return null;
      }
    },
  },
};
</script>
