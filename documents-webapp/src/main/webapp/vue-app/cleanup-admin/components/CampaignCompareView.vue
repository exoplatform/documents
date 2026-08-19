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
  <!-- cleanupFiltersCompareRow is a selector for the click-outside handler below, not a style hook -->
  <div v-if="comparableCampaigns.length" class="cleanupFiltersCompareRow">
    <div class="d-flex align-center">
      <h6 class="my-0 me-4 text-color">{{ $t('cleanup.admin.compare.title') }}</h6>
      <v-select
        ref="menu1"
        v-model="otherCampaignId"
        :items="comparableCampaignItems"
        :label="$t('cleanup.admin.compare.selectCampaign')"
        item-text="name"
        item-value="id"
        class="pa-0"
        style="max-width: 220px"
        dense
        outlined
        hide-details
        @change="compare" />
    </div>
    <v-simple-table v-if="comparison" class="mt-2">
      <thead>
        <tr>
          <th>{{ $t('cleanup.admin.compare.delta') }}</th>
          <th class="text-center">{{ $t('cleanup.admin.compare.count') }}</th>
          <th class="text-center">{{ $t('cleanup.admin.compare.bytes') }}</th>
        </tr>
      </thead>
      <tbody>
        <tr>
          <td>{{ $t('cleanup.admin.compare.new') }}</td>
          <td class="text-center">{{ comparison.newCount }}</td>
          <td class="text-center">{{ $cleanupUtils.formatBytes(comparison.newBytes) }}</td>
        </tr>
        <tr>
          <td>{{ $t('cleanup.admin.compare.gone') }}</td>
          <td class="text-center">{{ comparison.goneCount }}</td>
          <td class="text-center">{{ $cleanupUtils.formatBytes(comparison.goneBytes) }}</td>
        </tr>
        <tr>
          <td>{{ $t('cleanup.admin.compare.persisting') }}</td>
          <td class="text-center">{{ comparison.persistingCount }}</td>
          <td class="text-center">{{ $cleanupUtils.formatBytes(comparison.persistingBytes) }}</td>
        </tr>
      </tbody>
    </v-simple-table>
  </div>
</template>
<script>
export default {
  props: {
    campaign: {
      type: Object,
      default: null,
    },
    campaigns: {
      type: Array,
      default: () => [],
    },
  },
  data() {
    return {
      otherCampaignId: null,
      comparison: null,
    };
  },
  computed: {
    comparableCampaigns() {
      return this.campaigns.filter(other => other.id !== this.campaign?.id
        && !['DRAFT', 'DRY_RUN_RUNNING'].includes(other.state));
    },
    comparableCampaignItems() {
      return [
        {name: this.$t('cleanup.admin.compare.none'), id: null},
        ...this.comparableCampaigns,
      ];
    },
  },
  watch: {
    campaign() {
      this.otherCampaignId = null;
      this.comparison = null;
    },
  },
  created() {
    document.addEventListener('click', this.closeMenus);
  },
  beforeDestroy() {
    document.removeEventListener('click', this.closeMenus);
  },
  methods: {
    compare() {
      if (!this.otherCampaignId) {
        this.comparison = null;
        return;
      }
      return this.$cleanupService.compareCampaigns(this.campaign.id, this.otherCampaignId)
        .then(comparison => this.comparison = comparison)
        .catch(() => {
          this.comparison = null;
          document.dispatchEvent(new CustomEvent('notification-alert', {detail: {
            message: this.$t('cleanup.admin.compare.error'),
            type: 'error',
          }}));
        });
    },
    closeMenus(event) {
      if (this?.$refs?.menu1
          && !event?.target?.closest?.('.cleanupFiltersCompareRow')) {
        window.setTimeout(() => {
          this.$refs.menu1.blur();
        }, 50);
      }
    },
  }
};
</script>
