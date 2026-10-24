/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <gnu.org/licenses>.
 */

import DocumentTranscriptDrawer from './components/DocumentTranscriptDrawer.vue';
import DocumentTranscriptStatus from './components/DocumentTranscriptStatus.vue';
import DocumentTranscriptContent from './components/DocumentTranscriptContent.vue';
import DocumentTranscriptHighlight from './components/DocumentTranscriptHighlight.vue';
import DocumentTranscriptPlayer from './components/DocumentTranscriptPlayer.vue';

const components = {
  'document-transcript-drawer': DocumentTranscriptDrawer,
  'document-transcript-status': DocumentTranscriptStatus,
  'document-transcript-content': DocumentTranscriptContent,
  'document-transcript-highlight': DocumentTranscriptHighlight,
  'document-transcript-player': DocumentTranscriptPlayer,
};

for (const key in components) {
  Vue.component(key, components[key]);
}
