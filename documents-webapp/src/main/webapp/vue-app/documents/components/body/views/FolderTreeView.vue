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
    v-if="treeViewCollapsed"
    flat
    :loading="loading"
    width="310"
    class="border-right-color expand-transition-enter-active">
    <v-card-title class="pa-0 border-bottom-color"> 
      <span v-if="treeViewCollapsed" class="text-header">{{ $t('documents.tree.title') }}</span>
      <v-spacer />
      <v-tooltip bottom>
        <template #activator="{ on, attrs }">
          <v-btn
            icon
            v-bind="attrs"
            v-on="on"
            @click.stop.prevent="$root.$emit('tree-view-expand', false)">
            <img
              src="/social/images/sidebar.svg"
              class="icon-default-color mb-1"
              height="20px"
              width="20px">
          </v-btn>
        </template>
        <span class="caption">
          {{ $t('documents.tooltip.close.tree') }}
        </span>
      </v-tooltip>
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
    treeViewCollapsed: {
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
    items: []
  }),
  created() {
    this.retrieveDocumentTree();
    this.$root.$on('set-advanced-filter', advancedFilter => {
      this.showHidden = advancedFilter.showHidden;
      this.retrieveDocumentTree();
    });
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
      return await this.$documentFileService.getUserSpaces().then(data => {
        const spaces = data.spaces || [];
        if (spaces.length === 0) {
          return [];
        } else {
          const spacesTree = {
            name: this.$t('documents.label.drives'),
            icon: 'fa fa-layer-group',
            id: 'space_drives',
            drives: true,
            children: spaces.map(space => ({
              id: space.id,
              spaceId: space.id,
              identityId: space.identityId,
              name: space.prettyName,
              avatarUrl: space.avatarUrl,
              drive: true,
              children: [],
            }))
          };
          return spacesTree;
        }
      }).catch(error => {
        console.error('Error fetching user spaces:', error);
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
