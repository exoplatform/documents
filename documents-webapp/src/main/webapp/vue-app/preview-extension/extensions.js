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


extensionRegistry.registerExtension('Preview', 'previewExtensions', {
  id: 'video-preview',
  fileType: 'video',
  rank: 20,
  componentOptions: {
    vueComponent: Vue.options.components['documents-audio-video-preview'],
  },
});
extensionRegistry.registerExtension('Preview', 'previewExtensions', {
  id: 'audio-preview',
  fileType: 'audio',
  rank: 30,
  componentOptions: {
    vueComponent: Vue.options.components['documents-audio-video-preview'],
  },
});
