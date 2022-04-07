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
  <exo-drawer
    ref="documentAllUsersVisibilityDrawer"
    class="documentAllUsersVisibilityDrawer"
    @closed="close"
    right>
    <template slot="title">
      <div class="d-flex">
        <v-icon
          size="16"
          color="grey lighten-1"
          class="clickable"
          @click="close()">
          fas fa-arrow-left
        </v-icon>
        <span class="ps-2">{{ specificCollaborators }}</span>
      </div>
    </template>
    <template slot="content">
      <div v-if="users.length" class="my-4">
        <documents-visibility-collaborators
          v-for="user in users"
          :key="user"
          :user="user"
          @remove-user="removeUser"
          @set-visibility="setUserVisibility" />
      </div>
    </template>
    <template slot="footer">
      <div class="d-flex">
        <v-spacer />
        <v-btn
          class="btn me-2"
          @click="close">
          {{ $t('documents.label.visibility.cancel') }}
        </v-btn>
        <v-btn
          class="btn btn-primary"
          @click="saveVisibility">
          {{ $t('documents.label.visibility.save') }}
        </v-btn>
      </div>
    </template>
  </exo-drawer>
</template>
<script>

export default {
  props: {
    users: {
      type: Array,
      default: () => [],
    },
  },
  data: () => ({
    ownerIdentity: [],
    file: { 'acl': {
      'visibilityChoice': 'ALL_MEMBERS'
    }},
  }),
  computed: {
    specificCollaborators(){
      return this.$t('documents.label.visibility.specificCollaborator');
    },
    isMobile() {
      return this.$vuetify.breakpoint.name === 'xs' || this.$vuetify.breakpoint.name === 'sm';
    },
  },
  methods: {
    open() {
      this.$refs.documentAllUsersVisibilityDrawer.open();
    },
    close() {
      this.$refs.documentAllUsersVisibilityDrawer.close();
    },
    saveVisibility(){
      const collaborators = [];
      for (const user of  this.users) {
        const  collaborator= {
          'permission': user.permission || 'read',
          'identity': {
            'id': user.id,
            'name': user.displayName || user.fullName,
            'remoteId': user.remoteId,
            'providerId': user.providerId,
          }
        };
        collaborators.push(collaborator);      }
      this.file.acl.collaborators=collaborators;
      if (this.file.acl.visibilityChoice==='SPECIFIC_COLLABORATOR'){
        this.file.acl.allMembersCanEdit=false;
      }
      this.$root.$emit('save-visibility',this.file);
      this.close();
    },
    removeUser(user) {
      const index = this.users.findIndex(addedUser => {
        return user.remoteId === addedUser.remoteId;
      });
      if (index >= 0) {
        this.users.splice(index, 1);
      }
    },
    setUserVisibility(user) {
      const index = this.users.findIndex(addedUser => {
        return user.remoteId === addedUser.remoteId;
      });
      if (index >= 0) {
        this.users[index].permission=user.permission;
      }
    },
  }
};
</script>
