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
  <div class="d-inline">
    <v-btn
      color="error"
      elevation="0"
      outlined
      :disabled="isDeleteButtonDisabled"
      @click="openConfirmDialog">
      <v-icon size="16" class="me-2">fa-trash</v-icon>
      {{ $t('trash.element.delete') }}
    </v-btn>
    <confirm-dialog
      v-if="dialog"
      ref="deleteBulkDialog"
      :title="deleteDialogTitle"
      :message="deleteDialogMessage"
      :ok-label="$t('trash.element.delete')"
      :cancel-label="$t('trash.element.delete.cancel')"
      @ok="$emit('delete-items')"
      @closed="close" />
  </div>
</template>
<script>
export default {
  props: {
    selectionLength: {
      type: Number,
      default: () => 0
    },
    bulkActionProgress: {
      type: Boolean,
      default: false
    }
  },
  data() {
    return {
      dialog: false
    };
  },
  computed: {
    deleteDialogTitle() {
      return this.$t(this.selectionLength === 1 && 'trash.element.bulk.delete.confirm.title' || 'trash.elements.bulk.delete.confirm.title', {
        0: this.selectionLength,
      });
    },
    deleteDialogMessage() {
      return this.$t(this.selectionLength === 1 && 'trash.element.delete.confirm.message' || 'trash.elements.bulk.delete.confirm.message', {
        0: this.selectionLength,
        1: this.selectionLength,
      });
    },
    isDeleteButtonDisabled() {
      return this.bulkActionProgress;
    }
  },
  methods: {
    openConfirmDialog() {
      this.dialog = true;
      this.$nextTick().then(() => window.setTimeout(() => this.$refs.deleteBulkDialog.open(), 200));
    },
    close() {
      window.setTimeout(() => this.dialog = false, 200);
    },
  },
};
</script>