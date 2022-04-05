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

      <v-list-item>
        <v-list-item-content class="my-1">
          <div class="my-4">
            <v-label for="choice">
              <span class="font-weight-bold text-start text-color body-2">{{ $t('documents.label.visibility.choice') + ' :' }}</span>
            </v-label>
            <v-select
              v-model="file.acl.visibilityChoice"
              :items="visibilityLabel"
              item-text="text"
              item-value="value"
              dense
              class="caption"
              outlined
              :hint="infoMessage"
              persistent-hint />
          </div>
          <div v-if="showSwitch" class="d-flex flex-row align-center my-4">
            <v-label for="visibility">
              <span class="text-color body-2">
                {{ $t('documents.label.visibility.allowEveryone') }}
              </span>
            </v-label>
            <v-spacer />
            <v-switch
              v-model="file.acl.allMembersCanEdit"
              class="mt-0 me-1" />
          </div>
        </v-list-item-content>
      </v-list-item>

      <v-divider dark class="mx-4" />

      <v-list-item>
        <v-list-item-content class="my-1">
          <div class="d-flex">
            <v-label for="collaborator">
              <span class="font-weight-bold text-start text-color body-2">{{ $t('documents.label.visibility.collaborator') }}</span>
            </v-label>
            <v-tooltip bottom v-if="!isMobile">
              <template v-slot:activator="{ on, attrs }">
                <v-icon
                  color="grey lighten-1"
                  dark
                  v-bind="attrs"
                  v-on="on"
                  size="16"
                  class="px-2 iconStyle"
                  @mouseenter="applyItemClass()">
                  fa-info-circle
                </v-icon>
              </template>
              <span class="center lotfi">{{ $t('documents.label.visibility.collaborator.info') }}</span>
            </v-tooltip>
          </div>
          <exo-identity-suggester
            ref="invitedCollaborators"
            :labels="suggesterLabels"
            v-model="collaborators"
            :search-options="searchOptions"
            name="collaborator"
            type-of-relations="user_to_invite"
            height="40"
            include-users
            include-spaces />
        </v-list-item-content>
      </v-list-item>
      <div v-if="users.length">
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
  
  data: () => ({
    ownerIdentity: [],
    file: { 'acl': {
      'visibilityChoice': 'ALL_MEMBERS'
    }},
    fileName: '',
    collaborators: [],
    searchOptions: {
      currentUser: '',
    },
    users: [],
  }),
  computed: {
    visibilityTitle(){
      return this.$t('documents.label.visibilityTitle').replace('{0}', this.fileName);
    },
    visibilityLabel(){
      return [
        {
          text: this.$t('documents.label.visibility.allMembers'),
          value: 'ALL_MEMBERS',
        },
        {
          text: this.$t('documents.label.visibility.specific'),
          value: 'SPECIFIC_COLLABORATOR',
        },
      ];
    },
    infoMessage(){
      switch (this.file.acl.visibilityChoice) {
      case 'SPECIFIC_COLLABORATOR':
        return this.$t('documents.label.visibility.user.info');
      case 'ALL_MEMBERS':
        return this.file.acl.allMembersCanEdit ? this.$t('documents.label.visibility.allMembers.info') : this.$t('documents.label.visibility.specific.info');
      default:
        return this.$t('documents.label.visibility.allMembers.info');
      }
    },
    showSwitch(){
      return this.file.acl.visibilityChoice === 'ALL_MEMBERS';
    },
    suggesterLabels() {
      return {
        searchPlaceholder: this.$t('documents.label.visibility.searchPlaceholder'),
        placeholder: this.$t('documents.label.visibility.placeholder'),
        noDataLabel: this.$t('documents.label.visibility.noDataLabel'),
      };
    },
    isMobile() {
      return this.$vuetify.breakpoint.name === 'xs' || this.$vuetify.breakpoint.name === 'sm';
    },
  },
  watch: {
    collaborators() {
      if (!this.collaborators) {
        this.$nextTick(this.$refs.invitedCollaborators.$refs.selectAutoComplete.deleteCurrentItem);
        return;
      }
      const found = this.users.find(user => {
        return user.remoteId === this.collaborators.remoteId
            && user.providerId === this.collaborators.providerId;
      });
      if (!found) {
        this.users.push(
          this.collaborators,
        );
      }
      this.collaborators = null;
    },
  },
  methods: {
    applyItemClass(){
      window.setTimeout(() => {
        const elements = document.getElementsByClassName('v-tooltip__content');
        for (let i = 0; i < elements.length; i++){
          if (elements[i].innerText.includes(this.$t('documents.label.visibility.collaborator.info'))){
            elements[i].style.left = '880px';
          }
        }      }, 100);

    },
    open(file,fileName) {
      this.file=file;
      this.fileName=fileName;
      this.$userService.getUser(this.file.creatorIdentity.remoteId).then(user => {
        this.ownerIdentity = user;
      });
      this.users = [];
      for (const collaborator of file.acl.collaborators){
        const user = collaborator.identity;
        user.permission = collaborator.permission;
        this.users.push(user);
      }
      this.$refs.documentVisibilityDrawer.open();
    },
    close() {
      this.$refs.documentVisibilityDrawer.close();
    },
    saveVisibility(){
      const collaborators = [];
      for (const user of  this.users) {
        const  collaborator= {
          'permission': user.permission || 'read',
          'identity': {
            'id': user.id,
            'name': user.displayName || user.name,
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
