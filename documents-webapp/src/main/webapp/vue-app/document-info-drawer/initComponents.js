/*
 * This file is part of the Meeds project (https://meeds.io/).
 * 
 * Copyright (C) 2020 - 2025 Meeds Association contact@meeds.io
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

import DocumentInfoDrawer from './components/DocumentInfoDrawer.vue';
import DocumentCategories from './components/DocumentCategories.vue';
import DocumentTranscriptButton from '../document-transcript-drawer/components/DocumentTranscriptButton.vue';
import DocumentTranscriptStatus from '../document-transcript-drawer/components/DocumentTranscriptStatus.vue';
import DocumentRecordingDetails from './components/DocumentRecordingDetails.vue';
import DocumentMediaThumbnail from './components/DocumentMediaThumbnail.vue';
import DocumentTranscriptContent from '../document-transcript-drawer/components/DocumentTranscriptContent.vue';
import DocumentTranscriptChapter from '../document-transcript-drawer/components/DocumentTranscriptChapter.vue';
import DocumentTranscriptChapterList from '../document-transcript-drawer/components/DocumentTranscriptChapterList.vue';
import DocumentTranscriptHighlight from '../document-transcript-drawer/components/DocumentTranscriptHighlight.vue';
import DocumentTranscriptPlayer from '../document-transcript-drawer/components/DocumentTranscriptPlayer.vue';
import DocumentInfoMenu from './components/DocumentInfoMenu.vue';
import DocumentInfoMenuItem from './components/DocumentInfoMenuItem.vue';
import DocumentInfoRow from './components/DocumentInfoRow.vue';
import DocumentInfoPerson from './components/DocumentInfoPerson.vue';
import DocumentInfoDetails from './components/DocumentInfoDetails.vue';
import DocumentInfoContent from './components/DocumentInfoContent.vue';
import DocumentInfoVersions from './components/DocumentInfoVersions.vue';
import DocumentInfoTranscript from './components/DocumentInfoTranscript.vue';
import DocumentInfoPath from './components/DocumentInfoPath.vue';

const components = {
  'document-info-drawer': DocumentInfoDrawer,
  'document-categories': DocumentCategories,
  'document-transcript-button': DocumentTranscriptButton,
  'document-transcript-status': DocumentTranscriptStatus,
  'document-recording-details': DocumentRecordingDetails,
  'document-media-thumbnail': DocumentMediaThumbnail,
  'document-transcript-content': DocumentTranscriptContent,
  'document-transcript-chapter': DocumentTranscriptChapter,
  'document-transcript-chapter-list': DocumentTranscriptChapterList,
  'document-transcript-highlight': DocumentTranscriptHighlight,
  'document-transcript-player': DocumentTranscriptPlayer,
  'document-info-menu': DocumentInfoMenu,
  'documents-info-menu-item': DocumentInfoMenuItem,
  'documents-info-row': DocumentInfoRow,
  'document-info-person': DocumentInfoPerson,
  'document-info-details': DocumentInfoDetails,
  'document-info-content': DocumentInfoContent,
  'document-info-versions': DocumentInfoVersions,
  'document-info-transcript': DocumentInfoTranscript,
  'document-info-path': DocumentInfoPath,
};

for (const key in components) {
  // the transcript drawer's bundle registers the same transcript components:
  // the first registration stays, so a mounted player is never replaced
  if (!Vue.options.components[key]) {
    Vue.component(key, components[key]);
  }
}