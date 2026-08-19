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
  <div class="d-flex flex-column align-center py-10">
    <v-icon size="48" class="text-light-color mb-4">fas fa-check-circle</v-icon>
    <div class="text-color">{{ $t('cleanup.review.outcome.title') }}</div>
    <div v-if="outcome" class="d-flex flex-wrap justify-center mt-4">
      <div class="mx-6 text-center">
        <div class="text-color font-weight-bold">{{ outcome.deletedCount }}</div>
        <div class="text-light-color caption">{{ $t('cleanup.review.outcome.deleted') }}</div>
      </div>
      <div class="mx-6 text-center">
        <div class="text-color font-weight-bold">{{ $cleanupSize(outcome.freedBytes) }}</div>
        <div class="text-light-color caption">{{ $t('cleanup.review.outcome.freed') }}</div>
      </div>
      <div class="mx-6 text-center">
        <div class="text-color font-weight-bold">{{ outcome.keptCount }}</div>
        <div class="text-light-color caption">{{ $t('cleanup.review.outcome.kept') }}</div>
      </div>
    </div>
  </div>
</template>
<script>
export default {
  props: {
    summary: {
      type: Object,
      default: null,
    },
  },
  computed: {
    outcome() {
      return this.summary?.outcome;
    },
  },
};
</script>
