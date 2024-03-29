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
 *
*/
import DownloadDocument from './components/DownloadDocument.vue';
import DownloadDocumentMain from './components/DownloadDocumentMain.vue';

const components = {
  'download-document': DownloadDocument,
  'download-document-main': DownloadDocumentMain
};

for (const key in components) {
  Vue.component(key, components[key]);
}