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
  <!-- Hosted in the Documents header review drawer (whose title slot carries
       the app title), no more a standalone portlet: plain div, no v-app -->
  <div class="full-width">
    <v-card class="pa-5" flat>
      <div v-if="loading" class="d-flex justify-center py-10">
        <v-progress-circular indeterminate />
      </div>
      <template v-else-if="summary && summary.state === 'PUBLISHED'">
        <document-cleanup-review-summary-banner
          :summary="summary"
          :review-closed="reviewClosed"
          :remaining-millis="remaining" />
        <document-cleanup-review-items-list
          :review-closed="reviewClosed"
          class="mt-4"
          @kept="loadSummary" />
      </template>
      <div
        v-else-if="summary && cleanupInProgress"
        class="d-flex flex-column align-center py-10">
        <v-icon size="48" class="text-light-color mb-4">fas fa-broom</v-icon>
        <div class="text-color">{{ $t('cleanup.review.inProgress.title') }}</div>
        <div class="text-light-color caption mt-2">{{ $t('cleanup.review.inProgress.description') }}</div>
      </div>
      <document-cleanup-review-outcome
        v-else-if="summary && summary.state === 'COMPLETED'"
        :summary="summary" />
      <div v-else class="d-flex flex-column align-center py-10">
        <v-icon size="48" class="text-light-color mb-4">fas fa-check-circle</v-icon>
        <div class="text-color">{{ $t('cleanup.review.noCampaign.title') }}</div>
        <div class="text-light-color caption mt-2">{{ $t('cleanup.review.noCampaign.description') }}</div>
      </div>
    </v-card>
  </div>
</template>
<script>
// The displayed deadline has to keep ticking: the locking cron runs every 10
// minutes, so a campaign stays PUBLISHED for a short while after its grace
// deadline elapsed (systematically so with a zero grace period). The review is
// already frozen server-side in that window, so the UI must follow the DEADLINE,
// not only the state.
const DEADLINE_REFRESH_PERIOD_MS = 30000;

export default {
  data() {
    return {
      summary: null,
      loading: true,
      // Remaining review time as computed BY THE SERVER at the last refresh, plus
      // the local instant it was received at. Only the DIFFERENCE of two local
      // clock reads is ever used (see 'remaining'), so the browser clock only has
      // to measure elapsed time — it is never compared to a server epoch. A
      // skewed client can no longer close the review while the server would still
      // accept a keep, nor keep offering actions the server already refuses.
      remainingMillis: 0,
      syncedAt: Date.now(),
      now: Date.now(),
      deadlineTimerId: null,
    };
  },
  computed: {
    cleanupInProgress() {
      return ['LOCKED', 'EXECUTING'].includes(this.summary?.state);
    },
    remaining() {
      return Math.max(0, this.remainingMillis - (this.now - this.syncedAt));
    },
    reviewClosed() {
      return !!this.summary?.deadline && this.remaining <= 0;
    },
    deadlinePending() {
      return this.summary?.state === 'PUBLISHED' && !!this.summary?.deadline && !this.reviewClosed;
    },
  },
  watch: {
    deadlinePending() {
      this.refreshDeadlineTimer();
    },
  },
  created() {
    this.loadSummary();
    document.addEventListener('visibilitychange', this.onVisibilityChange);
  },
  beforeDestroy() {
    this.stopDeadlineTimer();
    document.removeEventListener('visibilitychange', this.onVisibilityChange);
  },
  methods: {
    // Coming back to the tab must re-evaluate the deadline IMMEDIATELY, not only
    // re-arm the timer: nothing ticked while the tab was hidden, so the review
    // could stay displayed as open for a whole period after it actually closed
    onVisibilityChange() {
      if (!document.hidden) {
        this.now = Date.now();
      }
      this.refreshDeadlineTimer();
    },
    loadSummary() {
      return this.$cleanupService.getMySummary()
        .then(summary => this.applySummary(summary))
        .catch(() => this.applySummary(null))
        .finally(() => {
          this.loading = false;
          this.refreshDeadlineTimer();
        });
    },
    // Re-syncs the countdown on EVERY refresh: the value received is the server's
    // own remaining time, so the local drift accumulated since the previous
    // refresh is discarded instead of compounding
    applySummary(summary) {
      this.summary = summary;
      this.remainingMillis = summary?.remainingMillis || 0;
      this.syncedAt = Date.now();
      this.now = this.syncedAt;
    },
    refreshDeadlineTimer() {
      this.stopDeadlineTimer();
      if (this.deadlinePending) {
        this.deadlineTimerId = window.setInterval(() => this.now = Date.now(), DEADLINE_REFRESH_PERIOD_MS);
      }
    },
    stopDeadlineTimer() {
      if (this.deadlineTimerId) {
        window.clearInterval(this.deadlineTimerId);
        this.deadlineTimerId = null;
      }
    },
  }
};
</script>
