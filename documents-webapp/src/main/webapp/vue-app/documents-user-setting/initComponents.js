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

import DocumentsUserSettings from './components/DocumentsUserSettings.vue';

import DocumentsWebdavDrawer from './components/drawers/DocumentsWebdavDrawer.vue';
import DocumentsWebdavConfirmAccessDrawer from './components/drawers/DocumentsWebdavConfirmAccessDrawer.vue';
import DocumentsWebdavMapDrivesDrawer from './components/drawers/DocumentsWebdavMapDrivesDrawer.vue';
import DocumentsWebdavRegenerateAccessDrawer from './components/drawers/DocumentsWebdavRegenerateAccessDrawer.vue';

const components = {
  'documents-user-settings': DocumentsUserSettings,
  'documents-webdav-drawer': DocumentsWebdavDrawer,
  'documents-webdav-confirm-access-drawer': DocumentsWebdavConfirmAccessDrawer,
  'documents-webdav-map-drives-drawer': DocumentsWebdavMapDrivesDrawer,
  'documents-webdav-regenerate-access-drawer': DocumentsWebdavRegenerateAccessDrawer,
};

for (const key in components) {
  Vue.component(key, components[key]);
}
