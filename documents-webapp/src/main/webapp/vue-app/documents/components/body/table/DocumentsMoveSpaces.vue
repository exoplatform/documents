<!--
* Copyright (C) 2022 eXo Platform SAS
*
*  This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU Affero General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU Affero General Public License
* along with this program.  If not, see <gnu.org/licenses>.
-->
<template>
  <div>
    <v-container
      pa-0
      fluid
      d-flex>
      <v-combobox
        ref="select"
        v-model="spaceModel"
        :items="spaces"
        :filter="filterSpaces"
        attach
        class="pt-0 ps-2 mb-0 inputSpaceName"
        solo
        @change="deleteSpace()">
        <template v-slot:selection="{ attrs, item, parent, selected }">
          <v-chip
            v-if="item === Object(item)"
            v-bind="attrs"
            :input-value="selected"
            close
            @click:close="deleteSpace">
            <v-avatar left>
              <v-img :src="item.avatarUrl" />
            </v-avatar>
            <span
              class="body-2 text-truncate"
              @click="parent.selectItem(item)">
              {{ item.displayName }}
            </span>
          </v-chip>
        </template>
        <template v-slot:item="{ index, item }">
          <v-list-item @click="updateSpace(item)">
            <v-chip
              close>
              <v-avatar left>
                <v-img :src="item.avatarUrl" />
              </v-avatar>
              <span class="text-truncate">
                {{ item.displayName }}
              </span>
            </v-chip>
          </v-list-item>
        </template>
      </v-combobox>
    </v-container>
  </div>
</template>

<script>
export default {
  props: {
    space: {
      type: Object,
      default: () => {
        return {};
      }
    }
  },
  data() {
    return {
      spaces: [],
      spaceModel: null,
      search: null,
      offset: 0,
      limit: 20,
      spacesSize: 0,
      loadingSpaces: false,
    };
  },
  watch: {
    spaceModel () {
      if (this.$refs.select && this.$refs.select.isMenuActive) {
        setTimeout(() => {
          this.$refs.select.isMenuActive = false;
        }, 50);
      }
    },
    spacesSize (){
      if (this.loadingSpaces && this.spacesSize >= this.limit){
        this.limit += this.spacesSize;
        this.getSpaces();
      }
    }
  },
  created() {
    this.getSpaces();
    this.spaceModel = this.space;
    $(document).on('mousedown', () => {
      if (this.$refs.select.isMenuActive) {
        window.setTimeout(() => {
          this.$refs.select.isMenuActive = false;
        }, 200);
      }
    });
  },
  methods: {
    getSpaces() {
      this.loadingSpaces = true;
      this.$spaceService.getSpaces(this.query, 0, this.limit, 'member', 'identity') .then((spaces) => {
        this.spaces = spaces.spaces;
        this.spacesSize = spaces && spaces.size || 0;
      }).finally(() => this.loadingSpaces = false);
    },
    filterSpaces(item, queryText) {
      return  item.displayName.toLocaleLowerCase().indexOf(queryText.toLocaleLowerCase()) >-1;},
    updateSpace(space) {
      this.$root.$emit('current-space',space);
      this.spaceModel = space;
    },
    deleteSpace() {
      this.spaceModel = null;
      this.$root.$emit('current-space',null);
    },
  }
};
</script>