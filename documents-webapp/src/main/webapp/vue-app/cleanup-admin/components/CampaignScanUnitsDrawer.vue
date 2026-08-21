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
  <!-- IN A DRAWER, and no longer inline under the progress bar: the list is one
       row per reader, so raising the scan's fan-out to sixteen turned it into
       fifty lines of wrapping paths that pushed the report itself below the
       fold. The page keeps the COUNT, this holds the detail -->
  <exo-drawer
    ref="drawer"
    right
    class="cleanupScanUnitsDrawer">
    <template slot="title">
      {{ $t('cleanup.admin.campaign.subtreesInFlight') }}
    </template>
    <template slot="content">
      <div class="pa-4">
        <div class="text-light-color caption mb-4">
          {{ $t('cleanup.admin.campaign.subtreesInFlightExplanation') }}
        </div>
        <div v-if="!inFlightUnits.length" class="text-light-color caption">
          {{ $t('cleanup.admin.campaign.subtreesInFlightNone') }}
        </div>
        <!-- One block per subtree rather than one ROW: a full JCR path does not
             fit on a line, and truncating the checkpoint hid the very figure the
             panel exists for -->
        <div
          v-for="unit in inFlightUnits"
          :key="unit.unitPath"
          class="mb-4">
          <div class="d-flex align-center flex-wrap">
            <span class="text-color font-weight-bold text-break me-3">{{ unit.unitPath }}</span>
            <span class="text-light-color caption text-no-wrap">
              <number-format :value="unit.scannedCount" />
              <span v-if="unit.totalCount" class="mx-1">/</span>
              <number-format v-if="unit.totalCount" :value="unit.totalCount" />
            </span>
            <!-- The walk count of THIS subtree, and the only figure that separates
                 a scan making progress from one going round in circles: a resumed
                 run spends one attempt per unit it claims, so a number climbing
                 with the checkpoint standing still is a subtree being re-walked,
                 not walked -->
            <span v-if="unit.attemptCount > 1" class="text-light-color caption ms-3 text-no-wrap">
              {{ $t('cleanup.admin.campaign.subtreeAttempts', {0: unit.attemptCount}) }}
            </span>
          </div>
          <div v-if="unit.lastScannedPath" class="text-light-color caption mt-1 text-break">
            {{ $t('cleanup.admin.campaign.subtreeCheckpoint', {0: unit.lastScannedPath}) }}
          </div>
        </div>
      </div>
    </template>
  </exo-drawer>
</template>
<script>
export default {
  props: {
    scanUnits: {
      type: Object,
      default: null,
    },
  },
  computed: {
    // RUNNING units only, and the server already bounds them by the reader count
    // — never the whole unit list, which is one row per space
    inFlightUnits() {
      return this.scanUnits?.inFlightUnits || [];
    },
  },
  methods: {
    open() {
      this.$refs.drawer.open();
    },
  },
};
</script>
