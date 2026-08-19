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
      {{ $t('cleanup.review.banner.candidates', {0: summary.candidateCount, 1: $cleanupUtils.formatBytes(summary.candidateBytes)}) }}
    </div>
    <div v-if="summary.keptCount" class="text-color">
      {{ $t('cleanup.review.banner.kept', {0: summary.keptCount, 1: $cleanupUtils.formatBytes(summary.keptBytes)}) }}
    </div>
    <!-- Past the grace deadline the review is already frozen server-side, even
         while the campaign is still PUBLISHED (the locking cron runs later) -->
    <div v-if="reviewClosed" class="text-light-color caption mt-1">
      {{ $t('cleanup.review.banner.closed') }}
    </div>
    <div v-else class="text-light-color caption mt-1">
      {{ $t('cleanup.review.banner.deadline', {0: $cleanupUtils.formatDateTime(summary.deadline)}) }}
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
  },
};
</script>
