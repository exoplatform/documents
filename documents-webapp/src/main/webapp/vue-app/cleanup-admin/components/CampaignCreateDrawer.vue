<!--
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
-->
<template>
  <exo-drawer
    id="cleanupCampaignCreateDrawer"
    ref="drawer"
    :loading="loading"
    right>
    <template slot="title">
      {{ $t('cleanup.admin.createDrawer.title') }}
    </template>
    <template slot="content">
      <v-form ref="form" class="pa-5">
        <v-label for="cleanupCampaignName">{{ $t('cleanup.admin.createDrawer.name') }}</v-label>
        <v-text-field
          id="cleanupCampaignName"
          v-model="name"
          :rules="[v => !!v || $t('cleanup.admin.createDrawer.nameRequired')]"
          class="pt-2 pb-4"
          outlined
          dense
          required />
        <v-label for="cleanupPeriodMonths" class="mt-2">{{ $t('cleanup.admin.createDrawer.periodMonths') }}</v-label>
        <v-text-field
          id="cleanupPeriodMonths"
          v-model.number="periodMonths"
          type="number"
          min="1"
          class="pt-2 pb-4"
          outlined
          dense />
        <v-label for="cleanupMinFileSize" class="mt-2">{{ $t('cleanup.admin.createDrawer.minFileSizeMb') }}</v-label>
        <v-text-field
          id="cleanupMinFileSize"
          v-model.number="minFileSizeMb"
          type="number"
          min="0"
          class="pt-2 pb-4"
          outlined
          dense />
        <v-label for="cleanupGraceDays" class="mt-2">{{ $t('cleanup.admin.createDrawer.graceDays') }}</v-label>
        <v-text-field
          id="cleanupGraceDays"
          v-model.number="graceDays"
          type="number"
          min="1"
          class="pt-2 pb-4"
          outlined
          dense />
        <v-label for="cleanupMaxVersions" class="mt-2">{{ $t('cleanup.admin.createDrawer.maxVersionsPerFile') }}</v-label>
        <v-text-field
          id="cleanupMaxVersions"
          v-model.number="maxVersionsPerFile"
          type="number"
          min="1"
          class="pt-2 pb-4"
          outlined
          dense />
        <v-label for="cleanupExcludedPaths" class="mt-2">{{ $t('cleanup.admin.createDrawer.excludedPaths') }}</v-label>
        <v-textarea
          id="cleanupExcludedPaths"
          v-model="excludedPathsText"
          :placeholder="$t('cleanup.admin.createDrawer.excludedPathsPlaceholder')"
          rows="3"
          class="extended-textarea mt-n2"
          auto-grow />
      </v-form>
    </template>
    <template slot="footer">
      <div class="d-flex justify-end">
        <v-btn class="btn me-2" @click="close">
          {{ $t('cleanup.admin.createDrawer.cancel') }}
        </v-btn>
        <v-btn
          :loading="loading"
          class="btn btn-primary"
          @click="save">
          {{ $t('cleanup.admin.createDrawer.launch') }}
        </v-btn>
      </div>
    </template>
  </exo-drawer>
</template>
<script>
const MEGA_BYTE = 1048576;

export default {
  data() {
    return {
      loading: false,
      name: '',
      periodMonths: null,
      minFileSizeMb: null,
      graceDays: null,
      maxVersionsPerFile: null,
      excludedPathsText: '',
    };
  },
  methods: {
    open() {
      this.name = '';
      this.loading = true;
      this.$refs.drawer.open();
      this.$cleanupService.getDefaults()
        .then(defaults => {
          this.periodMonths = defaults?.periodMonths;
          this.minFileSizeMb = defaults?.minFileSizeBytes != null && Math.round(defaults.minFileSizeBytes * 100 / MEGA_BYTE) / 100 || null;
          this.graceDays = defaults?.graceDays;
          this.maxVersionsPerFile = defaults?.maxVersionsPerFile;
          this.excludedPathsText = (defaults?.excludedPaths || []).join('\n');
        })
        .catch(() => this.displayAlert(this.$t('cleanup.admin.createDrawer.defaultsError'), 'error'))
        .finally(() => this.loading = false);
    },
    close() {
      this.$refs.drawer.close();
    },
    save() {
      if (!this.$refs.form.validate()) {
        return;
      }
      this.loading = true;
      const excludedPaths = this.excludedPathsText
        .split('\n')
        .map(path => path.trim())
        .filter(path => !!path);
      this.$cleanupService.createCampaign({
        name: this.name,
        periodMonths: this.periodMonths,
        minFileSizeBytes: this.minFileSizeMb != null ? Math.round(this.minFileSizeMb * MEGA_BYTE) : null,
        graceDays: this.graceDays,
        maxVersionsPerFile: this.maxVersionsPerFile,
        excludedPaths,
      }).then(campaign => {
        this.displayAlert(this.$t('cleanup.admin.createDrawer.success'));
        this.$emit('created', campaign);
        this.close();
      }).catch(() => {
        this.displayAlert(this.$t('cleanup.admin.createDrawer.error'), 'error');
      }).finally(() => this.loading = false);
    },
    displayAlert(message, type) {
      document.dispatchEvent(new CustomEvent('notification-alert', {detail: {
        message,
        type: type || 'success',
      }}));
    },
  }
};
</script>
