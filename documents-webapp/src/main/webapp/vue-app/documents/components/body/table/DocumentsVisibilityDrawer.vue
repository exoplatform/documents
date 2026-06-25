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
      <template #title>
        <span class="text-truncate">{{ $t('documents.label.visibility') }}</span>
      </template>
      <template #content>
        <v-list-item>
          <v-list-item-content class="my-1">
            <div class="d-flex align-center">
              <v-icon
                size="28"
                class="me-3"
                :color="fileIconColor">
                {{ fileIconClass }}
              </v-icon>
              <span class="text-truncate text-subtitle-1 font-weight-bold">{{ file.name }}</span>
            </div>
          </v-list-item-content>
        </v-list-item>
        <div class="px-4 pt-2">
          <p class="text-header text-sub-title mb-3">
            {{ $t('documents.label.document.access.header') }}
          </p>
          <div class="d-flex flex-row align-center mt-2">
            <v-label>
              <span class="text-body mr-2">{{ $t('documents.label.owner.display') }}:</span>
            </v-label>
            <exo-user-avatar
              :identity="ownerIdentity"
              fullname
              bold-title
              link-style />
          </div>
          <div class="mt-4">
            <v-label for="visibility">
              <span class="text-body font-weight-bold">{{ $t('documents.label.who.can.view') }}</span>
            </v-label>
            <v-select
              v-model="visibilityChoice"
              :items="visibilityLabel"
              item-text="text"
              item-value="value"
              dense
              class="caption pt-3"
              outlined />
            <p class="text-subtitle text-break mb-0">
              {{ choiceInfo }}
            </p>
          </div>
          <div v-if="showEditSwitch" class="mt-4">
            <v-label for="edit">
              <span class="text-body font-weight-bold">{{ $t('documents.label.who.can.edit') }}</span>
            </v-label>
            <div class="d-flex flex-row align-center mt-2">
              <v-label>
                <span class="text-body mr-6">{{ $t('documents.label.visibility.allowEveryone') }}</span>
              </v-label>
              <v-spacer />
              <v-switch
                v-model="allMembersCanEdit"
                class="mt-0 me-1" />
            </div>
          </div>
        </div>
        <div class="pa-4">
          <p class="text-header text-sub-title mb-3">
            {{ $t('documents.label.external.access.header') }}
          </p>
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
          <div v-if="users.length" class="mt-2">
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
                class="seeMoreItem clickable center"
                @click="displayAllListUsers()">
                <span class="seeMoreUsersList text-sub-title clickable">+{{ showMoreUsersNumber }}</span>
              </div>
            </div>
          </div>
        </div>
        <v-divider dark class="mx-4" />
        <div class="pa-4">
          <div class="d-flex">
            <p class="text-body font-weight-bold">
              {{ $t('documents.label.public.link.header') }}
            </p>
            <v-spacer />
            <v-switch
              v-model="publicLinkEnabled"
              class="mt-0 me-1" />
          </div>
          <template v-if="publicLinkEnabled">
            <public-document-options
              ref="publicDocumentOptions"
              :file="file"
              :existing-public-access="existingPublicAccess"
              @change="onPublicOptionsChange" />
          </template>
        </div>
      </template>
      <template #footer>
        <div class="d-flex">
          <v-spacer />
          <v-btn
            class="btn me-2"
            @click="close()">
            {{ $t('documents.label.visibility.cancel') }}
          </v-btn>
          <v-btn
            class="btn btn-primary"
            :disabled="isDisabled"
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
    file: {
      acl: {
        visibilityChoice: 'ALL_MEMBERS'
      }
    },
    icon: null,
    collaborators: [],
    searchOptions: {
      currentUser: '',
    },
    originalFileProperties: {
      visibilityChoice: '',
      publicLinkEnabled: false,
      publicLinkPassword: null,
      publicLinkExpiration: 0,
      allMembersCanEdit: false,
      collaborators: '',
    },
    actualFileProperties: {
      visibilityChoice: '',
      publicLinkEnabled: false,
      publicLinkPassword: null,
      publicLinkExpiration: 0,
      allMembersCanEdit: false,
      collaborators: '',
    },
    users: [],
    existingPublicAccess: null,
    visibilityChoice: null,
    allMembersCanEdit: false,
    publicLinkEnabled: false,
    hasPassword: false,
    showPasswordInput: false,
    showPassword: false,
    showConfirmPassword: false,
    confirmPassword: null,
    publicLinkPassword: null,
    hasExpirationDate: false,
    expirationDate: null,
    expirationDateMenu: false,
    publicLinkOptionsChanged: false,
    publicOptionsValid: false,
    lang: eXo.env.portal.language,
  }),
  computed: {
    fileIconClass() {
      return this.icon?.class;
    },
    fileIconColor() {
      return this.icon?.color;
    },
    ignoreItems() {
      return eXo.env.portal.spaceName && [`space:${eXo.env.portal.spaceName}`] || [];
    },
    choiceInfo() {
      switch (this.visibilityChoice) {
      case 'SPECIFIC_COLLABORATOR':
        return eXo.env.portal.spaceGroup ? this.$t('document.visibility.collaborators.choice.info') :
          this.$t('document.myDrive.visibility.collaborators.choice.info');
      case 'ALL_MEMBERS':
        return this.$t('document.visibility.allMembers.choice.info');
      case 'ANYONE':
        return eXo.env.portal.spaceGroup ? this.$t('documents.visibility.anyone.choice.info') :
          this.$t('documents.myDrive.visibility.anyone.choice.info');
      default:
        return this.$t('document.visibility.collaborators.choice.info');
      }
    },
    visibilityLabel() {
      const labels = [];
      if (eXo?.env?.portal?.spaceGroup) {
        labels.push({
          text: this.$t('documents.label.visibility.allMembers'),
          value: 'ALL_MEMBERS',
        });
      }
      labels.push({
        text: this.$t('documents.label.visibility.specific'),
        value: 'SPECIFIC_COLLABORATOR',
      });
      labels.push({
        text: eXo.env.portal.spaceGroup ? this.$t('documents.label.visibility.anyone') :
          this.$t('documents.myDrive.label.visibility.anyone'),
        value: 'ANYONE',
      });
      return labels;
    },
    showEditSwitch() {
      return eXo?.env?.portal?.spaceGroup && ['ALL_MEMBERS', 'ANYONE'].includes(this.visibilityChoice);
    },
    suggesterLabels() {
      return {
        searchPlaceholder: this.$t('documents.label.visibility.searchPlaceholder'),
        placeholder: this.$t('documents.label.visibility.placeholder'),
        noDataLabel: this.$t('documents.label.visibility.noDataLabel'),
      };
    },
    maxUsersToShow() {
      return this.$vuetify.breakpoint.width < 1600 ? 2 : 4;
    },
    usersToDisplay() {
      if (this.users.length > this.maxUsersToShow) {
        return this.users.slice(0, this.maxUsersToShow);
      } else {
        return this.users;
      }
    },
    showMoreUsersNumber() {
      return `${this.users.length - this.maxUsersToShow} ${this.$t('documents.label.visibility.others')}`;
    },
    isDisabled() {
      const noChanges = this.originalFileProperties.visibilityChoice === this.visibilityChoice
        && this.originalFileProperties.publicLinkEnabled === this.publicLinkEnabled
        && this.originalFileProperties.allMembersCanEdit === this.allMembersCanEdit
        && this.originalFileProperties.collaborators === this.stringifyArray(this.users)
        && !this.publicLinkOptionsChanged;
      const invalid = this.publicLinkEnabled && this.publicLinkOptionsChanged && !this.publicOptionsValid;
      return noChanges || invalid;
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
    visibilityChoice(newVal) {
      this.actualFileProperties = { ...this.actualFileProperties, visibilityChoice: newVal };
    },
    publicLinkEnabled(newVal) {
      this.actualFileProperties = { ...this.actualFileProperties, publicLinkEnabled: newVal };
    },
    allMembersCanEdit(newVal) {
      this.file.acl.allMembersCanEdit = newVal;
      this.actualFileProperties = { ...this.actualFileProperties, allMembersCanEdit: newVal };
    },
    hasPassword() {
      if (!this.hasPassword) {
        this.publicLinkPassword = null;
        this.confirmPassword = null;
        this.showPasswordInput = false;
      }
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
    getDocumentPublicAccessInfo() {
      if (this.file?.acl?.visibilityChoice !== 'COLLABORATORS_AND_PUBLIC_ACCESS') {
        this.resetPublicAccessState();
        this.openDrawer();
        return;
      }
      this.$documentFileService.getDocumentPublicAccess(this.file.id)
        .then(this.onPublicAccessLoaded)
        .catch(this.onPublicAccessLoadError);
    },
    onPublicAccessLoaded(publicDocumentAccess) {
      const hasLink = publicDocumentAccess && publicDocumentAccess.nodeId;
      this.publicLinkEnabled = hasLink;
      this.hasPassword = hasLink && publicDocumentAccess.hasPassword;
      this.hasExpirationDate = hasLink && !!publicDocumentAccess.expirationDate;
      if (hasLink && publicDocumentAccess.expirationDate) {
        const expDate = new Date(publicDocumentAccess.expirationDate.time);
        this.expirationDate = expDate.toISOString().slice(0, 10);
      }
      this.existingPublicAccess = publicDocumentAccess;
      this.originalFileProperties = {
        ...this.originalFileProperties,
        visibilityChoice: this.visibilityChoice,
        publicLinkEnabled: hasLink,
        allMembersCanEdit: this.allMembersCanEdit,
        collaborators: this.stringifyArray(JSON.parse(JSON.stringify(this.users))),
        publicLinkPassword: publicDocumentAccess?.decodedPassword || null,
        publicLinkExpiration: publicDocumentAccess?.expirationDate?.time || 0,
      };
      this.actualFileProperties = { ...this.originalFileProperties };
      this.openDrawer();
    },
    onPublicAccessLoadError() {
      this.resetPublicAccessState();
      this.openDrawer();
    },
    resetPublicAccessState() {
      this.publicLinkEnabled = false;
      this.hasPassword = false;
      this.hasExpirationDate = false;
      this.expirationDate = null;
      this.existingPublicAccess = null;
      this.originalFileProperties = {
        ...this.originalFileProperties,
        visibilityChoice: this.visibilityChoice,
        publicLinkEnabled: false,
        allMembersCanEdit: this.allMembersCanEdit,
        collaborators: this.stringifyArray(JSON.parse(JSON.stringify(this.users))),
      };
      this.actualFileProperties = { ...this.originalFileProperties };
    },
    openDrawer() {
      this.$refs.documentVisibilityDrawer.open();
    },
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
    open(file) {
      this.file = file;
      this.getFileIcon();
      this.allMembersCanEdit = this.file.acl.allMembersCanEdit;
      this.visibilityChoice = this.file.acl.visibilityChoice === 'COLLABORATORS_AND_PUBLIC_ACCESS' ? 'ANYONE' : this.file.acl.visibilityChoice;
      this.publicLinkEnabled = false;
      this.hasPassword = false;
      this.showPasswordInput = false;
      this.publicLinkPassword = null;
      this.confirmPassword = null;
      this.hasExpirationDate = false;
      this.expirationDate = null;
      this.publicLinkOptionsChanged = false;
      this.existingPublicAccess = null;
      if (this.file?.creatorIdentity?.remoteId) {
        this.$userService.getUser(this.file.creatorIdentity.remoteId).then(user => {
          this.ownerIdentity = user;
        });
      }
      this.users = [];
      for (const collaborator of file.acl.collaborators) {
        const user = collaborator.identity;
        user.permission = collaborator.permission;
        this.users.push(user);
      }
      this.getDocumentPublicAccessInfo();
    },
    close() {
      this.$refs.documentVisibilityDrawer.close();
    },
    displayAllListUsers() {
      this.$root.$emit('open-all-users-visibility-drawer', this.file);
    },
    onPublicOptionsChange(valid) {
      this.publicLinkOptionsChanged = true;
      this.publicOptionsValid = valid;
    },
    saveVisibility() {
      const publicOptions = this.$refs.publicDocumentOptions?.getAccessOptions();
      if (publicOptions === null) {
        return;
      }
      this.loading = true;
      this.$refs.documentVisibilityDrawer.startLoading();
      const collaborators = [];
      for (const user of this.users) {
        const collaborator = {
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
        collaborators.push(collaborator);
      }
      this.file.acl.collaborators = collaborators;
      if (this.visibilityChoice === 'SPECIFIC_COLLABORATOR') {
        this.file.acl.allMembersCanEdit = false;
      }
      this.file.acl.visibilityChoice = this.visibilityChoice;
      this.$root.$emit('save-visibility', this.file, this.publicLinkEnabled, publicOptions || {});
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
        this.users[index].permission = user.permission;
      }
      this.actualFileProperties = { ...this.actualFileProperties, collaborators: this.stringifyArray(this.usersToDisplay) };
    },
    stringifyArray(collaborators) {
      if (!collaborators) {
        return '';
      }
      collaborators = JSON.parse(JSON.stringify(collaborators));
      collaborators = collaborators
        .map(c => {
          const { id, permission, remoteId, providerId } = c;
          return JSON.stringify({ id, permission, remoteId, providerId });
        });
      return JSON.stringify(collaborators.sort());
    },
    getFileIcon() {
      const extensions = this.$documentsIconsExtension;
      if (this.file?.folder) {
        this.icon = extensions[0].get('folder');
      } else {
        let extension = extensions[0].get(this.file?.mimeType);
        if (!extension) {
          extension = extensions[0].get('file');
        }
        this.icon = extension;
      }
    },
  }
};
</script>