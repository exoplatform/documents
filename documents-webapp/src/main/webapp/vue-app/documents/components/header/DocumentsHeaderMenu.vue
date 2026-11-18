<!--

 Copyright (C) 2026 eXo Platform SAS.

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU Affero General Public License
 as published by the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 GNU Affero General Public License for more details.

 You should have received a copy of the GNU Affero General Public License
 along with this program. If not, see <gnu.org/licenses>.

-->
<template>
  <div v-if="displayed" class="documents-header-menu d-flex">
    <v-menu
      v-model="menu"
      :left="!$vuetify.rtl"
      offset-y>
      <template #activator="{on, attrs}">
        <v-btn
          id="documentsHeaderMenuButton"
          :title="$t('recycleBin.menu.more')"
          :aria-label="$t('recycleBin.menu.more')"
          small
          icon
          v-bind="attrs"
          v-on="on">
          <v-icon size="20">fa-ellipsis-v</v-icon>
        </v-btn>
      </template>
      <v-list class="pa-0" dense>
        <v-list-item id="documentsRecycleBinMenuItem" @click="$root.$emit('documents-recycle-bin-open', bin)">
          <v-icon size="16" class="me-2 icon-default-color">fa-trash</v-icon>
          <v-list-item-title>{{ $t('recycleBin.title') }}</v-list-item-title>
        </v-list-item>
      </v-list>
    </v-menu>
  </div>
</template>
<script>
export default {
  data: () => ({
    menu: false,
    ready: false,
    space: null,
  }),
  computed: {
    ownerId() {
      return this.$root.ownerId && String(this.$root.ownerId) || null;
    },
    // The space whose drive may be the one displayed: the space of the page, else the one the user browsed to.
    spaceId() {
      return eXo.env.portal.spaceId || this.$root.spaceId || null;
    },
    ownDrive() {
      return !eXo.env.portal.spaceId && !!this.ownerId && this.ownerId === String(eXo.env.portal.userIdentityId);
    },
    // The bin of the drive displayed, when the user holds it: their own, or
    // the one of a space they manage or publish in.
    bin() {
      if (this.ownDrive) {
        return {};
      }
      const spaceDrive = this.space && String(this.space.identityId) === this.ownerId;
      return spaceDrive && (this.space.isManager || this.space.isPublisher) && {spaceId: this.space.id} || null;
    },
    displayed() {
      return this.ready && !this.$root.driveView && !!this.bin;
    },
  },
  watch: {
    spaceId: {
      immediate: true,
      handler() {
        const spaceId = this.spaceId;
        this.space = null;
        if (spaceId) {
          this.$spaceService.getSpaceById(spaceId)
            .then(space => {
              if (String(this.spaceId) === String(spaceId)) {
                this.space = space;
              }
            })
            .catch(() => this.space = null);
        }
      },
    },
  },
  created() {
    this.$recycleBinService.loadLabels().then(() => this.ready = true);
  },
};
</script>
