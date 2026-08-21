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
      <span v-if="scanUnits.runningCount" class="ms-3 d-flex align-center">
        {{ $t('cleanup.admin.campaign.subtreesWalking', {0: scanUnits.runningCount}) }}
        <!-- The COUNT stays on the page and the per-subtree detail moves behind
             this: at sixteen readers the inline list was fifty lines of wrapping
             paths, and it pushed the report below the fold -->
        <v-tooltip bottom>
          <template #activator="{on, attrs}">
            <v-btn
              v-bind="attrs"
              :aria-label="$t('cleanup.admin.campaign.subtreesInFlight')"
              icon
              x-small
              class="ms-1"
              v-on="on"
              @click="$emit('open-subtrees')">
              <v-icon size="14">fas fa-info-circle</v-icon>
            </v-btn>
          </template>
          <span>{{ $t('cleanup.admin.campaign.subtreesInFlightTooltip') }}</span>
        </v-tooltip>
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
    // Per-unit breakdown, null until loaded (and null again whenever its request
    // fails). The percentage below reads its scanComplete flag: without the
    // breakdown, the bar is not allowed to claim 100%. Campaigns that never ran a
    // parallel scan are NOT null here — they get a zeroed breakdown whose
    // unitCount is 0
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
      // DRY RUN only, and not `running`: the subtree breakdown belongs to the
      // WALK. Left on during EXECUTING it reported '845 of 845 subtrees
      // finished' under a bar measuring the purge, which reads as if the purge
      // had subtrees — and as if something were still being walked while the
      // scan had long finished
      return this.campaign?.state === 'DRY_RUN_RUNNING' && this.scanUnits?.unitCount > 0;
    },
    progress() {
      // The completeness claim is only made when the SUBTREE counts back it, and
      // only for a dry run — an EXECUTING campaign has no scan units to consult,
      // and its own numerator is the item count it is really working through.
      //
      // An ABSENT breakdown falls to NOT complete, which is the whole point: null
      // is 'we cannot back the claim' (the first load has not resolved yet, or it
      // keeps failing — the loader deliberately keeps what it had), never 'the
      // scan is done'. Granting 100% there rendered a full bar next to an 'in
      // progress' chip, verbatim the contradiction this cap exists to remove.
      // A campaign that never ran a parallel scan is the OTHER case and reads as
      // unitCount === 0, not as a null breakdown: the server answers a zeroed
      // breakdown for it, so it still reaches 100% instead of being pinned at 99
      // forever
      const complete = this.campaign?.state !== 'DRY_RUN_RUNNING'
                    || this.scanUnits?.unitCount === 0
                    || this.scanUnits?.scanComplete === true;
      return this.$cleanupUtils.progressPercentage(this.campaign?.processedCount, this.campaign?.totalCount, complete);
    },
    eta() {
      return this.$cleanupDuration(this.campaign?.etaSeconds * 1000);
    },
  },
};
</script>
