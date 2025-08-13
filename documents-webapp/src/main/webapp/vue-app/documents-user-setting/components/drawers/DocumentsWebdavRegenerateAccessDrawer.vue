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
    id="documentsWebdavDrawer"
    :loading="loading"
    :right="!$vuetify.rtl"
    go-back-button>
    <template #title>
      {{ $t('UserSettings.documents.webdav.regenerateApiKey.title') }}
    </template>
    <template v-if="drawer" #content>
      <div class="d-flex flex-column full-width pa-5">
        <div>
          {{ $t('UserSettings.documents.webdav.regenerateApiKey.description') }}
        </div>
        <documents-credential-inputs
          :password="password" />
      </div>
    </template>
  </exo-drawer>
</template>
<script>
export default {
  data: () => ({
    drawer: false,
    password: null,
  }),
  methods: {
    open(password) {
      this.password = password;
      this.$refs.drawer.open();
    },
  },
};
</script>