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
  <div
    class="documentTranscriptSpeaker d-flex align-center"
    @click.stop
    @keydown.enter.stop>
    <user-avatar
      v-if="person"
      :profile-id="person.username"
      :identity="avatarIdentity"
      :size="20"
      extra-class="text-subtitle-2" />
    <!-- keyed by its activator: Vuetify moves the activator out of the hidden menu once, at mount, so a new activator element needs a new menu -->
    <v-menu
      v-if="!person || canEdit"
      :key="person ? 'change' : 'name'"
      v-model="menu"
      :disabled="!canEdit || busy"
      :close-on-content-click="false"
      class="d-flex"
      offset-y
      max-width="300">
      <template #activator="{on, attrs}">
        <v-btn
          v-if="person"
          v-bind="attrs"
          :title="$t('documents.transcript.speaker.change')"
          :aria-label="$t('documents.transcript.speaker.change')"
          :disabled="busy"
          class="documentTranscriptSpeakerChange"
          icon
          small
          v-on="on"
          @keydown.space.stop>
          <v-icon size="14">fas fa-pen</v-icon>
        </v-btn>
        <span
          v-else
          v-bind="attrs"
          :title="canEdit && $t('documents.transcript.speaker.name') || ''"
          :class="canEdit && 'clickable'"
          :role="canEdit ? 'button' : null"
          :tabindex="canEdit ? 0 : null"
          class="d-inline-flex align-center"
          v-on="on"
          @keydown.enter.stop.prevent="openFromKeyboard"
          @keydown.space.stop.prevent="openFromKeyboard">
          <v-chip
            :color="color"
            x-small
            outlined>
            {{ $t('documents.transcript.speaker.label', {0: speaker}) }}
          </v-chip>
        </span>
      </template>
      <v-card class="pa-3" flat>
        <div class="text-subtitle-2 mb-2">{{ $t('documents.transcript.speaker.who', {0: speaker}) }}</div>
        <identity-suggester
          v-if="menu"
          v-model="chosen"
          :labels="suggesterLabels"
          :search-options="searchOptions"
          :include-spaces="false"
          name="transcriptSpeaker"
          type-of-relations="user_to_invite"
          include-users />
        <div class="d-flex justify-end mt-2">
          <v-btn
            v-if="person"
            :disabled="saving"
            class="btn me-2"
            small
            @click="unname">
            {{ $t('documents.transcript.speaker.clear') }}
          </v-btn>
          <v-btn
            :disabled="!chosen || saving"
            :loading="saving"
            class="btn btn-primary"
            small
            @click="name">
            {{ $t('documents.transcript.speaker.apply') }}
          </v-btn>
        </div>
      </v-card>
    </v-menu>
  </div>
</template>
<script>
import {speakerColor} from '../js/TranscriptSpeakers.js';

export default {
  props: {
    speaker: {
      type: String,
      required: true,
    },
    person: {
      type: Object,
      default: null,
    },
    canEdit: {
      type: Boolean,
      default: false,
    },
    saving: {
      type: Boolean,
      default: false,
    },
    /** Another speaker is being saved: this one waits, its controls in place but disabled. */
    busy: {
      type: Boolean,
      default: false,
    },
  },
  data: () => ({
    menu: false,
    chosen: null,
    // without options the suggester's user search fails; an empty current user leaves the viewer among the users found
    searchOptions: {
      currentUser: '',
    },
  }),
  computed: {
    /**
     * The person as the avatar reads them, built from what the speakers' answer
     * already holds, so that no header fetches the user again.
     *
     * @returns {object} the identity, or null for an unnamed speaker
     */
    avatarIdentity() {
      return this.person && {
        id: this.person.identityId,
        username: this.person.username,
        fullname: this.person.fullName,
        avatar: this.person.avatarUrl,
        enabled: true,
        deleted: false,
        primaryProperty: null,
        external: false,
      } || null;
    },
    color() {
      return speakerColor(this.speaker);
    },
    suggesterLabels() {
      return {
        placeholder: this.$t('documents.transcript.speaker.search'),
        noDataLabel: this.$t('documents.transcript.speaker.noUser'),
      };
    },
  },
  watch: {
    saving(saving) {
      if (!saving) {
        this.menu = false;
      }
    },
    menu(menu) {
      if (!menu) {
        this.chosen = null;
      }
    },
  },
  methods: {
    /**
     * Opens the menu from the keyboard, Enter or Space as on a button, which
     * the menu's activator does not do for an element that is no button.
     * @returns {void}
     */
    openFromKeyboard() {
      if (this.canEdit && !this.busy) {
        this.menu = true;
      }
    },
    /**
     * Asks to name the speaker after the chosen user.
     * @returns {void}
     */
    name() {
      this.$emit('name', {speaker: this.speaker, user: this.chosen});
    },
    /**
     * Asks to unname the speaker.
     * @returns {void}
     */
    unname() {
      this.$emit('unname', {speaker: this.speaker});
    },
  },
};
</script>
