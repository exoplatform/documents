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
              v-model="visibilitySelected"
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
              v-model="allowEveryone"
              class="mt-0 me-1" />
          </div>
        </v-list-item-content>
      </v-list-item>

      <v-divider dark class="mx-4" />
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
    visibilitySelected: 'allMembers',
    allowEveryone: true,
  }),
  computed: {
    visibilityTitle(){
      return this.$t('documents.label.visibilityTitle').replace('{0}', this.fileName);
    },
    visibilityLabel(){
      return [
        {
          text: this.$t('documents.label.visibility.allMembers'),
          value: 'allMembers',
        },
        {
          text: this.$t('documents.label.visibility.specific'),
          value: 'specific',
        },
      ];
    },
    infoMessage(){
      switch (this.visibilitySelected) {
      case 'specific':
        return this.$t('documents.label.visibility.user.info');
      case 'allMembers':
        return this.allowEveryone ? this.$t('documents.label.visibility.allMembers.info') : this.$t('documents.label.visibility.specific.info');
      default:
        return this.$t('documents.label.visibility.allMembers.info');
      }
    },
    showSwitch(){
      return this.visibilitySelected === 'allMembers';
    }
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
    saveVisibility(){
      this.$root.$emit('save-visibility');
      this.close();
    }
  }
};
</script>
