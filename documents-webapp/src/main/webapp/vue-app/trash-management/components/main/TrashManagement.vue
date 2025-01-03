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
  <v-app>
    <v-card class="application-body" flat>
      <trash-management-toolbar
        :selection-length="selectedElements.length"
        :bulk-action-progress="bulkActionProgress"
        @delete-items="deleteSelectedItems" />
      <documents-trash-list
        ref="documentsTrashList"
        class="px-5"
        :bulk-action-progress="bulkActionProgress"
        @update-selection="updateSelection" />
    </v-card>
  </v-app>
</template>
<script>
export default {
  data() {
    return {
      selectedElements: [],
      bulkActionProgress: false
    };
  },
  created() {
    this.initSettings();
  },
  methods: {
    initSettings() {
      return this.$documentFileService.getUserSettings()
        .then(settings => {
          if (settings) {
            this.$documentsWebSocket.initCometd(settings.cometdContextName, settings.cometdToken, this.handleBulkActionNotif);
          }
        });
    },
    handleBulkActionNotif(actionData) {
      const actionName = actionData.actionType.toLowerCase();
      const actionStatus = actionData.status.toLowerCase();
      if (actionName === 'permanently_delete'){
        if (actionStatus === 'done_successfully') {
          this.$root.$emit('trash-elements-updated');
          this.displayAlert(this.$t('trash.elements.bulk.delete.success.message', {0: actionData?.treatedItemsIds?.length}));
        } else {
          this.$root.$emit('trash-elements-updated');
        }
        this.bulkActionProgress = false;
      }
    },
    updateSelection(items) {
      this.selectedElements = items;
    },
    deleteSelectedItems() {
      if (this.selectedElements.length === 1 ) {
        const item = this.selectedElements[0];
        this.deleteSingleDocument(item.path);
      } else {
        this.deleteMultipleDocuments();
      }
    },
    deleteSingleDocument(documentPath) {
      this.$trashManagementService.deleteDocumentPermanently(documentPath).then(() => {
        this.displayAlert(this.$t('trash.element.delete.message.success'));
        this.$root.$emit('trash-elements-updated');
      }).catch((error) => {
        console.error('Error deleting trash elements:', error);
        this.displayAlert(this.$t('trash.element.delete.message.error'), 'error');
      }).finally(() => this.loading = false);
    },
    deleteMultipleDocuments() {
      const max = Math.floor(9999);
      const random = crypto.getRandomValues(new Uint32Array(1))[0];
      const actionId = random % max;
      this.bulkActionProgress = true;
      this.$trashManagementService.deleteDocumentsPermanently(actionId, this.selectedElements).catch((error) => {
        console.error('Error deleting trash elements:', error);
        this.bulkActionProgress = false;
        this.displayAlert(this.$t('trash.element.delete.message.error'), 'error');
      });
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
