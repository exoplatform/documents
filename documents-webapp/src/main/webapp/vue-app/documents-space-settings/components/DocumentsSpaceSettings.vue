<!--

  Copyright (C) 2026 eXo Platform SAS.

  This program is free software; you can redistribute it and/or
  modify it under the terms of the GNU Affero General Public License
  as published by the Free Software Foundation; either version 3
  of the License, or (at your option) any later version.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with this program; if not, see<http://www.gnu.org/licenses/>.

-->
<template>
  <v-card
    v-if="canManageSettings"
    id="DocumentsSpaceSettings"
    flat>
    <div class="text-title py-1">
      {{ $t('documents.spaceSettings.title') }}
    </div>
    <v-list class="pa-0" dense>
      <v-list-item class="pa-0">
        <v-list-item-content>
          <v-list-item-title class="py-1">
            {{ $t('documents.spaceSettings.limitAccessManagement') }}
          </v-list-item-title>
          <v-list-item-subtitle class="pt-0 pb-1 text-wrap">
            {{ $t('documents.spaceSettings.limitAccessManagement.description') }}
          </v-list-item-subtitle>
        </v-list-item-content>
        <v-list-item-action>
          <v-switch
            id="DocumentsSpaceSettingLimitAccessManagement"
            v-model="limitAccessManagement"
            :aria-label="$t('documents.spaceSettings.limitAccessManagement')"
            class="ma-0"
            @change="saveSetting('limitAccessManagement', $event)" />
        </v-list-item-action>
      </v-list-item>
      <v-list-item class="pa-0">
        <v-list-item-content>
          <v-list-item-title class="py-1">
            {{ $t('documents.spaceSettings.keepDocumentsInside') }}
          </v-list-item-title>
          <v-list-item-subtitle class="pt-0 pb-1 text-wrap">
            {{ $t('documents.spaceSettings.keepDocumentsInside.description') }}
          </v-list-item-subtitle>
        </v-list-item-content>
        <v-list-item-action>
          <v-switch
            id="DocumentsSpaceSettingKeepDocumentsInside"
            v-model="keepDocumentsInside"
            :aria-label="$t('documents.spaceSettings.keepDocumentsInside')"
            class="ma-0"
            @change="saveSetting('keepDocumentsInside', $event)" />
        </v-list-item-action>
      </v-list-item>
    </v-list>
  </v-card>
</template>
<script>
export default {
  props: {
    spaceId: {
      type: [String, Number],
      default: null,
    },
  },
  data: () => ({
    canManageSettings: false,
    limitAccessManagement: false,
    keepDocumentsInside: false,
  }),
  created() {
    // not reactive: the saves still to send, in click order; the count of
    // failed saves, each of which drops the saves queued before it ended; and
    // the count of clicks, so that a stored value never overwrites a later click
    this.saveQueue = Promise.resolve();
    this.failedSaves = 0;
    this.clicks = 0;
    this.retrieveSettings();
  },
  methods: {
    retrieveSettings() {
      const clicks = this.clicks;
      return this.$spaceSecuritySettingsService.getSpaceSecuritySettings(this.spaceId)
        .then(settings => this.applySettings(settings, clicks))
        .catch(() => this.canManageSettings = false);
    },
    // field: the setting a toggle changes, sent alone, whichever toggle the
    // user clicked; both toggles share one queue
    saveSetting(field, value) {
      const failedSaves = this.failedSaves;
      const clicks = ++this.clicks;
      this.saveQueue = this.saveQueue.then(() => {
        if (failedSaves !== this.failedSaves) {
          return;
        }
        return this.$spaceSecuritySettingsService.updateSpaceSecuritySettings(this.spaceId, {[field]: value})
          .then(settings => {
            this.applySettings(settings, clicks);
            this.$root.$emit('alert-message', this.$t('documents.spaceSettings.saved'), 'success');
          })
          .catch(error => {
            // the toggles show the stored values again, unless clicked since
            this.failedSaves++;
            this.$root.$emit('alert-message', this.$t(this.errorLabelKey(error)), 'error');
            return this.retrieveSettings();
          });
      });
      return this.saveQueue;
    },
    // clicks: the count of clicks when the settings were read; a later click,
    // on either toggle, keeps both toggles as they are: every answer carries
    // both settings, so the later click's own answer sets them
    applySettings(settings, clicks) {
      this.canManageSettings = !!settings?.canManageSettings;
      if (clicks === this.clicks) {
        this.limitAccessManagement = !!settings?.limitAccessManagement;
        this.keepDocumentsInside = !!settings?.keepDocumentsInside;
      }
    },
    errorLabelKey(error) {
      switch (error?.code) {
      case 'space.security.notAllowed':
        return 'documents.spaceSettings.error.notAllowed';
      case 'space.concurrentUpdate':
        return 'documents.spaceSettings.error.concurrentUpdate';
      default:
        break;
      }
      switch (error?.status) {
      case 403:
        return 'documents.spaceSettings.error.notAllowed';
      case 409:
        return 'documents.spaceSettings.error.concurrentUpdate';
      default:
        return 'documents.spaceSettings.error.notSaved';
      }
    },
  },
};
</script>
