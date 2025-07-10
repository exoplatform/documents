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
import DocumentList from './components/DocumentList.vue';
import DocumentListWidgetItem from './components/DocumentListWidgetItem.vue';
import DocumentListSettingsDrawer from './components/DocumentListSettingsDrawer.vue';
import DocumentListWidgetItemCardVue from './components/DocumentListWidgetItemCard.vue.vue';

const components = {
  'document-list': DocumentList,
  'document-list-widget-item': DocumentListWidgetItem,
  'document-list-widget-item-card': DocumentListWidgetItemCardVue,
  'document-list-settings-drawer': DocumentListSettingsDrawer,
};

for (const key in components) {
  Vue.component(key, components[key]);
}
