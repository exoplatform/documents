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
  <component
    :is="isMobile && 'v-bottom-sheet' || 'v-menu'"
    ref="trashActionMenu"
    v-model="menu"
    :attach="isMobile && '#vuetify-apps'"
    :left="!$vuetify.rtl"
    :right="$vuetify.rtl"
    transition="slide-x-reverse-transition"
    content-class="position-absolute application-menu z-index-modal"
    offset-y
    eager>
    <template #activator="{on, attrs}">
      <v-btn
        v-bind="attrs"
        v-on="on"
        :loading="loading"
        icon>
        <v-icon size="20">fa-ellipsis-v</v-icon>
      </v-btn>
    </template>
    <v-list
      :max-width="!isMobile && 300 || 'auto'"
      :class="isMobile && 'border-top-left-radius border-top-right-radius'"
      dense>
      <v-list-item
        class="action-menu-item d-flex align-center"
        dense
        @click="restoreDocument()">
        <v-icon size="16">fa-undo</v-icon>
        <v-list-item-title class="ps-2">
          <span>{{ $t('trash.element.restore') }}</span>
        </v-list-item-title>
      </v-list-item>
      <v-list-item
        class="action-menu-item d-flex align-center"
        dense
        @click="openConfirmDialog">
        <v-icon class="error--text" size="16">fa-trash</v-icon>
        <v-list-item-title class="ps-2">
          <span class="error--text">{{ $t('trash.element.delete') }}</span>
        </v-list-item-title>
        <confirm-dialog
          v-if="dialog"
          ref="dialog"
          :title="$t('trash.element.delete.confirm.title')"
          :message="$t('trash.element.delete.confirm.message')"
          :ok-label="$t('trash.element.delete')"
          :cancel-label="$t('trash.element.delete.cancel')"
          @ok="deleteDocumentPermanently"
          @closed="close" />
      </v-list-item>
    </v-list>
  </component>
</template>
<script>
export default {
  props: {
    trashElementItem: {
      type: Object,
      default: null,
    },
  },
  data: () => ({
    id: Math.random(), // NOSONAR
    menu: false,
    loading: false,
    dialog: false
  }),
  computed: {
    isMobile() {
      return this.$vuetify.breakpoint.name === 'xs' || this.$vuetify.breakpoint.name === 'sm';
    }
  },
  watch: {
    menu() {
      // Workaround to fix closing menu when clicking outside
      if (this.menu) {
        document.addEventListener('mousedown', this.closeMenu);
      } else {
        document.removeEventListener('mousedown', this.closeMenu);
      }
    },
  },
  methods: {
    restoreDocument() {
      this.loading = true;
      this.$trashManagementService.restoreDocument(this.trashElementItem.path).then(() => {
        this.displayAlert(this.$t('trash.element.restore.message.success'));
        this.$root.$emit('trash-elements-updated');
      }).catch((error) => {
        console.error('Error restoring trash elements:', error);
        this.displayAlert(this.$t('trash.element.restore.message.error'), 'error');
      }).finally(() => this.loading = false);
    },
    deleteDocumentPermanently() {
      this.loading = true;
      this.$trashManagementService.deleteDocumentPermanently(this.trashElementItem.path).then(() => {
        this.displayAlert(this.$t('trash.element.delete.message.success'));
        this.$root.$emit('trash-elements-updated');
      }).catch((error) => {
        console.error('Error deleting trash elements:', error);
        this.displayAlert(this.$t('trash.element.delete.message.error'), 'error');
      }).finally(() => this.loading = false);
    },
    displayAlert(message, type) {
      document.dispatchEvent(new CustomEvent('notification-alert', {detail: {
        message,
        type: type || 'success',
      }}));
    },
    closeMenu() {
      this.menu = false;
    },
    openConfirmDialog() {
      this.dialog = true;
      this.$nextTick().then(() => window.setTimeout(() => this.$refs.dialog.open(), 200));
    },
    close() {
      window.setTimeout(() => this.dialog = false, 200);
    },
  }
};
</script>