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
      <v-tooltip
        v-if="executable"
        :disabled="executeEnabled"
        bottom>
        <template #activator="{ on, attrs }">
          <div v-bind="attrs" v-on="on">
            <v-btn
              :disabled="!executeEnabled"
              :loading="actionInProgress"
              class="btn btn-primary me-2"
              @click="execute">
              {{ $t('cleanup.admin.campaign.execute') }}
            </v-btn>
          </div>
        </template>
        <span>{{ $t('cleanup.admin.execute.remaining', {0: remainingTime}) }}</span>
      </v-tooltip>
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
// Slow safety net around the CometD follow-up, which stays the primary refresh
// path: it also re-evaluates 'now' so the Execute gate and the remaining-time
// tooltip keep ticking instead of freezing on the value captured at load.
const REFRESH_PERIOD_MS = 30000;

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
      now: Date.now(),
      refreshTimerId: null,
    };
  },
  computed: {
    cancelable() {
      return this.campaign && !['COMPLETED', 'CANCELLED'].includes(this.campaign.state);
    },
    executable() {
      return ['PUBLISHED', 'LOCKED'].includes(this.campaign?.state);
    },
    running() {
      return ['DRY_RUN_RUNNING', 'EXECUTING'].includes(this.campaign?.state);
    },
    // A campaign cancelled mid-grace keeps its future lockDate: without the
    // terminal-state guard the grace would stay 'pending' and the fallback timer
    // would keep ticking for weeks on a campaign nothing can happen to anymore
    gracePending() {
      return !['COMPLETED', 'CANCELLED'].includes(this.campaign?.state)
        && !!this.graceDeadline && this.graceDeadline > this.now;
    },
    // Ticking is only needed while progress can move or a deadline can elapse
    refreshNeeded() {
      return this.running || this.gracePending;
    },
    graceDeadline() {
      return this.campaign?.lockDate
        || (this.campaign?.publishedDate && (this.campaign.publishedDate + (this.campaign.graceDays || 0) * 86400000)) || 0;
    },
    executeEnabled() {
      return this.campaign?.state === 'LOCKED' || (!!this.graceDeadline && this.graceDeadline <= this.now);
    },
    remainingTime() {
      return this.$cleanupUtils.formatRemaining(this.graceDeadline, this.now);
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
    refreshNeeded() {
      this.refreshTimer();
    },
  },
  created() {
    this.loadCampaign();
    document.addEventListener('campaign.progress', this.applyProgressEvent);
    document.addEventListener('campaign.stateChanged', this.applyStateChangedEvent);
    document.addEventListener('visibilitychange', this.onVisibilityChange);
  },
  beforeDestroy() {
    this.stopTimer();
    document.removeEventListener('campaign.progress', this.applyProgressEvent);
    document.removeEventListener('campaign.stateChanged', this.applyStateChangedEvent);
    document.removeEventListener('visibilitychange', this.onVisibilityChange);
  },
  methods: {
    loadCampaign() {
      this.now = Date.now();
      return this.$cleanupService.getCampaign(this.campaignId)
        .then(campaign => this.campaign = campaign)
        .catch(() => this.displayAlert(this.$t('cleanup.admin.campaigns.loadError'), 'error'))
        .finally(() => this.refreshTimer());
    },
    // Coming back to the tab must tick IMMEDIATELY, not only re-arm the timer:
    // no refresh happened while the tab was hidden, so the countdown — and the
    // Execute gate it drives — would stay stale for a whole period otherwise
    onVisibilityChange() {
      if (!document.hidden) {
        this.tick();
      }
      this.refreshTimer();
    },
    // Started only while it can change something, and never while the tab is
    // hidden: no polling behind the user's back
    refreshTimer() {
      this.stopTimer();
      if (this.refreshNeeded && !document.hidden) {
        this.refreshTimerId = window.setInterval(this.tick, REFRESH_PERIOD_MS);
      }
    },
    stopTimer() {
      if (this.refreshTimerId) {
        window.clearInterval(this.refreshTimerId);
        this.refreshTimerId = null;
      }
    },
    tick() {
      this.now = Date.now();
      if (this.running) {
        // CometD remains the primary refresh path: this poll only prevents a
        // permanently stale progress bar if the socket never connected or dropped
        this.loadCampaign();
      }
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
