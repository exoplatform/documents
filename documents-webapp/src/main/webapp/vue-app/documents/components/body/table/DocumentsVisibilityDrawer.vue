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
          <v-label for="collaborator">
            <span class="font-weight-bold text-start text-color body-2">{{ $t('documents.label.visibility.collaborator') }}</span>
          </v-label>
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
          <p class="text-sub-title text-left mb-0">
            <span class="caption">
              {{ $t('documents.label.visibility.collaborator.info') }}
            </span>
          </p>
        </v-list-item-content>
      </v-list-item>
      <div v-if="users.length">
        <documents-visibility-collaborators
          v-for="user in users"
          :key="user"
          :user="user"
          @remove-user="removeUser" @set-visibility="setUserVisibility" />
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
        return this.allowEveryone ? this.$t('documents.label.visibility.allMembers.info') : this.$t('documents.label.visibility.specific.info');
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
