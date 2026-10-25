<!--
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
-->
<template>
  <div class="d-flex align-center text-caption font-weight-bold overflow-hidden">
    <user-avatar
      v-if="person"
      :profile-id="person.username"
      :size="22"
      :popover="false"
      extra-class="text-truncate"
      fullname />
    <template v-else>
      <v-avatar
        :color="speaker && color || 'grey'"
        size="22"
        class="flex-shrink-0 me-2 white--text"
        aria-hidden="true">
        <v-icon
          v-if="!speaker"
          size="11"
          color="white">
          fas fa-users
        </v-icon>
        <span v-else class="documentTimelineNumber">{{ speaker }}</span>
      </v-avatar>
      <span class="text-truncate">{{ name }}</span>
    </template>
  </div>
</template>
<script>
import {speakerColor} from '../../document-transcript-drawer/js/TranscriptSpeakers.js';

export default {
  props: {
    speaker: {
      type: String,
      default: null,
    },
    person: {
      type: Object,
      default: null,
    },
    name: {
      type: String,
      default: null,
    },
  },
  computed: {
    /**
     * The colour of the speaker, as in the transcript.
     * @returns {string} a theme colour
     */
    color() {
      return speakerColor(this.speaker);
    },
  },
};
</script>
