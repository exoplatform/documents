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
  <v-app>
    <v-card class="application-body border-box-sizing pa-5" flat>
      <div class="d-flex align-center mb-4">
        <v-btn
          v-if="selectedCampaignId"
          :aria-label="$t('cleanup.admin.backToList')"
          class="ms-n2"
          icon
          @click="selectedCampaignId = null">
          <v-icon size="18">fas fa-arrow-left</v-icon>
        </v-btn>
        <h4 class="my-0 text-header">{{ $t('cleanup.admin.title') }}</h4>
        <v-spacer />
        <v-btn
          v-if="!selectedCampaignId"
          class="btn btn-primary"
          @click="$refs.createDrawer.open()">
          {{ $t('cleanup.admin.newCampaign') }}
        </v-btn>
      </div>
      <document-cleanup-campaign-detail
        v-if="selectedCampaignId"
        ref="campaignDetail"
        :campaign-id="selectedCampaignId"
        :campaigns="campaigns"
        @edit="$refs.createDrawer.open($event)"
        @refresh="loadCampaigns"
        @closed="selectedCampaignId = null" />
      <document-cleanup-campaign-list
        v-else
        :campaigns="campaigns"
        :loading="loading"
        @open="selectedCampaignId = $event.id" />
      <!-- ONE drawer instance for both modes: the New campaign button opens it
           empty, the campaign detail's Edit button opens it on a campaign
           (open(campaign)). Hosted here rather than duplicated in the detail
           view, so the name and the grace period are validated and reported in
           exactly one place -->
      <document-cleanup-campaign-create-drawer
        ref="createDrawer"
        @created="campaignCreated"
        @updated="campaignUpdated" />
    </v-card>
  </v-app>
</template>
<script>
export default {
  data() {
    return {
      campaigns: [],
      loading: false,
      selectedCampaignId: null,
      // Monotonic token of the last load STARTED: every CometD state change plus
      // the detail's refresh event reload the list, so a slow response must not
      // overwrite a newer one (see loadCampaigns)
      loadToken: 0,
    };
  },
  created() {
    this.loadCampaigns();
    this.$cleanupWebSocket.init();
    document.addEventListener('campaign.progress', this.applyProgressEvent);
    document.addEventListener('campaign.stateChanged', this.applyStateChangedEvent);
  },
  beforeDestroy() {
    document.removeEventListener('campaign.progress', this.applyProgressEvent);
    document.removeEventListener('campaign.stateChanged', this.applyStateChangedEvent);
  },
  methods: {
    loadCampaigns() {
      this.loading = true;
      this.loadToken = this.loadToken + 1;
      const token = this.loadToken;
      return this.$cleanupService.getCampaigns()
        .then(campaigns => {
          if (token === this.loadToken) {
            this.campaigns = campaigns || [];
          }
        })
        .catch(() => {
          if (token === this.loadToken) {
            this.displayAlert(this.$t('cleanup.admin.campaigns.loadError'), 'error');
          }
        })
        .finally(() => {
          if (token === this.loadToken) {
            this.loading = false;
          }
        });
    },
    applyProgressEvent(event) {
      const message = event?.detail;
      const campaign = message && this.campaigns.find(c => c.id === message.campaignId);
      if (campaign) {
        Object.assign(campaign, {
          state: message.state,
          processedCount: message.processed,
          totalCount: message.total,
          etaSeconds: message.etaSeconds,
        });
      }
    },
    applyStateChangedEvent(event) {
      this.applyProgressEvent(event);
      // states carry REST-only aggregates (counts, bytes): re-fetch the source of truth
      this.loadCampaigns();
    },
    campaignCreated(campaign) {
      this.loadCampaigns()
        .then(() => this.selectedCampaignId = campaign?.id || null);
    },
    // The update endpoint answers the FULL updated campaign: applied straight
    // onto the open detail — so its header, state chip and grace countdown
    // re-sync from the response without a refetch — and the list is reloaded so
    // the row re-renders too. The drawer already reported the outcome.
    campaignUpdated(campaign) {
      this.$refs.campaignDetail?.applyCampaign(campaign);
      this.loadCampaigns();
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
