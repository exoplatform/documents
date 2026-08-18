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
  <div class="cleanupUserReviewApp">
    <v-card class="pa-5" flat>
      <div v-if="loading" class="d-flex justify-center py-10">
        <v-progress-circular indeterminate />
      </div>
      <template v-else-if="summary && summary.state === 'PUBLISHED'">
        <document-cleanup-review-summary-banner :summary="summary" />
        <document-cleanup-review-items-list
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
export default {
  data() {
    return {
      summary: null,
      loading: true,
    };
  },
  computed: {
    cleanupInProgress() {
      return ['LOCKED', 'EXECUTING'].includes(this.summary?.state);
    },
  },
  created() {
    this.loadSummary();
  },
  methods: {
    loadSummary() {
      return this.$cleanupService.getMySummary()
        .then(summary => this.summary = summary)
        .catch(() => this.summary = null)
        .finally(() => this.loading = false);
    },
  }
};
</script>
