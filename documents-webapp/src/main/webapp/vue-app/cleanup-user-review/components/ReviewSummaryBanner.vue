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
  <v-alert
    :icon="false"
    :type="reviewClosed ? 'warning' : 'info'"
    border="left"
    colored-border
    class="ma-0">
    <div class="text-color">
      {{ $t('cleanup.review.banner.candidates', {0: summary.candidateCount, 1: $cleanupSize(summary.candidateBytes)}) }}
    </div>
    <div v-if="summary.keptCount" class="text-color">
      {{ $t('cleanup.review.banner.kept', {0: summary.keptCount, 1: $cleanupSize(summary.keptBytes)}) }}
    </div>
    <!-- Past the grace deadline the review is already frozen server-side, even
         while the campaign is still PUBLISHED (the locking cron runs later) -->
    <div v-if="reviewClosed" class="text-light-color caption mt-1">
      {{ $t('cleanup.review.banner.closed') }}
    </div>
    <!-- The deadline is rendered by the platform's shared <date-format>, so the
         sentence is split around it instead of interpolating a pre-formatted
         string into the i18n message -->
    <div v-else class="text-light-color caption mt-1 d-flex flex-wrap">
      <span>{{ $t('cleanup.review.banner.deadline') }}</span>
      <date-format
        :value="summary.deadline"
        :format="$cleanupUtils.DATE_TIME_FORMAT"
        class="ms-1" />
      <!-- Countdown of the SERVER-computed remaining time, not of a deadline
           compared to the browser clock -->
      <span v-if="remainingLabel" class="ms-1">
        {{ $t('cleanup.review.banner.remaining', {0: remainingLabel}) }}
      </span>
    </div>
  </v-alert>
</template>
<script>
export default {
  props: {
    summary: {
      type: Object,
      default: null,
    },
    reviewClosed: {
      type: Boolean,
      default: false,
    },
    remainingMillis: {
      type: Number,
      default: 0,
    },
  },
  computed: {
    remainingLabel() {
      return this.$cleanupUtils.formatDuration(this.remainingMillis);
    },
  },
};
</script>
