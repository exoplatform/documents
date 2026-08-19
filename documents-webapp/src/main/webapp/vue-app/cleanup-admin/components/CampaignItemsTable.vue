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
      <template slot="item.action" slot-scope="{item}">
        {{ $t(`cleanup.item.action.${item.action}`) }}
      </template>
      <template slot="item.state" slot-scope="{item}">
        <v-chip small outlined>
          {{ $t(`cleanup.item.state.${item.state}`) }}
        </v-chip>
      </template>
      <template slot="item.fileSize" slot-scope="{item}">
        {{ $cleanupUtils.formatBytes(item.fileSize) }}
      </template>
      <template slot="item.versionsSize" slot-scope="{item}">
        {{ $cleanupUtils.formatBytes(item.versionsSize) }}
      </template>
      <template slot="item.reclaimedBytes" slot-scope="{item}">
        {{ $cleanupUtils.formatBytes(item.reclaimedBytes) }}
      </template>
    </v-data-table>
  </div>
</template>
<script>
const MEGA_BYTE = 1048576;
const ITEM_STATES = ['CANDIDATE', 'EXEMPTED', 'SPARED_BY_MODIFICATION', 'GONE', 'PURGED', 'SKIPPED'];
const ITEM_ACTIONS = ['DELETE', 'PURGE_VERSIONS'];

export default {
  props: {
    campaignId: {
      type: Number,
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
      options: {
        page: 1,
        itemsPerPage: 20,
        sortBy: ['fileSize'],
        sortDesc: [true],
      },
      itemsPerPageOptions: [20, 50, 100],
    };
  },
  computed: {
    headers() {
      return [
        {text: this.$t('cleanup.admin.items.name'), value: 'name', align: 'left', sortable: false},
        {text: this.$t('cleanup.admin.items.path'), value: 'path', align: 'left', sortable: false},
        {text: this.$t('cleanup.admin.items.owner'), value: 'ownerFullName', align: 'center', sortable: false},
        {text: this.$t('cleanup.admin.items.action'), value: 'action', align: 'center', sortable: false},
        {text: this.$t('cleanup.admin.items.state'), value: 'state', align: 'center', sortable: false},
        {text: this.$t('cleanup.admin.items.fileSize'), value: 'fileSize', align: 'center'},
        {text: this.$t('cleanup.admin.items.versionsSize'), value: 'versionsSize', align: 'center', sortable: false},
        {text: this.$t('cleanup.admin.items.reclaimedBytes'), value: 'reclaimedBytes', align: 'center', sortable: false},
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
  },
  created() {
    document.addEventListener('click', this.closeMenus);
    this.loadItems();
  },
  beforeDestroy() {
    document.removeEventListener('click', this.closeMenus);
  },
  methods: {
    reload() {
      this.options = {...this.options, page: 1};
      this.loadItems();
    },
    loadItems() {
      this.loading = true;
      const {page, itemsPerPage, sortBy, sortDesc} = this.options;
      return this.$cleanupService.getCampaignItems(this.campaignId, {
        state: this.stateFilter,
        action: this.actionFilter,
        minSize: this.minSizeMb != null && this.minSizeMb !== '' ? Math.round(this.minSizeMb * MEGA_BYTE) : null,
        page: page - 1,
        size: itemsPerPage,
        sort: `${sortBy[0] || 'fileSize'},${sortDesc[0] === false ? 'asc' : 'desc'}`,
      }).then(data => {
        this.items = data?.items || [];
        this.totalItems = data?.totalItems || 0;
      }).catch(() => {
        document.dispatchEvent(new CustomEvent('notification-alert', {detail: {
          message: this.$t('cleanup.admin.items.loadError'),
          type: 'error',
        }}));
      }).finally(() => this.loading = false);
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
