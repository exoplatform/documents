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
    <div v-if="selectedItems.length" class="d-flex align-center mb-2">
      <span class="text-color">
        {{ $t('cleanup.review.items.selected', {0: selectedItems.length}) }}
      </span>
      <v-spacer />
      <v-btn
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
      item-key="id"
      show-select
      @update:options="loadItems">
      <template slot="item.path" slot-scope="{item}">
        <div :title="item.path" class="cleanupPathCell">{{ item.path }}</div>
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
          <v-btn
            v-if="item.state === 'CANDIDATE'"
            :loading="item.loading"
            class="btn"
            small
            @click="keepOne(item)">
            {{ $t('cleanup.review.items.keep') }}
          </v-btn>
          <v-tooltip
            :open-delay="500"
            bottom>
            <template #activator="{ on, attrs }">
              <v-btn
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
  data() {
    return {
      items: [],
      totalItems: 0,
      selectedItems: [],
      loading: false,
      options: {
        page: 1,
        itemsPerPage: 20,
      },
      itemsPerPageOptions: [20, 50, 100],
    };
  },
  computed: {
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
      const itemIds = this.selectedItems
        .filter(item => item.state === 'CANDIDATE')
        .map(item => item.id);
      if (!itemIds.length) {
        return;
      }
      this.selectedItems.forEach(item => item.loading = true);
      return this.$cleanupService.keepItems(itemIds)
        .then(() => this.keptDone(itemIds.length))
        .catch(() => this.displayAlert(this.$t('cleanup.review.items.keepError'), 'error'))
        .finally(() => this.selectedItems.forEach(item => item.loading = false));
    },
    keptDone(count) {
      this.displayAlert(this.$t('cleanup.review.items.keepSuccess', {0: count}));
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
