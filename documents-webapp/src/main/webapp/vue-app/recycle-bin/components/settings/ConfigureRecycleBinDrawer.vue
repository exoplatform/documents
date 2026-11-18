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
  <div>
    <exo-drawer
      id="configureRecycleBinDrawer"
      ref="drawer"
      v-model="drawer"
      :right="!$vuetify.rtl"
      :loading="saving">
      <template #title>{{ $t('recycleBin.configure.title') }}</template>
      <template v-if="drawer" #content>
        <div class="pa-4 text-start">
          <div class="text-header pb-3">{{ $t('recycleBin.configure.general') }}</div>
          <div class="d-flex align-center pb-6">
            <div class="flex-grow-1 font-weight-bold text-start pe-2">{{ $t('recycleBin.configure.retention.label') }}</div>
            <number-input
              id="recycleBinRetentionDays"
              v-model="retentionDays"
              :min="minDays"
              :max="maxDays"
              :step="1"
              :label="$t('recycleBin.configure.retention.label')"
              :minus-title="$t('recycleBin.configure.retention.decrease')"
              :plus-title="$t('recycleBin.configure.retention.increase')"
              editable
              @valid="inRange = $event" />
            <div class="ps-2">{{ $t('recycleBin.configure.retention.unit') }}</div>
          </div>
        </div>
      </template>
      <template #footer>
        <div class="d-flex">
          <v-spacer />
          <v-btn
            id="configureRecycleBinCancel"
            :disabled="saving"
            class="btn me-2"
            @click="close">
            {{ $t('recycleBin.button.cancel') }}
          </v-btn>
          <v-btn
            id="configureRecycleBinApply"
            :disabled="!valid || saving"
            :loading="saving"
            class="btn btn-primary"
            @click="apply">
            {{ $t('recycleBin.button.apply') }}
          </v-btn>
        </div>
      </template>
    </exo-drawer>
    <documents-recycle-bin-confirm-dialog
      ref="confirmShorten"
      :title="$t('recycleBin.confirm.shorten.title')"
      :message="$t('recycleBin.confirm.shorten.message', {0: retentionDays})"
      dialog-id="recycleBinConfirmShorten"
      @confirm="save" />
  </div>
</template>
<script>
import {PURGE_ACTION_ID, addActionEndListener, removeActionEndListener} from '../../js/RecycleBinNotifications.js';

export default {
  data: () => ({
    drawer: false,
    saving: false,
    inRange: false,
    retentionDays: null,
    savedDays: null,
    minDays: 1,
    maxDays: 365,
  }),
  computed: {
    valid() {
      return this.inRange
          && Number.isInteger(this.retentionDays)
          && this.retentionDays >= this.minDays
          && this.retentionDays <= this.maxDays;
    },
  },
  created() {
    addActionEndListener(this.purgeEnded);
  },
  beforeDestroy() {
    removeActionEndListener(this.purgeEnded);
  },
  methods: {
    open() {
      return this.$recycleBinService.getSettings()
        .then(settings => {
          this.savedDays = settings?.retentionDays || null;
          this.retentionDays = this.savedDays;
          this.$refs.drawer.open();
        })
        .catch(this.displayError);
    },
    close() {
      this.$refs.drawer.close();
    },
    apply() {
      // The period shown is saved even when unchanged: it replaces a stored
      // value the server could not read, and which it shows as its default.
      if (this.retentionDays < this.savedDays) {
        this.$refs.confirmShorten.open();
      } else {
        this.save();
      }
    },
    save() {
      this.saving = true;
      return this.$recycleBinService.saveSettings(this.retentionDays)
        .then(() => {
          this.savedDays = this.retentionDays;
          this.$recycleBinService.displayAlert(this.$t('recycleBin.configure.saved'));
          this.close();
        })
        .catch(this.displayError)
        .finally(() => this.saving = false);
    },
    // A shorter period removes for good what stayed longer, then tells who set it.
    purgeEnded(action) {
      if (action.actionId === PURGE_ACTION_ID) {
        if (action.succeeded) {
          this.$recycleBinService.displayAlert(this.$t('recycleBin.purge.success', {0: action.numberOfItems}));
        } else {
          this.$recycleBinService.displayAlert(this.$t('recycleBin.purge.failed'), 'error');
        }
      }
    },
    displayError(error) {
      this.$recycleBinService.displayError(error, key => this.$t(key));
    },
  },
};
</script>
