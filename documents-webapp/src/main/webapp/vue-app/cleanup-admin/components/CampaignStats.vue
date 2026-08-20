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
  },
  computed: {
    running() {
      return this.campaign && ['DRY_RUN_RUNNING', 'EXECUTING'].includes(this.campaign.state);
    },
    progress() {
      return this.$cleanupUtils.progressPercentage(this.campaign?.processedCount, this.campaign?.totalCount);
    },
    eta() {
      return this.$cleanupDuration(this.campaign?.etaSeconds * 1000);
    },
  },
};
</script>
