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
  <v-data-table
    :headers="headers"
    :items="campaigns"
    :loading="loading"
    :no-data-text="$t('cleanup.admin.campaigns.empty')"
    :items-per-page="20"
    must-sort
    sort-by="startedDate"
    sort-desc
    @click:row="$emit('open', $event)">
    <template slot="item.state" slot-scope="{item}">
      <v-chip
        :color="$cleanupUtils.campaignStateColor(item.state)"
        :outlined="!$cleanupUtils.isLoudState(item.state)"
        :dark="$cleanupUtils.isLoudState(item.state)"
        small>
        {{ $t(`cleanup.campaign.state.${item.state}`) }}
      </v-chip>
    </template>
    <template slot="item.startedDate" slot-scope="{item}">
      <v-tooltip v-if="item.startedDate" bottom>
        <template #activator="{on, attrs}">
          <span v-bind="attrs" v-on="on">
            <!-- Keyed on the minute tick so the text is RE-COMPUTED: the component
                 renders 'about 32 minutes ago' from its value once, and a row that
                 nothing pushes an event for is never re-rendered otherwise -->
            <relative-date-format :key="minuteTick" :value="new Date(item.startedDate)" />
          </span>
        </template>
        <date-format :value="item.startedDate" :format="$cleanupUtils.DATE_TIME_FORMAT" />
      </v-tooltip>
      <span v-else>-</span>
    </template>
    <template slot="item.progress" slot-scope="{item}">
      <div class="d-flex align-center" style="min-width: 120px">
        <v-progress-linear
          :value="progressOf(item)"
          height="6"
          style="min-width: 60px"
          rounded />
        <span class="ms-2 text-no-wrap caption">{{ progressOf(item) }}%</span>
      </div>
    </template>
    <template slot="item.candidateCount" slot-scope="{item}">
      {{ item.candidateCount }}
    </template>
    <template slot="item.reclaimableBytes" slot-scope="{item}">
      {{ $cleanupSize(item.reclaimableBytes) }}
    </template>
    <template slot="item.actions" slot-scope="{item}">
      <v-btn
        :aria-label="$t('cleanup.admin.campaign.open')"
        icon
        small
        @click.stop="$emit('open', item)">
        <v-icon size="14">fas fa-eye</v-icon>
      </v-btn>
      <!-- Shown only where the SERVER allows it, so the console never offers an
           action it would answer 400 to. A COMPLETED campaign is deliberately
           absent: it is the record of an irreversible purge -->
      <v-btn
        v-if="deletable(item)"
        :aria-label="$t('cleanup.admin.campaign.delete')"
        icon
        small
        @click.stop="$emit('delete', item)">
        <v-icon size="14">fas fa-trash</v-icon>
      </v-btn>
    </template>
  </v-data-table>
</template>
<script>
export default {
  props: {
    // Bumped once a minute by the parent; the relative dates are keyed on it
    minuteTick: {
      type: Number,
      default: 0,
    },
    campaigns: {
      type: Array,
      default: () => [],
    },
    loading: {
      type: Boolean,
      default: false,
    },
  },
  computed: {
    headers() {
      return [
        {
          text: this.$t('cleanup.admin.campaign.name'),
          value: 'name',
          align: 'left',
        },
        {
          text: this.$t('cleanup.admin.campaign.state'),
          value: 'state',
          align: 'center',
        },
        {
          text: this.$t('cleanup.admin.campaign.startedDate'),
          value: 'startedDate',
          align: 'center',
        },
        {
          text: this.$t('cleanup.admin.campaign.progress'),
          value: 'progress',
          align: 'center',
          sortable: false,
        },
        {
          text: this.$t('cleanup.admin.campaign.candidates'),
          value: 'candidateCount',
          align: 'center',
        },
        {
          text: this.$t('cleanup.admin.campaign.reclaimable'),
          value: 'reclaimableBytes',
          align: 'center',
        },
        {
          text: this.$t('cleanup.admin.campaign.actions'),
          value: 'actions',
          align: 'center',
          sortable: false,
        },
      ];
    },
  },
  methods: {
    // The server's DELETABLE_STATES, mirrored: DRAFT, SIMULATED, CANCELLED
    deletable(campaign) {
      return ['DRAFT', 'SIMULATED', 'CANCELLED'].includes(campaign?.state);
    },
    progressOf(campaign) {
      return this.$cleanupUtils.progressPercentage(campaign.processedCount, campaign.totalCount);
    },
  }
};
</script>
