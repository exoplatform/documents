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
    items: [],
    showHidden: false,
    pageSize: 20,
    drivesLimit: 20,
    drivesOffset: 0,
  }),
  created(){
    this.$root.$on('openTreeFolderDrawer', this.open);
  },
  beforeDestroy() {
    this.$root.$off('openTreeFolderDrawer', this.open);
  },
  methods: {
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
      try {
        const promises = [
          this.$documentFileService.getFullTreeData(
            this.$root.ownerId && this.$root.ownerId !== '0' ? this.$root.ownerId : eXo.env.portal.spaceIdentityId || eXo.env.portal.userIdentityId,
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
        this.items = this.items.map(obj => ({
          ...JSON.parse(JSON.stringify(obj, (key, value) =>
            // eslint-disable-next-line no-undefined
            (value === null ? undefined : value)
          )),
          children: obj.children || []
        }));

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
    }
  }
};
</script>
