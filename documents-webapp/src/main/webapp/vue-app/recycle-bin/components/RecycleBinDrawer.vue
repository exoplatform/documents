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
  <exo-drawer
    :id="drawerId"
    ref="drawer"
    v-model="drawer"
    :right="!$vuetify.rtl"
    expanded
    disable-pull-to-refresh
    @closed="$emit('closed')">
    <template v-if="drawer && drive" #content>
      <documents-recycle-bin
        :owner-identity-id="drive.ownerIdentityId"
        :opened-drive="drive"
        scope="DRIVE"
        class="pa-5"
        @back="close"
        @changed="$emit('changed')" />
    </template>
  </exo-drawer>
</template>
<script>
export default {
  props: {
    drawerId: {
      type: String,
      default: 'recycleBinDriveDrawer',
    },
  },
  data: () => ({
    drawer: false,
    drive: null,
  }),
  methods: {
    // Opens, for an administrator, the bin of a drive of the Bin Overview:
    // {ownerIdentityId, name, avatarUrl, type}. Its back arrow closes it.
    open(drive) {
      this.drive = Object.assign({}, drive, {ownerIdentityId: String(drive.ownerIdentityId)});
      this.$refs.drawer.open();
    },
    close() {
      this.$refs.drawer.close();
    },
  },
};
</script>
