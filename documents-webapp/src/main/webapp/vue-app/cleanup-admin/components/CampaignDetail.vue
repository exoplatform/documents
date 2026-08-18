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
  <div v-if="campaign">
    <div class="d-flex align-center flex-wrap">
      <h5 class="my-0 text-color font-weight-bold">{{ campaign.name }}</h5>
      <v-chip
        class="ms-3"
        small
        outlined>
        {{ $t(`cleanup.campaign.state.${campaign.state}`) }}
      </v-chip>
      <v-spacer />
      <v-btn
        v-if="campaign.state === 'SIMULATED'"
        :loading="actionInProgress"
        class="btn btn-primary me-2"
        @click="publish">
        {{ $t('cleanup.admin.campaign.publish') }}
      </v-btn>
      <v-btn
        v-if="campaign.state === 'LOCKED'"
        :loading="actionInProgress"
        class="btn btn-primary me-2"
        @click="execute">
        {{ $t('cleanup.admin.campaign.execute') }}
      </v-btn>
      <v-btn
        v-if="cancelable"
        :loading="actionInProgress"
        class="btn me-2"
        @click="cancel">
        {{ $t('cleanup.admin.campaign.cancel') }}
      </v-btn>
      <v-btn
        v-if="campaign.archiveAvailable"
        :href="$cleanupService.getArchiveUrl(campaign.id)"
        class="btn"
        target="_blank">
        {{ $t('cleanup.admin.campaign.downloadArchive') }}
      </v-btn>
    </div>
    <div class="text-light-color caption mt-2">
      {{ paramsSummary }}
    </div>
    <div class="text-light-color caption">
      {{ datesSummary }}
    </div>
    <document-cleanup-campaign-stats :campaign="campaign" />
    <document-cleanup-campaign-compare-view
      :campaign="campaign"
      :campaigns="campaigns"
      class="mt-4" />
    <document-cleanup-campaign-items-table
      :campaign-id="campaign.id"
      class="mt-4" />
  </div>
</template>
<script>
export default {
  props: {
    campaignId: {
      type: Number,
      default: null,
    },
    campaigns: {
      type: Array,
      default: () => [],
    },
  },
  data() {
    return {
      campaign: null,
      actionInProgress: false,
    };
  },
  computed: {
    cancelable() {
      return this.campaign && !['COMPLETED', 'CANCELLED'].includes(this.campaign.state);
    },
    paramsSummary() {
      return this.$t('cleanup.admin.campaign.paramsSummary', {
        0: this.campaign.periodMonths,
        1: this.$cleanupUtils.formatBytes(this.campaign.minFileSizeBytes),
        2: this.campaign.graceDays,
        3: this.campaign.maxVersionsPerFile,
        4: this.campaign.excludedPaths?.length || 0,
      });
    },
    datesSummary() {
      return ['startedDate', 'publishedDate', 'lockDate', 'completedDate']
        .map(field => `${this.$t(`cleanup.admin.campaign.${field}`)}: ${this.$cleanupUtils.formatDateTime(this.campaign[field]) || '-'}`)
        .join(' | ');
    },
  },
  watch: {
    campaignId() {
      this.loadCampaign();
    },
  },
  created() {
    this.loadCampaign();
    document.addEventListener('campaign.progress', this.applyProgressEvent);
    document.addEventListener('campaign.stateChanged', this.applyStateChangedEvent);
  },
  beforeDestroy() {
    document.removeEventListener('campaign.progress', this.applyProgressEvent);
    document.removeEventListener('campaign.stateChanged', this.applyStateChangedEvent);
  },
  methods: {
    loadCampaign() {
      return this.$cleanupService.getCampaign(this.campaignId)
        .then(campaign => this.campaign = campaign)
        .catch(() => this.displayAlert(this.$t('cleanup.admin.campaigns.loadError'), 'error'));
    },
    applyProgressEvent(event) {
      const message = event?.detail;
      if (message && this.campaign && message.campaignId === this.campaign.id) {
        Object.assign(this.campaign, {
          state: message.state,
          processedCount: message.processed,
          totalCount: message.total,
          etaSeconds: message.etaSeconds,
        });
      }
    },
    applyStateChangedEvent(event) {
      if (event?.detail?.campaignId === this.campaignId) {
        this.loadCampaign().then(() => this.$emit('refresh'));
      }
    },
    publish() {
      this.callAction(this.$cleanupService.publishCampaign(this.campaignId), 'publish');
    },
    execute() {
      this.callAction(this.$cleanupService.executeCampaign(this.campaignId), 'execute');
    },
    cancel() {
      this.callAction(this.$cleanupService.cancelCampaign(this.campaignId), 'cancel');
    },
    callAction(promise, action) {
      this.actionInProgress = true;
      return promise
        .then(() => {
          this.displayAlert(this.$t(`cleanup.admin.campaign.${action}Success`));
          return this.loadCampaign();
        })
        .then(() => this.$emit('refresh'))
        .catch(error => this.displayAlert(this.$t(`cleanup.admin.campaign.${action}Error`, {0: error?.message || ''}), 'error'))
        .finally(() => this.actionInProgress = false);
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
