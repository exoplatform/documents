<!--
* Copyright (C) 2021 eXo Platform SAS
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
  <exo-drawer 
    ref="documentVisibilityDrawer"
    class="documentVisibilityDrawer"
    @closed="close"
    right>
    <template slot="title">
      {{ visibilityTitle }}
    </template>
    <template slot="content">
      <v-list-item>
        <v-list-item-content class="my-1">
          <exo-user-avatar
            :identity="ownerIdentity"
            avatar-class="me-2"
            size="42"
            bold-title>
            <template slot="subTitle">
              <span class="caption font-italic">
                {{ $t('documents.label.owner') }}
              </span>
            </template>
          </exo-user-avatar>
        </v-list-item-content>
      </v-list-item>

      <v-divider dark class="mx-4" />
    </template>
  </exo-drawer>
</template>
<script>

export default {
  props: {
    file: {
      type: Object,
      default: null,
    },
    fileName: {
      type: String,
      default: '',
    },
  },
  data: () => ({
    ownerIdentity: [],
  }),
  computed: {
    visibilityTitle(){
      return this.$t('documents.label.visibilityTitle').replace('{0}', this.fileName);
    },
  },
  mounted(){
    this.$userService.getUser(this.file.creatorIdentity.remoteId).then(user => {
      this.ownerIdentity = user;
    });
  },
  methods: {
    open() {
      this.$refs.documentVisibilityDrawer.open();
    },
    close() {
      this.$refs.documentVisibilityDrawer.close();
    },
  }
};
</script>
