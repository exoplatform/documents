/*
 * This file is part of the Meeds project (https://meeds.io/).
 *
 * Copyright (C) 2020 - 2023 Meeds Association contact@meeds.io
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */

import DocumentsWidgetApp from './components/DocumentsWidgetApp.vue';

import DocumentsWidgetSettingsDrawer from './components/settings/DocumentsWidgetSettingsDrawer.vue';
import DocumentsWidgetFormDrawer from './components/settings/DocumentsWidgetDrawer.vue';
import DocumentsWidgetDisplayPreview from './components/settings/DocumentsWidgetPreview.vue';
import DocumentsWidgetInput from './components/settings/DocumentsWidgetInput.vue';
import DocumentsWidgetIconInput from './components/settings/DocumentsWidgetIconInput.vue';

import DocumentsWidgetList from './components/view/DocumentsWidgetList.vue';
import DocumentsWidgetHeader from './components/view/DocumentsWidgetHeader.vue';
import DocumentsWidgetIcon from './components/view/DocumentsWidgetIcon.vue';
import DocumentsWidgetColumn from './components/view/DocumentsWidgetColumn.vue';
import DocumentsWidgetCard from './components/view/DocumentsWidgetCard.vue';

const components = {
  'documents-widget-app': DocumentsWidgetApp,
  'documents-widget-settings-drawer': DocumentsWidgetSettingsDrawer,
  'documents-widget-form-drawer': DocumentsWidgetFormDrawer,
  'documents-widget-display-preview': DocumentsWidgetDisplayPreview,
  'documents-widget-input': DocumentsWidgetInput,
  'documents-widget-icon-input': DocumentsWidgetIconInput,
  'documents-widget-list': DocumentsWidgetList,
  'documents-widget-header': DocumentsWidgetHeader,
  'documents-widget-icon': DocumentsWidgetIcon,
  'documents-widget-column': DocumentsWidgetColumn,
  'documents-widget-card': DocumentsWidgetCard,
};

for (const key in components) {
  Vue.component(key, components[key]);
}
