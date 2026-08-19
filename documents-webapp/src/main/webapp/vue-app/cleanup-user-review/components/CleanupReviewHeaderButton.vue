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
  <div v-if="displayButton" class="d-inline-flex">
    <v-tooltip bottom>
      <template #activator="{ on, attrs }">
        <v-btn
          v-bind="attrs"
          v-on="on"
          :aria-label="$t('cleanup.review.headerButton.tooltip')"
          color="warning"
          elevation="0"
          class="ms-4"
          icon
          small
          @click="openDrawer">
          <v-icon size="18">fa-broom</v-icon>
        </v-btn>
      </template>
      <span class="text-subtitle-font-size">{{ $t('cleanup.review.headerButton.tooltip') }}</span>
    </v-tooltip>
    <exo-drawer
      id="cleanupUserReviewDrawer"
      ref="drawer"
      expanded
      right
      @opened="drawerOpened = true"
      @closed="drawerClosed">
      <template slot="title">
        {{ $t('cleanup.review.title') }}
      </template>
      <template slot="content">
        <!-- Mounted only while the drawer is open: the summary and the items
             list reload on EVERY (re)open instead of once at page load -->
        <document-cleanup-user-review v-if="drawerOpened" />
      </template>
    </exo-drawer>
  </div>
</template>
<script>
export default {
  data() {
    return {
      summary: null,
      drawerOpened: false,
    };
  },
  computed: {
    displayButton() {
      if (!this.summary) {
        return false;
      }
      if (this.summary.state === 'COMPLETED') {
        // Show the outcome only when the completed campaign concerned the user
        const outcome = this.summary.outcome;
        return !!outcome && (outcome.deletedCount > 0 || outcome.keptCount > 0 || outcome.freedBytes > 0);
      }
      // PUBLISHED (review open) or LOCKED/EXECUTING (read-only placeholder):
      // show only when the campaign holds something of the user's
      return this.summary.candidateCount > 0 || this.summary.keptCount > 0;
    },
  },
  created() {
    this.loadSummary();
  },
  methods: {
    loadSummary() {
      // 404 (no relevant campaign) or any error hides the button
      return this.$cleanupService.getMySummary()
        .then(summary => this.summary = summary)
        .catch(() => this.summary = null);
    },
    openDrawer() {
      this.$refs.drawer.open();
    },
    drawerClosed() {
      this.drawerOpened = false;
      // Refresh the header button visibility with the decisions just made
      return this.loadSummary();
    },
  },
};
</script>
