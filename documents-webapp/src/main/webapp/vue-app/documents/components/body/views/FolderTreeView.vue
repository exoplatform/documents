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
  <v-card 
    v-if="treeViewExpended"
    flat
    :loading="loading"
    class="border-right-color expand-transition-enter-active col-3">
    <v-card-title class="pa-0 border-bottom-color"> 
      <span v-if="!treeViewExpended" class="text-header">{{ $t('documents.tree.title') }}</span>
      <v-spacer />
      <v-btn
        icon
        v-bind="attrs"
        v-on="on"
        @click.stop.prevent="$root.$emit('tree-view-expend', false)">
        <img
          alt=""
          :title="$t('documents.tooltip.close.tree')"
          src="/social/images/sidebar.svg"
          class="icon-default-color mb-1"
          height="20px"
          width="20px">
      </v-btn>
    </v-card-title>
    <v-card-text class="px-0">
      <document-tree-view
        :items="items"
        :folder-path="folderPath" 
        :show-hidden="showHidden" />
    </v-card-text>
  </v-card>
</template>
<script>

export default {

  props: {
    folderPath: {
      type: String,
      default: null
    },
    treeViewExpended: {
      type: String,
      default: null
    },
    isMobile: {
      type: Boolean,
      default: false
    }
  },

  data: () => ({
    loading: false,
    showHidden: false,
    items: [],
    pageSize: 20,
    drivesLimit: 20,
    drivesOffset: 0,
  }),
  created() {
    this.retrieveDocumentTree();
    this.$root.$on('set-advanced-filter', advancedFilter => {
      this.showHidden = advancedFilter.showHidden;
      this.retrieveDocumentTree();
    });
    this.$root.$on('search-drives', this.searchDrives);
  },
  
  beforeDestroy() {
    this.$root.$off('search-drives', this.searchDrives);
  },
  methods: {
    async retrieveDocumentTree() {
      this.items = [];
      this.loading = true;
      try {
        const promises = [
          this.$documentFileService.getFullTreeData(
            this.$root.ownerId,
            null,
            this.folderPath,
            this.showHidden
          )
        ];
        if (!eXo.env.portal.spaceIdentityId) {
          promises.push(this.getUserSpaces());
          promises.push(this.getUserProfile());
        } else {
          promises.push(Promise.resolve(null));
          promises.push(Promise.resolve({}));
        }
        const [treeData, userSpacesTree, userProfile] = await Promise.all(promises);
        this.items = treeData || [];
        if (!eXo.env.portal.spaceIdentityId && this.items[0]?.name === 'Private') {
          this.items[0].avatarUrl = userProfile.avatar || '';
        }
        if (userSpacesTree && userSpacesTree.children?.length > 0) {
          this.items.push(userSpacesTree);
        }
        this.items = this.items.map(obj => {
          return JSON.parse(JSON.stringify(obj, (key, value) => 
          // eslint-disable-next-line no-undefined
            (value === null ? undefined : value) 
          ));
        });

      } catch (error) {
        console.error('Error retrieving document tree:', error);
      } finally {
        this.loading = false;
      }
    },
    async getUserSpaces() {
      return await this.$documentFileService.getUserSpaces(null,this.drivesOffset,this.drivesLimit).then(data => {
        const spaces = data.spaces || [];
        if (spaces.length === 0) {
          return [];
        } else {
          const spacesList = spaces.map(space => ({
            id: space.id,
            spaceId: space.id,
            groupId: space.groupId,
            identityId: space.identityId,
            name: space.displayName,
            avatarUrl: space.avatarUrl,
            drive: true,
            children: [],
          }));
          this.$root.$emit('add-drives', spacesList);
          if (data.size > this.drivesLimit) {
            spacesList.push({
              id: 'load_more',
              name: this.$t('documents.loadMore'),
              isLoadMore: true,
              offset: this.drivesOffset,
              limit: this.drivesLimit,
            });
          }  
          const spacesTree = {
            name: this.$t('documents.label.drives'),
            icon: 'fa fa-layer-group',
            id: 'space_drives',
            drives: true,
            children: spacesList
          };
          this.$root.$emit('loading-documents', false);
          return spacesTree;
        }
      }).catch(error => {
        console.error('Error fetching user spaces:', error);
        this.$root.$emit('loading-documents', false);
        return [];
      });
    },
    async getUserProfile() {
      try {
        const profile = await this.$documentFileService.getUserProfile(eXo.env.portal.userName);
        return profile || {};
      } catch (error) {
        console.error('Error fetching user profile:', error);
        return {};
      }
    },
    searchDrives(query) {
      this.drivesOffset = 0;
      this.drivesLimit = 20;
      this.$documentFileService.getUserSpaces(query, this.drivesOffset, this.drivesLimit).then(data => {
        const drives = data.spaces || [];
        if (drives.length > 0) {
          const newChildren = drives.map(drive => ({
            id: drive.id,
            spaceId: drive.id,
            identityId: drive.identityId,
            name: drive.displayName,
            avatarUrl: drive.avatarUrl,
            drive: true,
            children: [],
          }));
          if (data.size > this.drivesLimit) {
            newChildren.push({
              id: 'load_more',
              name: this.$t('documents.loadMore'),
              isLoadMore: true,
              offset: this.drivesOffset,
              limit: this.drivesLimit,
              query: query,
            });
          }  
          this.setChildren(this.items, 'space_drives', newChildren);
          this.$root.$emit('document-show-drives', newChildren);
        } else {
          this.setChildren(this.items, 'space_drives', []);
          this.$root.$emit('document-show-drives', []);
        }
        this.$root.$emit('loading-documents', false);
      }).catch(error => {
        console.error('Error searching drives:', error);
        this.$root.$emit('loading-documents', false);
      });
    },
    setChildren(tree, targetId, newChildren) {
      for (const node of tree) {
        if (node.id === targetId) {
          if (!Array.isArray(node.children)) {
            node.children = [];
          }
          node.children=newChildren;
          return true;
        }
        if (Array.isArray(node.children)) {
          const added = this.setChildren(node.children, targetId, newChildren);
          if (added) {return true;}
        }
      }
      return false;
    },
  }
};
</script>
