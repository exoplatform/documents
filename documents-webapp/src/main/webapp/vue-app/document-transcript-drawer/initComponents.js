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
import DocumentTranscriptChapter from './components/DocumentTranscriptChapter.vue';
import DocumentTranscriptChapterList from './components/DocumentTranscriptChapterList.vue';
import DocumentTranscriptHighlight from './components/DocumentTranscriptHighlight.vue';
import DocumentTranscriptPlayer from './components/DocumentTranscriptPlayer.vue';
import DocumentTranscriptSpeaker from './components/DocumentTranscriptSpeaker.vue';
import DocumentTranscriptSpeakerSuggestion from './components/DocumentTranscriptSpeakerSuggestion.vue';

const components = {
  'document-transcript-drawer': DocumentTranscriptDrawer,
  'document-transcript-status': DocumentTranscriptStatus,
  'document-transcript-content': DocumentTranscriptContent,
  'document-transcript-chapter': DocumentTranscriptChapter,
  'document-transcript-chapter-list': DocumentTranscriptChapterList,
  'document-transcript-highlight': DocumentTranscriptHighlight,
  'document-transcript-player': DocumentTranscriptPlayer,
  'document-transcript-speaker': DocumentTranscriptSpeaker,
  'document-transcript-speaker-suggestion': DocumentTranscriptSpeakerSuggestion,
};

for (const key in components) {
  // the details drawer's bundle registers the same transcript components:
  // the first registration stays, so a mounted player is never replaced
  if (!Vue.options.components[key]) {
    Vue.component(key, components[key]);
  }
}
