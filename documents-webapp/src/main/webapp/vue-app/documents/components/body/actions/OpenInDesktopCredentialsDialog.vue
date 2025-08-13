<!--

  Copyright (C) 2025 eXo Platform SAS

  This program is free software: you can redistribute it and/or modify
  it under the terms of the GNU Affero General Public License as published by
  the Free Software Foundation, either version 3 of the License, or
  (at your option) any later version.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU Affero General Public License
  along with this program.  If not, see <gnu.org/licenses>.

-->
<template>
  <v-dialog
    ref="dialog"
    v-model="dialog"
    :loading="loading"
    content-class="uiPopup"
    max-width="100vw"
    width="420"
    persistent>
    <v-card v-if="dialog" class="elevation-12 transparent">
      <div class="ignore-vuetify-classes popupHeader transparent ClearFix">
        <v-btn
          class="pull-right"
          icon
          small
          @click="close">
          <v-icon size="16">fa-times</v-icon>
        </v-btn>
        <span class="ignore-vuetify-classes text-title">
          {{ $t('documents.label.openInDesktop.dialog.title') }}
        </span>
      </div>
      <v-card-text>
        <template v-if="password">
          <div>
            {{ $t('documents.label.openInDesktop.dialog.description') }}
          </div>
          <documents-credential-inputs
            :password="password" />
        </template>
        <documents-confirm-access-input
          v-else
          ref="confirmAccessInput"
          @loading="loading = $event"
          @validated="password = $event" />
      </v-card-text>
      <v-card-actions v-if="password">
        <v-spacer />
        <v-btn
          :loading="computing"
          :diabled="!href"
          :href="href"
          class="btn btn-primary me-2"
          @click="openFile">
          {{ $t('documents.label.openInDesktop.dialog.openFile') }}
        </v-btn>
        <v-btn
          class="btn ms-2"
          @click="close">
          {{ $t('documents.label.openInDesktop.dialog.cancel') }}
        </v-btn>
        <v-spacer />
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>
<script>
export default {
  data: () => ({
    dialog: false,
    loading: false,
    computing: false,
    protocol: null,
    relativePath: null,
    identityId: null,
    password: null,
  }),
  computed: {
    href() {
      return this.relativePath && this.identityId && this.protocol ? `${this.protocol}${window.origin}/webdav/drives/(${this.identityId})/${this.relativePath}` : null;
    },
  },
  watch: {
    dialog() {
      if (this.dialog) {
        document.dispatchEvent(new CustomEvent('modalOpened'));
      } else {
        document.dispatchEvent(new CustomEvent('modalClosed'));
      }
      this.$emit('input', this.dialog);
    },
  },
  created() {
    this.$root.$on('open-in-desktop-dialog', this.open);
  },
  beforeDestroy() {
    this.$root.$off('open-in-desktop-dialog', this.open);
  },
  methods: {
    async open(protocol, path) {
      this.protocol = protocol;
      this.identityId = null;
      this.relativePath = null;
      this.dialog = true;
      await this.$nextTick();
      if (this.$refs.confirmAccessInput) {
        this.$refs.confirmAccessInput.init();
      }
      await this.computePath(path);
    },
    async computePath(path) {
      this.computing = true;
      try {
        let providerId;
        let remoteId;
        if (path.startsWith('/Groups/spaces/')) {
          providerId = 'space';
          const groupName = path.replace('/Groups/spaces/', '').split('/').shift();
          const space = await this.$spaceService.getSpaceByGroupId(`/spaces/${groupName}`);
          remoteId = space.prettyName;
          this.relativePath = path.replace(`/Groups/spaces/${groupName}/Documents/`, '');
        } else {
          providerId = 'organization';
          remoteId = eXo.env.portal.userName;
          this.relativePath = path.substring(path.indexOf(`/${eXo.env.portal.userName}/`) + `/${eXo.env.portal.userName}/Private/`.length);
        }
        const identity = await this.$identityService.getIdentityByProviderIdAndRemoteId(providerId, remoteId);
        this.identityId = identity.id;
      } finally {
        this.computing = false;
      }
    },
    close() {
      this.dialog = false;
    },
  },
};
</script>