/*
 * Copyright (C) 2023 eXo Platform SAS.
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

extensionRegistry.registerExtension('WebNotification', 'notification-group-extension', {
  rank: 40,
  name: 'documents',
  plugins: [
    'AddDocumentCollaboratorPlugin',
    'ImportDocumentsPlugin'
  ],
  icon: 'fa-folder-open',
});

extensionRegistry.registerExtension('WebNotification', 'notification-content-extension', {
  type: 'AddDocumentCollaboratorPlugin',
  rank: 10,
  vueComponent: Vue.options.components['user-notification-add-document-collaborator'],
});

extensionRegistry.registerExtension('WebNotification', 'notification-content-extension', {
  type: 'ImportDocumentsPlugin',
  rank: 10,
  vueComponent: Vue.options.components['user-notification-import-documents'],
});