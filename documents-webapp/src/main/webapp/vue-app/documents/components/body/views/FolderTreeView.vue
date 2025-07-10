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
    ownerId: eXo.env.portal.spaceIdentityId || eXo.env.portal.userIdentityId,
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
    retrieveDocumentTree(){
      this.items = [];
      this.loading = true;
      this.$documentFileService.getFullTreeData(this.ownerId,null,this.folderPath,this.showHidden)
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
