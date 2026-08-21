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
  <exo-drawer
    ref="drawer"
    right
    class="cleanupScanFailuresDrawer">
    <template slot="title">
      {{ $t('cleanup.admin.campaign.skippedTitle') }}
    </template>
    <template slot="content">
      <div class="pa-4">
        <div class="mb-4">
          {{ $t('cleanup.admin.campaign.skippedExplanation') }}
        </div>
        <!-- One block per SUBTREE, worst first: the count says how much of the
             report that subtree lost, the reason and trace say why. Only the FIRST
             failure of each is kept — ten thousand traces of the same bug is a
             page nobody reads -->
        <div
          v-for="unit in failures"
          :key="unit.unitPath"
          class="mb-6">
          <div class="d-flex align-center flex-wrap">
            <span class="text-color font-weight-bold text-break me-3">{{ unit.unitPath }}</span>
            <span class="text-light-color caption text-no-wrap">
              {{ $t('cleanup.admin.campaign.skippedInSubtree', {0: unit.evalFailureCount}) }}
            </span>
            <v-spacer />
            <v-btn
              v-if="unit.evalFailureDetail"
              :aria-label="$t('cleanup.admin.items.copyStackTrace')"
              icon
              small
              @click="copy(unit)">
              <v-icon size="14">fas fa-copy</v-icon>
            </v-btn>
          </div>
          <div v-if="unit.evalFailureReason" class="text-light-color caption mt-1 text-break">
            {{ unit.evalFailureReason }}
          </div>
          <!-- Pre-formatted and scrollable in its OWN box: a stack trace must not
               make the drawer scroll sideways -->
          <pre
            v-if="unit.evalFailureDetail"
            :style="stackTraceStyle"
            class="mt-2 pa-2 caption">{{ unit.evalFailureDetail }}</pre>
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
    failures() {
      return this.scanUnits?.evaluationFailures || [];
    },
    // Inline, because this module ships NO stylesheet on purpose (a per-portlet
    // skin was written and removed on review): a trace scrolls inside its own box
    // so it can never make the drawer scroll sideways
    stackTraceStyle() {
      return {
        'overflow-x': 'auto',
        'white-space': 'pre',
        'background-color': 'var(--allPagesGreyBackground, #f0f0f0)',
        'border-radius': '4px',
      };
    },
  },
  methods: {
    open() {
      this.$refs.drawer.open();
    },
    copy(unit) {
      this.$cleanupUtils.copyToClipboard(unit?.evalFailureDetail)
        .then(copied => document.dispatchEvent(new CustomEvent('notification-alert', {detail: {
          message: copied ? this.$t('cleanup.admin.items.stackTraceCopied') : this.$t('cleanup.admin.items.copyFailed'),
          type: copied ? 'success' : 'error',
        }})));
    },
  },
};
</script>
