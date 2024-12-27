<!--
 * Copyright (C) 2024 eXo Platform SAS.
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
  <v-card
    class="d-flex flex-column mt-2"
    flat>
    <div id="documentsTrashListBody" class="flex-grow-1 flex-shrink-1 pt-2">
      <v-data-table
        v-if="initDone"
        :headers="headers"
        :items="trashElements"
        v-model="selectedElements"
        :loading="loading"
        :options.sync="options"
        :server-items-length="totalSize"
        :footer-props="{ itemsPerPageOptions }"
        :show-select="!isMobile"
        must-sort
        @update:options="handleOptionsChange">
        <template
          v-if="!isMobile"
          slot="header.data-table-select"
          slot-scope="{on, props}">
          <v-checkbox
            v-on="on"
            v-bind="props"
            on-icon="fas fa-check-square fa-lg primary--text"
            indeterminate-icon="fas fa-minus-square fa-lg"
            off-icon="far fa-square fa-lg"
            class="my-auto pt-2"
            @change="on.input" />
        </template>
        <template v-if="selectedElements.length" slot="body.prepend">
          <tr>
            <td :colspan="headers.length + 1" class="px-0">
              <v-alert
                :icon="false"
                class="ma-0 ps-5 no-border-radius"
                border="left"
                type="info"
                colored-border>
                <div v-html="selectionLabel"></div>
              </v-alert>
            </td>
          </tr>
        </template>
        <template slot="item" slot-scope="props">
          <document-trash-item
            :key="props.item.id"
            :item="props.item"
            :selected="props.isSelected"
            :select="props.select"
            :headers="headers" />
        </template>
      </v-data-table>
    </div>
  </v-card>
</template>

<script>

export default {
  data: () => ({
    loading: false,
    initDone: false,
    trashElements: [],
    selectedElements: [],
    options: {
      page: 1,
      itemsPerPage: 20,
      sortBy: ['modifiedDate'],
      sortDesc: [true],
    },
    totalSize: 0,
    itemsPerPageOptions: [20, 50, 100],
  }),
  computed: {
    headers() {
      return [
        {
          text: this.$t('trash.element.name'),
          value: 'name',
          align: 'left',
          sortable: !this.isMobile,
          class: 'trash-element-name-header pe-2',
          width: '45%'
        },
        {
          text: this.$t('trash.element.deletion.date'),
          value: 'modifiedDate',
          align: 'center',
          sortable: !this.isMobile,
          class: 'trash-element-deletion-header px-1',
          width: '20%'
        },
        {
          text: this.$t('trash.element.origin'),
          value: 'origin',
          align: 'center',
          sortable: false,
          class: 'trash-element-origin-header px-1',
          width: '20%'
        },
        {
          text: this.$t('trash.element.actions'),
          value: 'actions',
          align: 'center',
          sortable: false,
          class: 'trash-element-actions-header px-1',
          width: '15%'
        },
      ];
    },
    selectionLabel() {
      return this.$t('trash.element.selected', {
        0: `<strong>${this.selectedElements.length}</strong>`,
      });
    },
    isMobile() {
      return this.$vuetify.breakpoint.name === 'xs' || this.$vuetify.breakpoint.name === 'sm';
    }
  },
  created() {
    this.fetchTrashElements();
    this.$root.$on('trash-elements-updated', (() => this.updateTrashElements()));
  },
  methods: {
    fetchTrashElements() {
      this.loading = true;
      const {page, itemsPerPage, sortBy, sortDesc} = this.options;
      const offset = (page - 1) * itemsPerPage;
      const sortField = sortBy[0] || 'modifiedDate';
      const sortOrder = sortDesc[0] ? 'desc' : 'asc';
      this.$trashManagementService.getDeletedDocuments(itemsPerPage, offset, sortField, sortOrder).then((data) => {
        this.trashElements = data.entities;
        this.totalSize = data.size || 0;
      }).catch((error) => {
        console.error('Error fetching trash elements:', error);
      }).finally(() => {
        this.loading = false;
        this.initDone = true;
      });
    },
    handleOptionsChange(newOptions) {
      this.options = {...newOptions };
      this.fetchTrashElements();
    },
    updateTrashElements() {
      this.fetchTrashElements();
    }
  },
};
</script>