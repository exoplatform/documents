<!--

 Copyright (C) 2025 eXo Platform SAS.

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU Affero General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU Affero General Public License for more details.

 You should have received a copy of the GNU Affero General Public License
 along with this program. If not, see <http://www.gnu.org/licenses/>.

-->
<template>
  <exo-drawer
    ref="drawer"
    v-model="drawer"
    id="documentsWebdavConfirmAccessDrawer"
    :loading="loading"
    :right="!$vuetify.rtl"
    go-back-button
    eager>
    <template #title>
      {{ $t('UserSettings.documents.webdav.confirmAccess.title') }}
    </template>
    <template #content>
      <documents-confirm-access-input
        ref="confirmAccessInput"
        :drawer="nextStepDrawer"
        :renew="renew"
        class="pa-5"
        @loading="loading = $event"
        @validated="close" />
    </template>
  </exo-drawer>
</template>
<script>
export default {
  data: () => ({
    drawer: false,
    loading: false,
    nextStepDrawer: null,
    renew: false,
  }),
  methods: {
    async open(nextStepDrawer, renew) {
      this.nextStepDrawer = nextStepDrawer;
      this.renew = renew;
      await this.$nextTick();
      if (this.$refs.confirmAccessInput.init()) {
        this.$refs.drawer.open();
      }
    },
    close() {
      this.$refs.drawer.close();
    },
  },
};
</script>