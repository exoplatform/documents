<!--

  Copyright (C) 2003 - 2025 eXo Platform SAS.

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
  <v-tooltip v-if="offlineModeEnabled" bottom>
    <template #activator="{ on, attrs }">
      <div
        v-bind="attrs"
        v-on="on">
        <v-badge
          :value="hasDocmentsAccessedOffline"
          :offset-x="offset"
          :offset-y="offset"
          color="var(--allPagesBadgePrimaryColor, #d32a2a)"
          overlap
          flat
          dot>
          <v-btn
            id="offlineDocumentsButton"
            v-bind="tooltip ? {
              'aria-label': tooltipText,
            } : {
              'title': tooltipText,
            }"
            :small="small"
            :class="btnClass"
            icon
            @click="$root.$emit('open-document-offline-files', !noGoBackButton)">
            <v-icon size="20">fa-power-off</v-icon>
          </v-btn>
        </v-badge>
        <documents-offline-drawer />
      </div>
    </template>
    <span>{{ tooltipText }}</span>
  </v-tooltip>
</template>
<script>
export default {
  props: {
    tooltip: {
      type: Boolean,
      default: false,
    },
    small: {
      type: Boolean,
      default: false,
    },
    noGoBackButton: {
      type: Boolean,
      default: false,
    },
    btnClass: {
      type: String,
      default: null,
    },
  },
  data: () => ({
    offlineModeEnabled: false,
    hasDocmentsAccessedOffline: false,
  }),
  computed: {
    tooltipText() {
      return this.hasDocmentsAccessedOffline ? this.$t('OfflineApp.pwa.documents.uploadDocumentsTooltip') : this.$t('OfflineApp.pwa.documents.accessDocumentsTooltip');
    },
    offset() {
      return this.small ? 10 : 12;
    },
  },
  created() {
    this.$root.$on('documents-offline-updated', this.init);
    this.init();
  },
  beforeDestroy() {
    this.$root.$off('documents-offline-updated', this.init);
  },
  methods: {
    async init() {
      this.offlineModeEnabled = await this.$documentOfflineService.isDatabaseExists();
      this.hasDocmentsAccessedOffline = await this.$documentOfflineService.hasDocumentsAccessedOffline();
    },
  }
};
</script>
