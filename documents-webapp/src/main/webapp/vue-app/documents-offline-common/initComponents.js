/*
 * Copyright (C) 2025 eXo Platform SAS.
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
 */

import OfflineDocumentItem from './components/OfflineDocumentItem.vue';
import OfflineDocumentPreviewDialog from './components/OfflineDocumentPreviewDialog.vue';
import OfflineDocumentDrawer from './components/OfflineDocumentDrawer.vue';
import OfflineDocumentsButton from './components/OfflineDocumentsButton.vue';

const components = {
  'documents-offline-item': OfflineDocumentItem,
  'documents-offline-preview-dialog': OfflineDocumentPreviewDialog,
  'documents-offline-drawer': OfflineDocumentDrawer,
  'documents-offline-button': OfflineDocumentsButton,
};

for (const key in components) {
  Vue.component(key, components[key]);
}
