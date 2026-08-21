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
  <div>
    <div v-if="running" class="d-flex align-center mt-4">
      <v-progress-linear
        :value="progress"
        height="8"
        rounded />
      <div class="ms-3 d-flex align-center text-no-wrap">
        <span>{{ $t('cleanup.admin.campaign.progressPercent', {0: progress}) }}</span>
        <span class="ms-1">(</span>
        <number-format :value="campaign.processedCount" />
        <span class="mx-1">/</span>
        <number-format :value="campaign.totalCount" />
        <span>)</span>
      </div>
      <span v-if="eta" class="ms-3 text-no-wrap text-light-color">
        {{ $t('cleanup.admin.campaign.eta', {0: eta}) }}
      </span>
    </div>
    <!-- SUBTREE progress, next to the node percentage and not instead of it: the
         two answer different questions, and only this one answers "is it done".
         A dry run holds open until every subtree SETTLED, so a scan can sit at
         100% of its nodes with one subtree still to finish -->
    <div v-if="showSubtrees" class="d-flex align-center mt-2 text-light-color caption">
      <span>{{ $t('cleanup.admin.campaign.subtreesSettled', {0: scanUnits.settledCount, 1: scanUnits.unitCount}) }}</span>
      <span v-if="scanUnits.runningCount" class="ms-3">
        {{ $t('cleanup.admin.campaign.subtreesWalking', {0: scanUnits.runningCount}) }}
      </span>
      <span v-if="scanUnits.maxAttemptCount > 1" class="ms-3">
        {{ $t('cleanup.admin.campaign.subtreeAttempts', {0: scanUnits.maxAttemptCount}) }}
      </span>
    </div>
    <div class="d-flex flex-wrap mt-4">
      <div class="me-8">
        <div class="text-light-color caption">{{ $t('cleanup.admin.campaign.candidates') }}</div>
        <number-format :value="campaign.candidateCount" class="text-color font-weight-bold" />
      </div>
      <div class="me-8">
        <div class="text-light-color caption">{{ $t('cleanup.admin.campaign.reclaimable') }}</div>
        <div class="text-color font-weight-bold">{{ $cleanupSize(campaign.reclaimableBytes) }}</div>
      </div>
      <div class="me-8">
        <div class="text-light-color caption">{{ $t('cleanup.admin.campaign.reclaimed') }}</div>
        <div class="text-color font-weight-bold">{{ $cleanupSize(campaign.reclaimedBytes) }}</div>
      </div>
    </div>
  </div>
</template>
<script>
export default {
  props: {
    campaign: {
      type: Object,
      default: null,
    },
    // Per-unit breakdown, null until loaded (and for campaigns that never ran a
    // parallel scan). The percentage below reads its scanComplete flag: without
    // it, the bar is not allowed to claim 100%
    scanUnits: {
      type: Object,
      default: null,
    },
  },
  computed: {
    running() {
      return this.campaign && ['DRY_RUN_RUNNING', 'EXECUTING'].includes(this.campaign.state);
    },
    showSubtrees() {
      return this.running && this.scanUnits?.unitCount > 0;
    },
    progress() {
      // The completeness claim is only made when the SUBTREE counts back it, and
      // only for a dry run — an EXECUTING campaign has no scan units to consult,
      // and its own numerator is the item count it is really working through
      const complete = this.campaign?.state !== 'DRY_RUN_RUNNING' || !this.scanUnits || this.scanUnits.scanComplete;
      return this.$cleanupUtils.progressPercentage(this.campaign?.processedCount, this.campaign?.totalCount, complete);
    },
    eta() {
      return this.$cleanupDuration(this.campaign?.etaSeconds * 1000);
    },
  },
};
</script>
