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
    <v-card
      v-if="recycleBinDisplayed"
      class="application-body px-4 pt-3 pb-4"
      flat>
      <documents-recycle-bin @back="closeRecycleBin" />
    </v-card>
    <div v-else class="application-body">
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
        <v-list-item v-if="recycleBinLabelsLoaded">
          <v-list-item-content>
            <v-list-item-title>
              {{ $t('recycleBin.userSettings.review') }}
            </v-list-item-title>
          </v-list-item-content>
          <v-list-item-action>
            <v-btn
              id="documentsUserSettingsRecycleBin"
              :title="$t('recycleBin.userSettings.review')"
              :aria-label="$t('recycleBin.userSettings.review')"
              small
              icon
              @click="openRecycleBin">
              <v-icon size="24" class="icon-default-color">
                {{ $vuetify.rtl && 'fa-caret-left' || 'fa-caret-right' }}
              </v-icon>
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
    recycleBinLabelsLoaded: false,
    recycleBinDisplayed: false,
  }),
  watch: {
    displayed() {
      this.$root.$updateApplicationVisibility(this.displayed);
    },
  },
  created() {
    this.$recycleBinService.loadLabels().then(() => this.recycleBinLabelsLoaded = true);
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
    // Another setting displays its detail in place of the page; this one stays when the detail is its own.
    hideSettingsApps(event) {
      if (event?.detail !== this.id) {
        this.displayed = false;
      }
    },
    showSettingsApps() {
      this.displayed = true;
    },
    openRecycleBin() {
      document.dispatchEvent(new CustomEvent('hideSettingsApps', {detail: this.id}));
      this.recycleBinDisplayed = true;
    },
    closeRecycleBin() {
      this.recycleBinDisplayed = false;
      document.dispatchEvent(new CustomEvent('showSettingsApps'));
    },
  },
};
</script>