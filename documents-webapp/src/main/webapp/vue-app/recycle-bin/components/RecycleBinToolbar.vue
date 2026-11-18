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
  <div id="recycleBinToolbar" class="recycle-bin-toolbar d-flex align-center py-3">
    <template v-if="selectionLength">
      <v-btn
        id="recycleBinBulkDelete"
        :disabled="busy"
        class="btn btn-primary error-color-background text-truncate me-2"
        style="background-image: none !important"
        @click="$emit('delete-selection')">
        <v-icon
          class="me-2"
          size="16"
          dark>
          fa-trash
        </v-icon>
        <span class="text-truncate text-none">{{ $t('recycleBin.action.delete') }}</span>
      </v-btn>
      <v-btn
        id="recycleBinBulkRestore"
        :disabled="busy"
        class="btn btn-primary text-truncate"
        @click="$emit('restore-selection')">
        <v-icon
          class="me-2"
          size="16"
          dark>
          fa-redo
        </v-icon>
        <span class="text-truncate text-none">{{ $t('recycleBin.action.restore') }}</span>
      </v-btn>
      <span class="d-sr-only" aria-live="polite">
        {{ $t('recycleBin.selection.count', {0: selectionLength}) }}
      </span>
    </template>
    <v-btn
      v-else
      id="recycleBinEmptyButton"
      :disabled="busy || !canEmpty"
      class="btn btn-primary text-truncate"
      @click="$emit('empty')">
      <v-icon
        class="me-2"
        size="16"
        dark>
        fa-trash
      </v-icon>
      <span class="text-truncate text-none">{{ $t('recycleBin.action.empty') }}</span>
    </v-btn>
    <v-spacer />
    <v-text-field
      v-if="filtering"
      id="recycleBinFilter"
      ref="filter"
      v-model="term"
      :placeholder="$t('recycleBin.filter.placeholder')"
      :aria-label="$t('recycleBin.filter.placeholder')"
      class="recycle-bin-filter flex-grow-0 pa-0 mt-0 me-2"
      style="width: 260px; max-width: 60%"
      autocomplete="off"
      hide-details
      clearable
      @blur="closeEmptyFilter" />
    <v-btn
      id="recycleBinFilterButton"
      :title="$t('recycleBin.filter.placeholder')"
      :aria-label="$t('recycleBin.filter.placeholder')"
      :aria-expanded="filtering && 'true' || 'false'"
      small
      icon
      @click="toggleFilter">
      <v-icon :class="term && 'primary--text' || 'icon-default-color'" size="20">fa-filter</v-icon>
    </v-btn>
  </div>
</template>
<script>
export default {
  props: {
    selectionLength: {
      type: Number,
      default: 0,
    },
    canEmpty: {
      type: Boolean,
      default: false,
    },
    busy: {
      type: Boolean,
      default: false,
    },
  },
  data: () => ({
    filtering: false,
    term: null,
    typing: null,
    waitForTyping: 300,
  }),
  watch: {
    term() {
      window.clearTimeout(this.typing);
      this.typing = window.setTimeout(() => this.$emit('filter', this.term?.trim?.() || null), this.waitForTyping);
    },
  },
  beforeDestroy() {
    window.clearTimeout(this.typing);
  },
  methods: {
    // The field takes the place beside its icon, and leaves it once emptied.
    toggleFilter() {
      if (this.filtering && !this.term) {
        this.filtering = false;
      } else {
        // The one who asked for the field types in it at once.
        this.filtering = true;
        this.$nextTick(() => this.$refs.filter?.focus?.());
      }
    },
    closeEmptyFilter(event) {
      // A click on the icon closes the field by itself: closing it here too would open it again.
      if (!this.term && event?.relatedTarget?.id !== 'recycleBinFilterButton') {
        this.filtering = false;
      }
    },
  },
};
</script>
