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
    <!-- Own row ABOVE the selection/bulk bar, which is left untouched: searched
         server-side (on the item path, so both the file name and its folders
         match), never by filtering the page already loaded -->
    <v-text-field
      v-model="search"
      :label="$t('cleanup.review.items.search')"
      prepend-inner-icon="fas fa-search"
      class="pa-0 mb-2"
      style="max-width: 320px"
      dense
      outlined
      hide-details
      clearable
      @input="onSearchTyped" />
    <div v-if="selectedItems.length && !reviewClosed" class="d-flex align-center mb-2">
      <span class="text-color">
        {{ $t('cleanup.review.items.selected', {0: selectedItems.length}) }}
      </span>
      <v-spacer />
      <v-btn
        v-if="selectedKeptIds.length"
        :disabled="bulkInProgress"
        :loading="unkeepInProgress"
        class="btn me-2"
        @click="unkeepSelected">
        {{ $t('cleanup.review.items.unkeepSelected') }}
      </v-btn>
      <v-btn
        v-if="selectedCandidateIds.length"
        :disabled="bulkInProgress"
        :loading="keepInProgress"
        class="btn btn-primary"
        @click="keepSelected">
        {{ $t('cleanup.review.items.keepSelected') }}
      </v-btn>
    </div>
    <v-data-table
      v-model="selectedItems"
      :headers="headers"
      :items="items"
      :loading="loading"
      :options.sync="options"
      :server-items-length="totalItems"
      :footer-props="{ itemsPerPageOptions }"
      :no-data-text="$t('cleanup.review.items.empty')"
      :show-select="!reviewClosed"
      item-key="id"
      must-sort
      @update:options="loadItems">
      <template slot="item.path" slot-scope="{item}">
        <div
          :title="item.path"
          class="text-truncate"
          style="max-width: 220px">
          {{ item.path }}
        </div>
      </template>
      <template slot="item.action" slot-scope="{item}">
        {{ $t(`cleanup.item.action.${item.action}`) }}
      </template>
      <template slot="item.fileSize" slot-scope="{item}">
        {{ $cleanupSize(item.action === 'PURGE_VERSIONS' ? item.versionsSize : item.fileSize) }}
      </template>
      <template slot="item.state" slot-scope="{item}">
        <v-chip
          :color="$cleanupUtils.itemStateColor(item.state)"
          :outlined="!$cleanupUtils.isLoudState(item.state)"
          :dark="$cleanupUtils.isLoudState(item.state)"
          small>
          {{ $t(`cleanup.item.state.${item.state}`) }}
        </v-chip>
      </template>
      <template slot="item.keep" slot-scope="{item}">
        <div class="d-flex align-center justify-center">
          <!-- Once the grace deadline elapsed the review is frozen server-side
               (cleanup.reviewClosed), so the actions must not be offered anymore -->
          <v-btn
            v-if="!reviewClosed && item.state === 'CANDIDATE'"
            :loading="item.loading"
            class="btn"
            small
            @click="keepOne(item)">
            {{ $t('cleanup.review.items.keep') }}
          </v-btn>
          <v-btn
            v-else-if="!reviewClosed && item.state === 'EXEMPTED'"
            :loading="item.loading"
            class="btn"
            small
            @click="unkeepOne(item)">
            {{ $t('cleanup.review.unkeep') }}
          </v-btn>
          <v-tooltip
            :open-delay="500"
            bottom>
            <template #activator="{ on, attrs }">
              <v-btn
                :aria-label="$t('cleanup.review.showDetails')"
                icon
                small
                v-bind="attrs"
                v-on="on"
                @click="showDetails(item)">
                <v-icon size="16">fa-info-circle</v-icon>
              </v-btn>
            </template>
            <span>{{ $t('cleanup.review.showDetails') }}</span>
          </v-tooltip>
        </div>
      </template>
    </v-data-table>
  </div>
</template>
<script>
const SEARCH_DEBOUNCE_MS = 400;
// Fallback label for a reason code carrying no i18n key of its own — e.g. the
// class name applyToItems falls back to when an exception has no message.
const UNKNOWN_REASON_KEY = 'cleanup.review.items.failureUnknown';
// The item table has NO name column: the DTO's 'name' is the last segment of the
// path, so the Name column is sorted (server-side) on the path.
const SORT_FIELDS = {name: 'path'};
const DEFAULT_SORT_FIELD = 'fileSize';

export default {
  props: {
    reviewClosed: {
      type: Boolean,
      default: false,
    },
  },
  data() {
    return {
      items: [],
      totalItems: 0,
      selectedItems: [],
      loading: false,
      keepInProgress: false,
      unkeepInProgress: false,
      search: null,
      searchDebounce: null,
      // Monotonic token of the last load STARTED: a response carrying an older
      // token is dropped instead of overwriting the table (see loadItems)
      loadToken: 0,
      options: {
        page: 1,
        itemsPerPage: 20,
        // Biggest reclaimable files first: what a reviewer wants to decide on
        // first. The server appends the stable 'id' tiebreaker.
        sortBy: [DEFAULT_SORT_FIELD],
        sortDesc: [true],
      },
      itemsPerPageOptions: [20, 50, 100],
    };
  },
  computed: {
    bulkInProgress() {
      return this.keepInProgress || this.unkeepInProgress;
    },
    // Each bulk action submits ONLY the selection it can actually decide: the
    // server refuses a keep on an already-kept item (and an un-keep on a
    // candidate), which would be reported as a failure the user never caused
    selectedCandidateIds() {
      return this.selectedItems.filter(item => item.state === 'CANDIDATE').map(item => item.id);
    },
    selectedKeptIds() {
      return this.selectedItems.filter(item => item.state === 'EXEMPTED').map(item => item.id);
    },
    // Server-sorted columns only (SORT_FIELDS maps 'name' onto the path). The
    // planned-action and the row-actions columns stay unsortable. The size column
    // orders on 'fileSize' — the reclaimable size shown for a PURGE_VERSIONS row
    // is the versions size, but fileSize remains the meaningful ranking here
    headers() {
      return [
        {text: this.$t('cleanup.review.items.name'), value: 'name', align: 'left'},
        {text: this.$t('cleanup.review.items.path'), value: 'path', align: 'left'},
        {text: this.$t('cleanup.review.items.action'), value: 'action', align: 'center', sortable: false},
        {text: this.$t('cleanup.review.items.size'), value: 'fileSize', align: 'center'},
        {text: this.$t('cleanup.review.items.state'), value: 'state', align: 'center'},
        {text: this.$t('cleanup.review.items.actions'), value: 'keep', align: 'center', sortable: false},
      ];
    },
  },
  watch: {
    reviewClosed(closed) {
      if (closed) {
        this.selectedItems = [];
      }
    },
  },
  created() {
    this.loadItems();
  },
  beforeDestroy() {
    if (this.searchDebounce) {
      window.clearTimeout(this.searchDebounce);
    }
  },
  methods: {
    // Debounced so a typed term costs ONE query, not one per keystroke; a new
    // term always restarts at page 1, otherwise the user could land on an empty
    // page of a much shorter result set
    onSearchTyped(text) {
      this.search = text || null;
      if (this.searchDebounce) {
        window.clearTimeout(this.searchDebounce);
      }
      this.searchDebounce = window.setTimeout(() => {
        this.options = {...this.options, page: 1};
        this.loadItems();
      }, SEARCH_DEBOUNCE_MS);
    },
    // SUPERSESSION: loads overlap (the debounce narrows the typing window but
    // never closes it, and @update:options plus the post-decision refreshes can
    // fire concurrently), and the path search is documented unindexable — so a
    // slow response can land after a newer one. Only the response of the LAST
    // load started is applied; older ones are dropped, table and spinner alike.
    loadItems() {
      this.loading = true;
      this.selectedItems = [];
      this.loadToken = this.loadToken + 1;
      const token = this.loadToken;
      const {page, itemsPerPage, sortBy, sortDesc} = this.options;
      const sortField = SORT_FIELDS[sortBy[0]] || sortBy[0] || DEFAULT_SORT_FIELD;
      return this.$cleanupService.getMyItems({
        search: this.search,
        page: page - 1,
        size: itemsPerPage,
        sort: `${sortField},${sortDesc[0] === false ? 'asc' : 'desc'}`,
      })
        .then(data => {
          if (token !== this.loadToken) {
            return;
          }
          this.items = data?.items?.map(i => ({
            ...i,
            loading: false,
          })) || [];
          this.totalItems = data?.totalItems || 0;
        })
        .catch(() => {
          if (token === this.loadToken) {
            this.displayAlert(this.$t('cleanup.review.items.loadError'), 'error');
          }
        })
        .finally(() => {
          if (token === this.loadToken) {
            this.loading = false;
          }
        });
    },
    showDetails(item) {
      document.dispatchEvent(new CustomEvent('open-document-info-drawer', {detail: item.nodeUuid}));
    },
    keepOne(item) {
      item.loading = true;
      return this.$cleanupService.keepItem(item.id)
        .then(() => this.keptDone(1))
        .catch(error => this.displayAlert(this.decisionError('keep', error), 'error'))
        .finally(() => item.loading = false);
    },
    keepSelected() {
      return this.bulkDecide(this.selectedCandidateIds, ids => this.$cleanupService.keepItems(ids), 'keep');
    },
    unkeepSelected() {
      return this.bulkDecide(this.selectedKeptIds, ids => this.$cleanupService.unkeepItems(ids), 'unkeep');
    },
    // The spinning rows are captured BEFORE the request: bulkDone reloads the
    // list, which clears selectedItems, so resetting their loading flag through
    // that (now empty) array in the finally would leave the rows spinning
    bulkDecide(itemIds, submit, action) {
      if (!itemIds.length || this.bulkInProgress) {
        return;
      }
      const decidedItems = this.selectedItems.slice();
      decidedItems.forEach(item => item.loading = true);
      this[`${action}InProgress`] = true;
      return submit(itemIds)
        .then(result => this.bulkDone(result, action))
        .catch(error => this.displayAlert(this.decisionError(action, error), 'error'))
        .finally(() => {
          decidedItems.forEach(item => item.loading = false);
          this[`${action}InProgress`] = false;
        });
    },
    unkeepOne(item) {
      item.loading = true;
      return this.$cleanupService.unkeepItem(item.id)
        .then(() => {
          this.displayAlert(this.$t('cleanup.review.items.unkeepSuccess', {0: 1}));
          this.$emit('kept');
          return this.loadItems();
        })
        .catch(error => this.displayAlert(this.decisionError('unkeep', error), 'error'))
        .finally(() => item.loading = false);
    },
    keptDone(count) {
      this.displayAlert(this.$t('cleanup.review.items.keepSuccess', {0: count}));
      this.$emit('kept');
      return this.loadItems();
    },
    // A bulk keep/un-keep continues past individual failures. The three outcomes
    // are told apart: total failure (nothing decided), PARTIAL success (naming
    // both counts — '2 could not be kept' alone reads the same whether 8 or 0
    // succeeded), and full success. The list is refreshed either way.
    //
    // The per-item REASONS are reported too, grouped by code: the backend already
    // answers localizable message codes (cleanup.notOwner, cleanup.reviewClosed,
    // cleanup.itemNotCandidate...) and throwing them away left the user with a
    // bare count and a 'please try again' that is wrong for all of them but one.
    bulkDone(result, action) {
      const succeeded = result?.succeeded || 0;
      const failures = result?.failures || [];
      if (failures.length) {
        const headline = succeeded
          ? this.$t(`cleanup.review.items.${action}Partial`, {0: succeeded, 1: failures.length})
          : this.$t(`cleanup.review.items.${action}Failures`, {0: failures.length});
        this.displayAlert(`${headline} ${this.failureDetail(failures)}`, 'warning');
      } else {
        this.displayAlert(this.$t(`cleanup.review.items.${action}Success`, {0: succeeded}));
      }
      this.$emit('kept');
      return this.loadItems();
    },
    // '3 × You are not allowed to decide for this file, 1 × The review period is
    // over' — plus the retry hint ONLY when every reason is the transient one
    failureDetail(failures) {
      const detail = this.$cleanupUtils.groupFailuresByReason(failures)
        .map(group => `${group.count} × ${this.reasonLabel(group.reason)}`)
        .join(', ');
      return this.$cleanupUtils.isRetryable(failures)
        ? `${detail}. ${this.$t('cleanup.review.items.retryHint')}`
        : `${detail}.`;
    },
    // Through the SHARED $cleanupErrorLabel (cleanup-common): a message code with
    // no bundle entry must never be shown raw, it falls back to a generic
    // sentence instead of leaking 'IllegalState...'. Only the fallback key is
    // local — the bundle differs per portlet
    reasonLabel(reasonOrError) {
      return this.$cleanupErrorLabel(reasonOrError, UNKNOWN_REASON_KEY);
    },
    // Single-item endpoints answer the same message codes in the error body, so
    // the very same localization applies there — the helper unwraps the Error
    decisionError(action, error) {
      return `${this.$t(`cleanup.review.items.${action}Error`)} ${this.reasonLabel(error)}`;
    },
    displayAlert(message, type) {
      document.dispatchEvent(new CustomEvent('notification-alert', {detail: {
        message,
        type: type || 'success',
      }}));
    },
  }
};
</script>
