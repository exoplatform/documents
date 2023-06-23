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
    <exo-drawer
      ref="documentVisibilityDrawer"
      class="documentVisibilityDrawer"
      @closed="close"
      right>
      <template slot="title">
        <span :title="visibilityTitle" class="text-truncate">{{ visibilityTitle }}</span>
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
                <span v-if="!file.folder" class="font-weight-bold text-start text-color body-2">{{ $t('documents.label.visibility.choice') + ' :' }}</span>
                <span v-else class="font-weight-bold text-start text-color body-2">{{ $t('documents.label.folders.visibility.choice') + ' :' }}</span>
              </v-label>
              <v-select
                v-model="file.acl.visibilityChoice"
                :items="visibilityLabel"
                item-text="text"
                item-value="value"
                dense
                class="caption"
                outlined />
            </div>
            <div v-if="showSwitch" class="d-flex flex-row my-4">
              <v-label for="visibility">
                <span class="text-color body-2 mr-6">
                  {{ $t('documents.label.visibility.allowEveryone') }}
                </span>
                <p class="caption"> {{ infoMessage }} </p>
              </v-label>
              <v-spacer />
              <v-switch
                v-model="file.acl.allMembersCanEdit"
                class="mt-0 me-1" />
            </div>
          </v-list-item-content>
        </v-list-item>

        <v-divider dark class="mx-4" />

        <v-list-item class="d-block">
          <v-list-item-content class="my-1">
            <div class="d-flex">
              <v-label for="collaborator">
                <span class="font-weight-bold text-start text-color body-2">{{ $t('documents.label.visibility.collaborator') }}</span>
              </v-label>
              <v-tooltip bottom v-if="!isMobile">
                <template #activator="{ on, attrs }">
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
                <span v-if="!file.folder" class="center lotfi">{{ $t('documents.label.visibility.collaborator.info') }}</span>
                <span v-else class="center lotfi">{{ $t('documents.label.folders.visibility.collaborator.info') }}</span>
              </v-tooltip>
            </div>
          </v-list-item-content>
          <exo-identity-suggester
            ref="invitedCollaborators"
            :labels="suggesterLabels"
            v-model="collaborators"
            :search-options="searchOptions"
            :ignore-items="ignoreItems"
            name="collaborator"
            type-of-relations="user_to_invite"
            height="40"
            :group-member="userGroup"
            :group-type="groupType"
            :all-groups-for-admin="allGroupsForAdmin"
            include-users
            include-spaces
            include-groups />
        </v-list-item>
        <div v-if="users.length">
          <documents-visibility-collaborators
            v-for="user in usersToDisplay"
            :key="user"
            :user="user"
            :is-mobile="isMobile" 
            @remove-user="removeUser"
            @set-visibility="setUserVisibility" />
          <div class="seeMoreUsers">
            <div
              v-if="users.length > maxUsersToShow"
              class="seeMoreItem  clickable center "
              @click="displayAllListUsers()">
              <span class="seeMoreUsersList text-sub-title clickable">+{{ showMoreUsersNumber }}</span>
            </div>
          </div>
        </div>
      </template>
      <template slot="footer">
        <div class="d-flex">
          <v-spacer />
          <v-btn
            class="btn me-2"
            @click="close()">
            {{ $t('documents.label.visibility.cancel') }}
          </v-btn>
          <v-btn
            class="btn btn-primary"
            :loading="loading"
            @click="saveVisibility()">
            {{ $t('documents.label.visibility.save') }}
          </v-btn>
        </div>
      </template>
    </exo-drawer>
    <documents-visibility-all-users-drawer
      ref="documentAllUsersVisibilityDrawer"
      :users="users"
      :is-mobile="isMobile" />
  </div>
</template>
<script>
export default {
  props: {
    isMobile: {
      type: Boolean,
      default: false
    }
  },
  data: () => ({
    loading: false,
    allGroupsForAdmin: true,
    userGroup: '/platform/users',
    groupType: 'GROUP',
    ownerIdentity: {
      id: 'system',
      providerId: 'system',
      remoteId: 'system',
      name: 'system',
      fullname: 'System',
      avatar: '/portal/rest/v1/social/users/default-image/avatar',
    },
    file: { 'acl': {
      'visibilityChoice': 'ALL_MEMBERS'
    }},
    collaborators: [],
    searchOptions: {
      currentUser: '',
    },
    users: [],
  }),
  computed: {
    ignoreItems() {
      return eXo.env.portal.spaceName && [`space:${eXo.env.portal.spaceName}`] || [];
    },
    visibilityTitle(){
      return this.$t('documents.label.visibilityTitle', {0: this.file?.name});
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
        if (!this.file.folder) {
          return this.$t('documents.label.visibility.user.info');
        } else {
          return this.$t('documents.label.folders.visibility.user.info');
        }
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
    maxUsersToShow(){
      return this.$vuetify.breakpoint.width < 1600 ? 2 : 4;
    },
    usersToDisplay () {
      if (this.users.length > this.maxUsersToShow) {
        return this.users.slice(0, this.maxUsersToShow);
      } else {
        return this.users;
      }
    },
    showMoreUsersNumber() {
      return `${this.users.length - this.maxUsersToShow  } ${  this.$t('documents.label.visibility.others')}`;
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
      if (!found && this.collaborators.id !== this.ignoreItems[0]) {
        this.users.push(
          this.mapCollaborator(this.collaborators),
        );
      }
      this.collaborators = null;
    },
  },
  created() {
    this.$root.$on('open-visibility-drawer', file => {
      this.open(file);
    });
    this.$root.$on('visibility-saved', () => {
      this.loading = false;
      this.$refs.documentVisibilityDrawer.endLoading();
      this.close();
    });
  },
  methods: {
    mapCollaborator(collaborator) {
      const fullName = collaborator.profile
          && collaborator.profile.fullName
          && collaborator.profile.fullName.substring(0, collaborator.profile.fullName.lastIndexOf(' ('));
      return {
        'permission': collaborator.permission || 'read',
        'id': collaborator.id,
        'profile': {
          'fullName': fullName,
        },
        'name': collaborator.displayName || fullName,
        'remoteId': collaborator.providerId === 'group' ? collaborator.spaceId : collaborator.remoteId,
        'providerId': collaborator.providerId,
        'avatar': collaborator.profile.avatarUrl

      };
    },
    applyItemClass(){
      window.setTimeout(() => {
        const elements = document.getElementsByClassName('v-tooltip__content');
        for (let i = 0; i < elements.length; i++){
          if (elements[i].innerText.includes(this.$t('documents.label.visibility.collaborator.info'))){
            elements[i].style.left = '880px';
          }
        }
      }, 100);
    },
    open(file) {
      this.file = file;
      if (this.file?.creatorIdentity?.remoteId){
        this.$userService.getUser(this.file.creatorIdentity.remoteId).then(user => {
          this.ownerIdentity = user;
        });
      }
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
    displayAllListUsers(){
      this.$root.$emit('open-all-users-visibility-drawer', this.file);
    },
    saveVisibility(){
      this.loading = true;
      this.$refs.documentVisibilityDrawer.startLoading();
      const collaborators = [];
      for (const user of  this.users) {
        const  collaborator= {
          'permission': user.permission || 'read',
          'identity': {
            'id': user.id,
            'name': user.name,
            'profile': {
              'fullName': user.fullName,
            },
            'remoteId': user.remoteId,
            'avatar': user.avatar,
            'providerId': user.providerId,
          }
        };
        if (user.groupId) {
          collaborator.identity.groupId = user.groupId;
        }
        collaborators.push(collaborator);      }
      this.file.acl.collaborators=collaborators;
      if (this.file.acl.visibilityChoice==='SPECIFIC_COLLABORATOR'){
        this.file.acl.allMembersCanEdit=false;
      }
      this.$root.$emit('save-visibility',this.file);
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
