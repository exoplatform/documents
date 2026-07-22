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
    :open.sync="openNodes"
    :active.sync="activeNodes"
    :items="items"
    :search="search"
    :load-children="fetchChildren"
    class="treeView-item my-2"
    item-key="id"
    hoverable
    activatable
    open-on-click
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
    },
    // Free-text filter fed to v-treeview's built-in search (matches node names of
    // the loaded tree — drives and any expanded folders).
    search: {
      type: String,
      default: ''
    }
  },

  data: () => ({
    currentFolderPathTab: [],
    loading: false,
    pageSize: 20,
    offset: 0,
    limit: 0,
    currentDriveBreadCrumb: [],
    // Two-way bound so programmatic expansion (documentsBreadcrumbUpdated) and
    // user expand/collapse both actually open v-treeview nodes.
    openNodes: [],
    activeNodes: [],
  }),
  computed: {
    openLevel() {
      return this.items && this.items.length ?  [...new Set(this.currentFolderPathTab)] : [];
    },
    idItemActive() {
      return this.currentFolderPathTab?.length ? this.currentFolderPathTab[this.currentFolderPathTab.length-1] : [];
    }
  },
  watch: {
    // When the breadcrumb path is (re)computed, open every ancestor down to the
    // current folder, mark it active, and scroll it into view.
    currentFolderPathTab: {
      handler() {
        this.openNodes = this.openLevel;
        this.activeNodes = this.idItemActive ? [this.idItemActive] : [];
        this.$nextTick(this.scrollToActiveNode);
      },
      deep: true,
    },
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
    // Scrolls the currently active (current folder) tree node into view.
    scrollToActiveNode() {
      const el = this.$el && this.$el.querySelector && this.$el.querySelector('.v-treeview-node--active');
      if (el && el.scrollIntoView) {
        el.scrollIntoView({block: 'center', behavior: 'smooth'});
      }
    },
    // Entry point when the current-folder breadcrumb is (re)emitted: auto-expand
    // the tree down to - and highlight - the current folder. The root items are
    // loaded asynchronously when the drawer opens, so if they are not there yet we
    // wait for them before expanding.
    documentsBreadcrumbUpdated(documentsBreadcrumb) {
      if (!documentsBreadcrumb || !documentsBreadcrumb.length) {
        return;
      }
      this.$nextTick().then(() => {
        if (this.items?.length) {
          this.expandToCurrentFolder(documentsBreadcrumb);
        } else {
          const unwatch = this.$watch('items', () => {
            if (this.items?.length) {
              unwatch();
              this.expandToCurrentFolder(documentsBreadcrumb);
            }
          });
        }
      });
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
      if (this.currentFolderPathTab[this.currentFolderPathTab.length - 1] === folder.id) {
        return;
      }
      if (folder.drives) {
        this.$root.$emit('document-show-drives', this.getNodeChildrenById('space_drives'));
      } else if (folder.drive) {
        if (!this.currentDriveBreadCrumb.includes('space_drives')){
          this.currentDriveBreadCrumb.push('space_drives');
        }
        this.$root.ownerId = folder.identityId;
        this.$root.spaceId = folder.spaceId;
        this.$root.$emit('open-folder', folder);
      } else {
        this.currentFolderPathTab.push(folder.id);
        if (folder.name ==='Private' && eXo.env.portal.userIdentityId) {
          this.$root.ownerId = eXo.env.portal.userIdentityId;
          this.$root.spaceId = null;
          this.$root.driveView = false;
        } else if (folder.ownerId && folder.ownerId !== '0') {
          this.$root.ownerId = folder.ownerId;
        }
        this.$root.$emit('open-folder', folder);
      }
      
    },
    fetchChildren (item) {
      if (item.fetching) {
        return item.fetching;
      }
      this.$root.$emit('tree-loading', true);
      const folderId = item.identityId ? null : item.id;
      const promise = this.$documentFileService
        .getFullTreeData(item.identityId,folderId).then(data => {
          if (data && data.length) {
            const newItems = data.map(obj => {
              return JSON.parse(JSON.stringify(obj, (key, value) =>
              // eslint-disable-next-line no-undefined
                (value === null ? undefined : value)
              ));
            });
            newItems[0].spaceId = item.spaceId;
            // The backend returns children === null for leaf folders (no
            // subfolders); the clone above strips it (-> undefined). Coerce to an
            // array so empty drives/folders don't throw on map/filter and so the
            // node always ends up with a real children array (never null).
            newItems[0].children = newItems[0].children || [];
            newItems[0].children.forEach(child => {
              child.spaceId = item.spaceId;
            });
            if (!Array.isArray(item.children)) {
              this.$set(item, 'children', []);
            }
            const existingIds = new Set(item.children.map(c => c.id));
            const toAdd = newItems[0].children.filter(c => !existingIds.has(c.id));
            item.children.push(...toAdd);
          }
          this.$root.$emit('tree-loading', false);
        });
      item.fetching = promise;
      promise.then(() => {
        item.fetching = null;
      }).catch(() => {
        item.fetching = null;
      });
      return promise;
    },
    // Lazily loads a node's children (once) so the next level of the path can be
    // resolved. The "Space Drives" group (node.drives) has its children loaded
    // statically, so it is never fetched.
    async ensureChildrenLoaded(node) {
      if (!node || node.drives) {
        return;
      }
      if (Array.isArray(node.children) && node.children.length) {
        return;
      }
      await this.fetchChildren(node);
    },
    // Progressively expands the tree from the correct root down to the current
    // folder, then highlights it and scrolls it into view.
    //
    // Why progressive (and not just setting :open with the breadcrumb ids):
    //  - v-treeview only opens nodes that already exist in `items`; descendants
    //    are lazy-loaded, so each ancestor's children must be fetched+inserted
    //    BEFORE the next level's id can be found/opened. Feeding :open ids of
    //    not-yet-loaded nodes is silently dropped (and :open.sync strips them).
    //  - A SPACE drive node stands in for that space's "Documents" root: fetching
    //    a drive hoists the root's children directly under the drive node (the
    //    root node itself is skipped). So for a space we start from the drive node
    //    (matched by space identityId) and skip breadcrumb[0] (the drive root).
    async expandToCurrentFolder(breadcrumb) {
      if (!breadcrumb || !breadcrumb.length || !this.items || !this.items.length) {
        return;
      }
      const openIds = [];
      let currentNode = null;
      const isSpacePath = breadcrumb.some(crumb => crumb.path && crumb.path.includes('/Groups/spaces'));
      if (isSpacePath && !eXo.env.portal.spaceIdentityId) {
        // Attachment picker / documents home (non-space page): the target space
        // lives under the "Space Drives" group. Start from its drive node.
        const drivesGroup = this.items.find(node => node.drives);
        if (drivesGroup) {
          openIds.push(drivesGroup.id);
        }
        const identityId = breadcrumb[0].identityId || this.$root.ownerId;
        currentNode = this.getDriveByIdentityId(identityId);
        if (!currentNode) {
          // Target drive not loaded yet (e.g. beyond the first drives page):
          // expand the group only so the user can find it.
          this.currentFolderPathTab = [...openIds];
          return;
        }
        openIds.push(currentNode.id);
      } else {
        // Personal home, or a space page: items[0] is the home root and matches
        // breadcrumb[0].
        currentNode = this.items[0];
        openIds.push(currentNode.id);
      }
      // The drive-root crumb is represented by the root/drive node itself, so the
      // walk always starts at breadcrumb index 1.
      await this.ensureChildrenLoaded(currentNode);
      let activeId = currentNode.id;
      for (let i = 1; i < breadcrumb.length; i++) {
        const child = (currentNode.children || []).find(node => node.id === breadcrumb[i].id);
        if (!child) {
          break;
        }
        currentNode = child;
        activeId = child.id;
        // Only ancestors need expanding/loading: open this node and load its
        // children so the NEXT crumb can be resolved. The current folder itself
        // (last crumb) is left as-is - just highlighted. The awaits are
        // intentionally sequential: each level depends on its parent's children
        // being loaded first.
        if (i < breadcrumb.length - 1) {
          openIds.push(child.id);
          // eslint-disable-next-line no-await-in-loop
          await this.ensureChildrenLoaded(currentNode);
        }
      }
      // Let v-treeview render the newly-inserted nodes before we open them, then a
      // single clean assignment of REAL, loaded ids: the currentFolderPathTab
      // watcher opens exactly these nodes, marks the current folder active and
      // scrolls it into view. No phantom ids -> :open.sync cannot strip them.
      await this.$nextTick();
      this.currentFolderPathTab = [...new Set([...openIds, activeId])];
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
