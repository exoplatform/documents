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
        {{ $cleanupUtils.formatBytes(item.action === 'PURGE_VERSIONS' ? item.versionsSize : item.fileSize) }}
      </template>
      <template slot="item.state" slot-scope="{item}">
        <v-chip small outlined>
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
      options: {
        page: 1,
        itemsPerPage: 20,
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
    headers() {
      return [
        {text: this.$t('cleanup.review.items.name'), value: 'name', align: 'left', sortable: false},
        {text: this.$t('cleanup.review.items.path'), value: 'path', align: 'left', sortable: false},
        {text: this.$t('cleanup.review.items.action'), value: 'action', align: 'center', sortable: false},
        {text: this.$t('cleanup.review.items.size'), value: 'fileSize', align: 'center', sortable: false},
        {text: this.$t('cleanup.review.items.state'), value: 'state', align: 'center', sortable: false},
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
  methods: {
    loadItems() {
      this.loading = true;
      this.selectedItems = [];
      const {page, itemsPerPage} = this.options;
      return this.$cleanupService.getMyItems(page - 1, itemsPerPage)
        .then(data => {
          this.items = data?.items?.map(i => ({
            ...i,
            loading: false,
          })) || [];
          this.totalItems = data?.totalItems || 0;
        })
        .catch(() => this.displayAlert(this.$t('cleanup.review.items.loadError'), 'error'))
        .finally(() => this.loading = false);
    },
    showDetails(item) {
      document.dispatchEvent(new CustomEvent('open-document-info-drawer', {detail: item.nodeUuid}));
    },
    keepOne(item) {
      item.loading = true;
      return this.$cleanupService.keepItem(item.id)
        .then(() => this.keptDone(1))
        .catch(() => this.displayAlert(this.$t('cleanup.review.items.keepError'), 'error'))
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
        .catch(() => this.displayAlert(this.$t(`cleanup.review.items.${action}Error`), 'error'))
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
        .catch(() => this.displayAlert(this.$t('cleanup.review.items.unkeepError'), 'error'))
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
    bulkDone(result, action) {
      const succeeded = result?.succeeded || 0;
      const failures = result?.failures?.length || 0;
      if (failures && succeeded) {
        this.displayAlert(this.$t(`cleanup.review.items.${action}Partial`, {0: succeeded, 1: failures}), 'warning');
      } else if (failures) {
        this.displayAlert(this.$t(`cleanup.review.items.${action}Failures`, {0: failures}), 'warning');
      } else {
        this.displayAlert(this.$t(`cleanup.review.items.${action}Success`, {0: succeeded}));
      }
      this.$emit('kept');
      return this.loadItems();
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
