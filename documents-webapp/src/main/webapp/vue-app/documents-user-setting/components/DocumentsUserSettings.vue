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
  <v-app>
    <div class="application-body">
      <v-list>
        <v-list-item>
          <v-list-item-content>
            <v-list-item-title class="text-title">
              {{ $t('UserSettings.documents.title') }}
            </v-list-item-title>
          </v-list-item-content>
        </v-list-item>
        <v-list-item>
          <v-list-item-content>
            <v-list-item-title>
              {{ $t('UserSettings.documents.webdav.title') }}
            </v-list-item-title>
          </v-list-item-content>
          <v-list-item-action>
            <v-btn
              small
              icon
              @click="$refs.webdavDrawer.open()">
              <v-icon size="18">fa-edit</v-icon>
            </v-btn>
          </v-list-item-action>
        </v-list-item>
      </v-list>
      <documents-webdav-drawer
        ref="webdavDrawer" />
    </div>
  </v-app>
</template>
<script>
export default {
  data: () => ({
    id: `Settings${parseInt(Math.random() * 10000)
      .toString()
      .toString()}`,
    displayed: true,
  }),
  watch: {
    displayed() {
      this.$root.$updateApplicationVisibility(this.displayed);
    },
  },
  created() {
    document.addEventListener('showSettingsApps', this.showSettingsApps);
    document.addEventListener('hideSettingsApps', this.hideSettingsApps);
  },
  mounted() {
    this.$root.$updateApplicationVisibility(this.displayed);
  },
  beforeDestroy() {
    document.removeEventListener('showSettingsApps', this.showSettingsApps);
    document.removeEventListener('hideSettingsApps', this.hideSettingsApps);
  },
  methods: {
    hideSettingsApps() {
      this.displayed = false;
    },
    showSettingsApps() {
      this.displayed = true;
    },
  },
};
</script>