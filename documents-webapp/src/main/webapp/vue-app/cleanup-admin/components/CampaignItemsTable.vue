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
    <!-- cleanupFiltersRow is a selector for the click-outside handler below, not a style hook -->
    <div class="d-flex align-center flex-wrap border-box-sizing mt-8 cleanupFiltersRow">
      <h6 class="my-0 me-4 text-color">{{ $t('cleanup.admin.items.title') }}</h6>
      <v-select
        ref="menu1"
        v-model="stateFilter"
        :items="stateFilterItems"
        :label="$t('cleanup.admin.items.filter.state')"
        class="pa-0"
        style="max-width: 220px"
        dense
        outlined
        hide-details
        @change="reload" />
      <v-select
        ref="menu2"
        v-model="actionFilter"
        :items="actionFilterItems"
        :label="$t('cleanup.admin.items.filter.action')"
        class="ms-2 pa-0"
        style="max-width: 220px"
        dense
        outlined
        hide-details
        @change="reload" />
      <v-text-field
        v-model.number="minSizeMb"
        :label="$t('cleanup.admin.items.filter.minSizeMb')"
        type="number"
        min="0"
        class="ms-2 pa-0"
        style="max-width: 220px"
        dense
        outlined
        hide-details
        @change="reload" />
      <!-- Searched server-side (on the item path, which covers the file name and
           its folders): filtering the current page client-side would silently
           hide matches living on the other pages -->
      <v-text-field
        v-model="search"
        :label="$t('cleanup.admin.items.filter.search')"
        prepend-inner-icon="fas fa-search"
        class="ms-2 pa-0"
        style="max-width: 260px"
        dense
        outlined
        hide-details
        clearable
        @input="onSearchTyped" />
    </div>
    <v-data-table
      :headers="headers"
      :items="items"
      :loading="loading"
      :options.sync="options"
      :server-items-length="totalItems"
      :footer-props="{ itemsPerPageOptions }"
      :no-data-text="$t('cleanup.admin.items.empty')"
      class="mt-2"
      must-sort
      @update:options="loadItems">
      <template slot="item.path" slot-scope="{item}">
        <div
          :title="item.path"
          class="text-truncate"
          style="max-width: 320px">
          {{ item.path }}
        </div>
      </template>
      <template slot="item.ownerFullName" slot-scope="{item}">
        {{ item.ownerFullName }}
      </template>
      <!-- Rendered by the platform's shared <date-format>, never a locally
           formatted string; a row scanned before the column existed carries no
           date at all, hence the dash -->
      <template slot="item.lastModifiedDate" slot-scope="{item}">
        <date-format
          v-if="item.lastModifiedDate"
          :value="item.lastModifiedDate"
          :format="$cleanupUtils.DATE_TIME_FORMAT"
          class="text-no-wrap" />
        <span v-else>-</span>
      </template>
      <template slot="item.action" slot-scope="{item}">
        {{ $t(`cleanup.item.action.${item.action}`) }}
      </template>
      <!-- The failure feedback hangs off the state chip instead of a column of
           its own: a reason plus a 2 KB stack trace in every row would wreck a
           table whose rows overwhelmingly did NOT fail. The tooltip is
           DISABLED rather than rendered empty on those rows (same pattern as the
           Execute gate in the campaign header), which also keeps one single chip
           definition instead of two copies drifting apart -->
      <template slot="item.state" slot-scope="{item}">
        <div class="d-flex align-center justify-center">
          <v-tooltip
            :disabled="!item.failureReason"
            :open-delay="500"
            bottom>
            <template #activator="{ on, attrs }">
              <v-chip
                :color="$cleanupUtils.itemStateColor(item.state)"
                :outlined="!$cleanupUtils.isLoudState(item.state)"
                :dark="$cleanupUtils.isLoudState(item.state)"
                small
                v-bind="attrs"
                v-on="on">
                {{ $t(`cleanup.item.state.${item.state}`) }}
              </v-chip>
            </template>
            <span v-if="item.failureReason" class="d-block">
              {{ $t('cleanup.admin.items.failureTooltip', {0: failureLabel(item)}) }}
            </span>
            <!-- The requeue count belongs to the SAME tooltip: it qualifies the
                 failure, it is not worth a column -->
            <span v-if="item.attemptCount > 0" class="d-block">
              {{ $t('cleanup.admin.items.attempt', {0: item.attemptCount}) }}
            </span>
          </v-tooltip>
          <!-- Offered ONLY when the row carries a stack trace: failureDetail is
               served by this administrator endpoint alone (it can name nodes the
               reviewer of a published campaign must not see), so a button
               copying nothing must never be shown -->
          <v-tooltip
            v-if="item.failureDetail"
            :open-delay="500"
            bottom>
            <template #activator="{ on, attrs }">
              <v-btn
                :aria-label="$t('cleanup.admin.items.copyStackTrace')"
                class="ms-1"
                icon
                x-small
                v-bind="attrs"
                v-on="on"
                @click="copyStackTrace(item)">
                <v-icon size="14">fas fa-copy</v-icon>
              </v-btn>
            </template>
            <span>{{ $t('cleanup.admin.items.copyStackTrace') }}</span>
          </v-tooltip>
        </div>
      </template>
      <template slot="item.fileSize" slot-scope="{item}">
        {{ $cleanupSize(item.fileSize) }}
      </template>
      <template slot="item.versionsSize" slot-scope="{item}">
        {{ $cleanupSize(item.versionsSize) }}
      </template>
      <template slot="item.reclaimedBytes" slot-scope="{item}">
        {{ $cleanupSize(item.reclaimedBytes) }}
      </template>
      <template slot="item.reclaimableBytes" slot-scope="{item}">
        {{ $cleanupSize(item.reclaimableBytes) }}
      </template>
    </v-data-table>
  </div>
</template>
<script>
const MEGA_BYTE = 1048576;
const ITEM_STATES = ['CANDIDATE', 'EXEMPTED', 'SPARED_BY_MODIFICATION', 'GONE', 'PURGED', 'SKIPPED'];
const ITEM_ACTIONS = ['DELETE', 'PURGE_VERSIONS'];
const SEARCH_DEBOUNCE_MS = 400;
// Generic sentence shown when the failure code carried by a row has no bundle
// entry of its own: a raw code (or a raw exception class name) must never reach
// the tooltip
const UNKNOWN_FAILURE_KEY = 'cleanup.admin.campaign.unexpectedError';
// The item table has NO name column: the DTO's 'name' is the last segment of the
// path, so the Name column is sorted (server-side) on the path.
const SORT_FIELDS = {name: 'path'};
// Before a purge, every Reclaimed cell reads 0 B: the column answers a question
// nobody can ask yet, while the figure that matters for the decision — what this
// report WOULD free — was not on screen at all. So the last column is Reclaimable
// until the campaign executes, and the default ordering follows it: a list ranks
// by the figure it displays (W24), which is the same rule the user review list
// already obeys.
const EXECUTED_STATES = ['EXECUTING', 'COMPLETED'];
const RECLAIMABLE_SORT_FIELD = 'reclaimableBytes';
const DEFAULT_SORT_FIELD = 'fileSize';

export default {
  props: {
    campaignId: {
      type: Number,
      default: null,
    },
    // Decides which of the two size outcomes the last column shows. Not derived
    // from the rows: an empty page would make the table guess, and a filtered one
    // would make it guess differently
    campaignState: {
      type: String,
      default: null,
    },
  },
  data() {
    return {
      items: [],
      totalItems: 0,
      loading: false,
      stateFilter: null,
      actionFilter: null,
      minSizeMb: null,
      search: null,
      searchDebounce: null,
      // Monotonic token of the last load STARTED: a response carrying an older
      // token is dropped instead of overwriting the table (see loadItems)
      loadToken: 0,
      options: {
        page: 1,
        itemsPerPage: 20,
        sortBy: [EXECUTED_STATES.includes(this.campaignState) ? DEFAULT_SORT_FIELD : RECLAIMABLE_SORT_FIELD],
        sortDesc: [true],
      },
      itemsPerPageOptions: [20, 50, 100],
    };
  },
  computed: {
    executed() {
      return EXECUTED_STATES.includes(this.campaignState);
    },
    headers() {
      // Only the columns the server can order on are sortable (SORT_FIELDS maps
      // 'name' onto the path); 'ownerFullName' is resolved after the query, so it
      // stays unsortable
      return [
        {text: this.$t('cleanup.admin.items.name'), value: 'name', align: 'left'},
        {text: this.$t('cleanup.admin.items.path'), value: 'path', align: 'left'},
        {text: this.$t('cleanup.admin.items.owner'), value: 'ownerFullName', align: 'center', sortable: false},
        {text: this.$t('cleanup.admin.items.lastModifiedDate'), value: 'lastModifiedDate', align: 'center'},
        {text: this.$t('cleanup.admin.items.action'), value: 'action', align: 'center'},
        {text: this.$t('cleanup.admin.items.state'), value: 'state', align: 'center'},
        {text: this.$t('cleanup.admin.items.fileSize'), value: 'fileSize', align: 'center'},
        {text: this.$t('cleanup.admin.items.versionsSize'), value: 'versionsSize', align: 'center'},
        // File size STAYS on screen whichever of the two is shown, and not for
        // symmetry: the Min size filter narrows on that column, and a table
        // filtering on a figure it does not display is the same quiet mismatch
        // W24 removed from the ordering
        this.executed
          ? {text: this.$t('cleanup.admin.items.reclaimedBytes'), value: 'reclaimedBytes', align: 'center'}
          : {text: this.$t('cleanup.admin.items.reclaimableBytes'), value: RECLAIMABLE_SORT_FIELD, align: 'center'},
      ];
    },
    stateFilterItems() {
      return [
        {text: this.$t('cleanup.admin.items.filter.allStates'), value: null},
        ...ITEM_STATES.map(state => ({text: this.$t(`cleanup.item.state.${state}`), value: state})),
      ];
    },
    actionFilterItems() {
      return [
        {text: this.$t('cleanup.admin.items.filter.allActions'), value: null},
        ...ITEM_ACTIONS.map(action => ({text: this.$t(`cleanup.item.action.${action}`), value: action})),
      ];
    },
  },
  watch: {
    campaignId() {
      this.reload();
    },
    // A campaign executes WHILE this table is open, and the last column swaps with
    // it. Ordering on a column that just disappeared would leave the table ranked
    // by a figure nobody can see, so the sort moves to the surviving default —
    // and ONLY when it was on the column being taken away, never overriding an
    // ordering the administrator chose themselves
    executed(isExecuted) {
      const sortedOn = this.options.sortBy[0];
      if (isExecuted && sortedOn === RECLAIMABLE_SORT_FIELD) {
        this.options = {...this.options, sortBy: [DEFAULT_SORT_FIELD], page: 1};
      } else if (!isExecuted && sortedOn === 'reclaimedBytes') {
        this.options = {...this.options, sortBy: [RECLAIMABLE_SORT_FIELD], page: 1};
      }
    },
  },
  created() {
    document.addEventListener('click', this.closeMenus);
    this.loadItems();
  },
  beforeDestroy() {
    if (this.searchDebounce) {
      window.clearTimeout(this.searchDebounce);
    }
    document.removeEventListener('click', this.closeMenus);
  },
  methods: {
    reload() {
      this.options = {...this.options, page: 1};
      this.loadItems();
    },
    // Live refresh while a run progresses: re-reads the CURRENT page with the
    // current filters, sort and search — unlike reload(), which restarts at
    // page 1 because the result set itself changed. Superseded responses are
    // dropped by loadItems' own token.
    refreshCurrentPage() {
      this.loadItems();
    },
    // Debounced so a typed term costs ONE query, not one per keystroke; a new
    // term always restarts at page 1, otherwise the user could land on an empty
    // page of a much shorter result set
    onSearchTyped(text) {
      this.search = text || null;
      if (this.searchDebounce) {
        window.clearTimeout(this.searchDebounce);
      }
      this.searchDebounce = window.setTimeout(() => this.reload(), SEARCH_DEBOUNCE_MS);
    },
    // SUPERSESSION: loads overlap (the search debounce narrows the typing window
    // but never closes it, and the filter changes, @update:options and the
    // campaignId watcher can fire concurrently), and the path search is
    // documented unindexable — so a slow response can land after a newer one.
    // Only the response of the LAST load started is applied; older ones are
    // dropped, table and spinner alike.
    loadItems() {
      this.loading = true;
      this.loadToken = this.loadToken + 1;
      const token = this.loadToken;
      const {page, itemsPerPage, sortBy, sortDesc} = this.options;
      const sortField = SORT_FIELDS[sortBy[0]] || sortBy[0] || DEFAULT_SORT_FIELD;
      return this.$cleanupService.getCampaignItems(this.campaignId, {
        state: this.stateFilter,
        action: this.actionFilter,
        minSize: this.minSizeMb != null && this.minSizeMb !== '' ? Math.round(this.minSizeMb * MEGA_BYTE) : null,
        search: this.search,
        page: page - 1,
        size: itemsPerPage,
        sort: `${sortField},${sortDesc[0] === false ? 'asc' : 'desc'}`,
      }).then(data => {
        if (token !== this.loadToken) {
          return;
        }
        this.items = data?.items || [];
        this.totalItems = data?.totalItems || 0;
      }).catch(() => {
        if (token !== this.loadToken) {
          return;
        }
        this.displayAlert(this.$t('cleanup.admin.items.loadError'), 'error');
      }).finally(() => {
        if (token === this.loadToken) {
          this.loading = false;
        }
      });
    },
    // Through the SHARED $cleanupErrorLabel: failureReason is a BARE message
    // code (cleanup.referentialIntegrity, cleanup.deleteError...), never a code
    // concatenated with an exception message anymore, so it localizes like every
    // other cleanup code and falls back to a generic sentence when unknown
    failureLabel(item) {
      return this.$cleanupErrorLabel(item?.failureReason, UNKNOWN_FAILURE_KEY);
    },
    // A stack trace the admin silently did not get is worse than a message: the
    // Clipboard API rejects on an insecure origin and when the permission is
    // denied, so EVERY path here ends on a toast, success or failure.
    copyStackTrace(item) {
      const detail = item?.failureDetail;
      if (!detail) {
        return;
      }
      this.$cleanupUtils.copyToClipboard(detail)
        .then(copied => this.displayAlert(copied ? this.$t('cleanup.admin.items.stackTraceCopied')
          : this.$t('cleanup.admin.items.copyFailed'),
        copied ? 'success' : 'error'));
    },
    displayAlert(message, type) {
      document.dispatchEvent(new CustomEvent('notification-alert', {detail: {
        message,
        type: type || 'success',
      }}));
    },
    closeMenus(event) {
      if (this?.$refs?.menu1
          && this?.$refs?.menu2
          && !event?.target?.closest?.('.cleanupFiltersRow')) {
        window.setTimeout(() => {
          this.$refs.menu1.blur();
          this.$refs.menu2.blur();
        }, 50);
      }
    },
  }
};
</script>
