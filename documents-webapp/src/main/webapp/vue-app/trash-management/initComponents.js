/*
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
*/

import TrashManagement from './components/main/TrashManagement.vue';
import TrashManagementToolbar from './components/main/TrashManagementToolbar.vue';
import DocumentsTrashList from './components/main/DocumentsTrashList.vue';
import DocumentsTrashItem from './components/item/DocumentsTrashItem.vue';
import DocumentsTrashItemMenu from './components/menu/DocumentsTrashItemMenu.vue';
import DocumentsTrashItemsBulkDelete from './components/bulk-action/DocumentsTrashItemsBulkDelete.vue';

const components = {
  'trash-management': TrashManagement,
  'trash-management-toolbar': TrashManagementToolbar,
  'documents-trash-list': DocumentsTrashList,
  'document-trash-item': DocumentsTrashItem,
  'document-trash-item-menu': DocumentsTrashItemMenu,
  'documents-trash-items-bulk-delete': DocumentsTrashItemsBulkDelete
};

for (const key in components) {
  Vue.component(key, components[key]);
}
