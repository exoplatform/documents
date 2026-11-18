/*
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
*/
import RecycleBin from './components/RecycleBin.vue';
import RecycleBinToolbar from './components/RecycleBinToolbar.vue';
import RecycleBinConfirmDialog from './components/RecycleBinConfirmDialog.vue';
import RecycleBinList from './components/list/RecycleBinList.vue';
import RecycleBinItem from './components/list/RecycleBinItem.vue';
import RecycleBinSpaceSettings from './components/settings/RecycleBinSpaceSettings.vue';

const components = {
  'documents-recycle-bin': RecycleBin,
  'documents-recycle-bin-toolbar': RecycleBinToolbar,
  'documents-recycle-bin-confirm-dialog': RecycleBinConfirmDialog,
  'documents-recycle-bin-list': RecycleBinList,
  'documents-recycle-bin-item': RecycleBinItem,
  'documents-recycle-bin-space-settings': RecycleBinSpaceSettings,
};

for (const key in components) {
  Vue.component(key, components[key]);
}
