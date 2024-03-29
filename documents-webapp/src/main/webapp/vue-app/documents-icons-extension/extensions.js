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
 */
 
const presentation = {
  class: 'fas fa-file-powerpoint',
  color: '#CB4B32',
};
const sheet = {
  class: 'fas fa-file-excel',
  color: '#217345',
};
const word = {
  class: 'fas fa-file-word',
  color: '#2A5699',
};
const image = {
  class: 'fas fa-file-image',
  color: '#999999',
};
const video = {
  class: 'fas fa-file-video',
  color: '#79577A',
};
const archive = {
  class: 'fas fa-file-archive',
  color: '#717272',
};
const code = {
  class: 'fas fa-file-code',
  color: '#6cf500',
};
const pdf = {
  class: 'fas fa-file-pdf',
  color: '#FF0000'
};
const text = {
  class: 'fas fa-file-alt',
  color: '#385989',
};
const illustration = {
  class: 'fas fa-file-contract',
  color: '#E79E24',
};
const file = {
  class: 'fas fa-file',
  color: '#476A9C',
};
const folder = {
  class: 'fas fa-folder',
  color: '#476A9C',
};
const documentsMapIconsExtensions = new Map([
  ['application/pdf', pdf],
  ['application/vnd.ms-powerpoint', presentation],
  ['application/vnd.openxmlformats-officedocument.presentationml.presentation', presentation],
  ['application/vnd.oasis.opendocument.presentation', presentation],
  ['application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', sheet],
  ['application/vnd.oasis.opendocument.spreadsheet', sheet],
  ['officedocument.spreadsheetml.sheet', sheet],
  ['application/vnd.ms-excel', sheet],
  ['text/csv', sheet],
  ['application/vnd.openxmlformats-officedocument.wordprocessingml.document', word],
  ['application/msword', word],
  ['application/rtf', word],
  ['application/vnd.oasis.opendocument.text', word],
  ['text/plain', text],
  ['image/webp', image],
  ['image/avif', image],
  ['image/bmp', image],
  ['image/gif', image],
  ['image/jpeg', image],
  ['image/png', image],
  ['image/tiff', image],
  ['video/x-msvideo', video],
  ['video/mp4', video],
  ['video/mpeg', video],
  ['video/ogg', video],
  ['video/webm', video],
  ['video/3gpp', video],
  ['application/zip', archive],
  ['application/vnd.rar', archive],
  ['application/postscript', illustration],
  ['text/html', code],
  ['text/xml', code],
  ['application/xml', code],
  ['text/css', code],
  ['file', file],
  ['folder', folder],
]);

extensionRegistry.registerExtension('documents', 'documents-icons-extension', documentsMapIconsExtensions);