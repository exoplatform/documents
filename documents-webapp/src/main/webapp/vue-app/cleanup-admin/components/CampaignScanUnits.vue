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
  <div v-if="inFlightUnits.length" class="mt-4">
    <div class="text-light-color caption mb-1">
      {{ $t('cleanup.admin.campaign.subtreesInFlight') }}
    </div>
    <div
      v-for="unit in inFlightUnits"
      :key="unit.unitPath"
      class="d-flex align-center flex-wrap caption py-1">
      <span class="text-color font-weight-bold text-truncate me-3">{{ unit.unitPath }}</span>
      <span class="text-light-color me-3 text-no-wrap">
        <number-format :value="unit.scannedCount" />
        <span v-if="unit.totalCount" class="mx-1">/</span>
        <number-format v-if="unit.totalCount" :value="unit.totalCount" />
      </span>
      <!-- The walk count of THIS subtree, and the only figure that separates a
           scan making progress from one going round in circles: a resumed run
           spends one attempt per unit it claims, so a number climbing with the
           checkpoint standing still is a subtree being re-walked, not walked -->
      <span v-if="unit.attemptCount > 1" class="text-light-color me-3 text-no-wrap">
        {{ $t('cleanup.admin.campaign.subtreeAttempts', {0: unit.attemptCount}) }}
      </span>
      <span v-if="unit.lastScannedPath" class="text-light-color text-truncate">
        {{ $t('cleanup.admin.campaign.subtreeCheckpoint', {0: unit.lastScannedPath}) }}
      </span>
    </div>
  </div>
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
};
</script>
