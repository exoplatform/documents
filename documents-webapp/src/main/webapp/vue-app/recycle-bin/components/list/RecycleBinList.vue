<!--

 Copyright (C) 2026 eXo Platform SAS.

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU Affero General Public License
 as published by the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 GNU Affero General Public License for more details.

 You should have received a copy of the GNU Affero General Public License
 along with this program. If not, see <gnu.org/licenses>.

-->
<template>
  <v-data-table
    id="recycleBinItems"
    v-model="selection"
    :headers="headers"
    :items="items"
    :loading="loading || busy"
    :options.sync="options"
    :server-items-length="total"
    :footer-props="{itemsPerPageOptions}"
    :hide-default-footer="total <= smallestPage"
    :no-data-text="$t('recycleBin.list.empty')"
    :loading-text="$t('recycleBin.list.loading')"
    class="recycle-bin-items"
    item-key="id"
    mobile-breakpoint="0"
    show-select
    must-sort>
    <template #[`header.data-table-select`]="{props, on}">
      <v-checkbox
        :input-value="props.value"
        :indeterminate="props.indeterminate"
        :aria-label="$t('recycleBin.list.selectAll')"
        :disabled="busy || !items.length"
        class="recycle-bin-select-all ma-0 pa-0"
        hide-details
        dense
        @change="on.input(!!$event)" />
    </template>
    <template #item="{item, isSelected, select}">
      <documents-recycle-bin-item
        :key="item.id"
        :item="item"
        :drive="drive"
        :selected="isSelected"
        :mobile="isMobile"
        :busy="busy"
        @select="select"
        @view="$emit('view', $event)"
        @restore="$emit('restore', $event)"
        @delete="$emit('delete', $event)" />
    </template>
  </v-data-table>
</template>
<script>
export default {
  props: {
    bin: {
      type: Object,
      required: true,
    },
    name: {
      type: String,
      default: null,
    },
    // The drive of the bin, as a line shows it before the folder an item was in
    drive: {
      type: Object,
      default: null,
    },
    busy: {
      type: Boolean,
      default: false,
    },
  },
  data: () => ({
    loading: false,
    items: [],
    selection: [],
    total: 0,
    options: {
      page: 1,
      itemsPerPage: 20,
      sortBy: ['deletedDate'],
      sortDesc: [true],
    },
    itemsPerPageOptions: [20, 50, 100],
    lastQuery: 0,
  }),
  computed: {
    isMobile() {
      return this.$vuetify.breakpoint.smAndDown;
    },
    // A list that fits in the smallest page shows no paging.
    smallestPage() {
      return Math.min(...this.itemsPerPageOptions);
    },
    headers() {
      const columns = [
        {value: 'name', align: 'start'},
        {value: 'originalLocation', width: '270px', desktop: true},
        {value: 'deletedDate', width: '160px'},
        {value: 'deletedBy', width: '120px', desktop: true},
        {value: 'size', width: '100px', desktop: true},
        {value: 'actions', width: '140px', sortable: false},
      ];
      return columns
        .filter(column => !column.desktop || !this.isMobile)
        .map(column => ({
          text: this.$t(`recycleBin.column.${column.value}`),
          value: column.value,
          align: column.align || 'center',
          sortable: column.sortable !== false,
          width: column.width,
          class: `recycle-bin-column-${column.value} text-no-wrap`,
        }));
    },
    // What the list shows: it is read again only when one of these changes.
    query() {
      const {page, itemsPerPage, sortBy, sortDesc} = this.options;
      return JSON.stringify({
        bin: this.bin,
        name: this.name || '',
        sortField: sortBy?.[0] || 'deletedDate',
        sortDirection: sortDesc?.[0] === false && 'asc' || 'desc',
        offset: (page - 1) * itemsPerPage,
        limit: itemsPerPage,
      });
    },
  },
  watch: {
    query() {
      this.refresh();
    },
    name() {
      if (this.options.page !== 1) {
        this.options = Object.assign({}, this.options, {page: 1});
      }
    },
    selection() {
      this.$emit('selection', this.selection);
      // The browser restores the native state of the box after a click on it:
      // it is set again once the click is over, for a screen reader to tell it.
      window.setTimeout(() => {
        const input = this.$el?.querySelector?.('.recycle-bin-select-all input');
        if (input) {
          input.checked = !!this.items.length && this.selection.length === this.items.length;
        }
      });
    },
  },
  created() {
    this.refresh();
  },
  methods: {
    refresh() {
      const {bin, name, sortField, sortDirection, offset, limit} = JSON.parse(this.query);
      const queryId = ++this.lastQuery;
      this.loading = true;
      return this.$recycleBinService.getItems(bin, name, sortField, sortDirection, offset, limit)
        .then(data => {
          // A slower answer of an earlier question never replaces a later one.
          if (queryId === this.lastQuery) {
            this.items = data?.entities || [];
            this.total = data?.size || 0;
            this.selection = [];
            this.$emit('total', this.total);
            if (!this.items.length && this.total && this.options.page > 1) {
              this.options = Object.assign({}, this.options, {page: 1});
            }
          }
        })
        .catch(error => {
          if (queryId === this.lastQuery) {
            this.items = [];
            this.total = 0;
            this.selection = [];
            this.$emit('total', 0);
            this.$emit('error', error);
          }
        })
        .finally(() => {
          if (queryId === this.lastQuery) {
            this.loading = false;
          }
        });
    },
  },
};
</script>
