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
    :use-filter="true"
    :filter-placeholder="$t('documents.drawer.tree.searchDrives')"
    @filter-updated="onFilterDrives"
    @closed="close"
    right>
    <template slot="title">
      <!-- Back arrow (returns to the underlying list/explorer) + title. The filter
           icon is provided by exo-drawer's built-in use-filter, wired to the tree's
           existing drive search. -->
      <div class="d-flex align-center">
        <v-btn
          icon
          small
          class="ms-n2 me-1 flex-shrink-0"
          :aria-label="$t('documents.label.button.back')"
          :title="$t('documents.label.button.back')"
          @click="close()">
          <v-icon size="20">fa-arrow-left</v-icon>
        </v-btn>
        <span class="text-truncate">{{ $t('documents.drawer.tree') }}</span>
      </div>
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
    // Selecting a drive/space/folder in the tree emits 'open-folder' -> close
    // this navigation drawer so the list shows the newly selected location.
    this.$root.$on('open-folder', this.close);
  },
  beforeDestroy() {
    this.$root.$off('openTreeFolderDrawer', this.open);
    this.$root.$off('open-folder', this.close);
  },
  methods: {
    async open(showHidden, breadcrumb) {
      this.showHidden = showHidden;
      this.$refs.treeViewDrawer?.open();
      // The tree content is created lazily when the drawer opens, so the
      // 'documentsBreadcrumb' event emitted before opening is lost. (Re)emit it
      // AFTER the root data is loaded and the tree exists, so the tree expands
      // the drive node + ancestors down to - and highlights - the current folder.
      await this.retrieveDocumentTree();
      if (breadcrumb && breadcrumb.length) {
        this.$nextTick(() => this.$root.$emit('documentsBreadcrumb', breadcrumb));
      }
    },
    close() {
      this.$refs.treeViewDrawer?.close();
    },
    // exo-drawer's built-in header filter → the tree's existing drive search
    // (searches the user's spaces server-side; an empty query resets to all).
    onFilterDrives(query) {
      this.$root.$emit('search-drives', query || '');
    },
    async retrieveDocumentTree() {
      this.items = [];
      this.loading = true;
      try {
        // The home root is FIXED to the page identity (the space on a space page,
        // else the current user's home). It must NOT follow the mutable
        // $root.ownerId - otherwise, after navigating into a space, re-opening the
        // tree would rebuild the first root from the space owner and overwrite
        // "Personal Documents" with the space's "Documents" folder. Navigation
        // only drives expansion/active state, never the root nodes' identities.
        const homeIdentityId = eXo.env.portal.spaceIdentityId || eXo.env.portal.userIdentityId;
        const promises = [
          this.$documentFileService.getFullTreeData(
            homeIdentityId,
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
        // The backend marks leaf folders (no subfolders) with children === null.
        // Vuetify's v-treeview requires children to be an ARRAY or absent - a
        // literal null makes buildTree crash (null.map). The JSON clone below
        // strips every nested null (null -> undefined -> key omitted); we must NOT
        // re-inject the RAW obj.children afterwards (that would bring the nested
        // nulls back). We only coerce the CLONED top-level children to an array so
        // the fixed roots stay expandable.
        this.items = this.items.map(obj => {
          const cloned = JSON.parse(JSON.stringify(obj, (key, value) =>
            // eslint-disable-next-line no-undefined
            (value === null ? undefined : value)
          ));
          cloned.children = cloned.children || [];
          return cloned;
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
    }
  }
};
</script>
