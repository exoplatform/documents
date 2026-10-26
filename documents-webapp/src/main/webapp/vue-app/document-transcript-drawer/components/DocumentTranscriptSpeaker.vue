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
    <div
      v-if="person && person.guest"
      class="d-flex align-center text-truncate">
      <v-avatar
        :size="20"
        color="grey lighten-2"
        class="flex-shrink-0 me-2">
        <span class="documentTranscriptSpeakerInitials text-sub-title">{{ guestInitials }}</span>
      </v-avatar>
      <span class="text-subtitle-2 text-truncate">{{ person.fullName }}</span>
      <span class="text-caption text-sub-title text-no-wrap ms-1">{{ $t('documents.transcript.speaker.guest') }}</span>
    </div>
    <user-avatar
      v-else-if="person"
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
      <v-card
        class="pa-3"
        flat>
        <div class="text-subtitle-2 mb-2">{{ $t('documents.transcript.speaker.who', {0: speaker}) }}</div>
        <!-- above the field: the person list opens below it and would cover the offer; the line is reserved so the field never moves while typing -->
        <div class="documentTranscriptSpeakerGuest mb-1">
          <v-list-item
            v-if="guestOffered"
            :input-value="guestChosen"
            class="px-2"
            color="primary"
            dense
            @click="guestChosen = !guestChosen">
            <v-list-item-title class="text-truncate">
              {{ $t('documents.transcript.speaker.useGuest', {0: guestName}) }}
            </v-list-item-title>
          </v-list-item>
        </div>
        <identity-suggester
          v-if="menu"
          v-model="chosen"
          :labels="suggesterLabels"
          :search-options="searchOptions"
          :include-spaces="false"
          name="transcriptSpeaker"
          type-of-relations="user_to_invite"
          include-users
          @input.native="typed = $event.target.value || ''" />
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
            :disabled="!chosen && !guestChosen || saving"
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
    typed: '',
    guestChosen: false,
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
    /**
     * The name typed in the person search, as a guest would be named.
     *
     * @returns {string} the name, trimmed
     */
    guestName() {
      return this.typed.trim();
    },
    /**
     * Tells whether to offer naming the speaker after the typed name, as a
     * guest with no account: once a name is typed and no user is chosen, even
     * when the search finds people, a guest's name being often a first name
     * some user also holds.
     *
     * @returns {boolean} true to offer it
     */
    guestOffered() {
      return !!this.guestName && !this.chosen;
    },
    /**
     * The initials of a guest's name: its first two words' first letters.
     *
     * @returns {string} the initials, upper case
     */
    guestInitials() {
      return (this.person?.fullName || '').trim().split(/\s+/).slice(0, 2).map(word => Array.from(word)[0] || '').join('').toUpperCase();
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
      // on the window and on capture, wherever the focus is: before the person search, which answers Escape itself, and the views around, which close on it
      if (menu) {
        window.addEventListener('keydown', this.closeMenuOnEscape, true);
      } else {
        window.removeEventListener('keydown', this.closeMenuOnEscape, true);
        this.chosen = null;
        this.typed = '';
        this.guestChosen = false;
      }
    },
    chosen(chosen) {
      if (chosen) {
        // the search forgets its text once a user is picked: a removed pick leaves no name to offer
        this.typed = '';
        this.guestChosen = false;
      }
    },
    guestName() {
      this.guestChosen = false;
    },
  },
  /**
   * Stops listening for Escape when the speaker goes while its menu is open.
   * @returns {void}
   */
  beforeDestroy() {
    window.removeEventListener('keydown', this.closeMenuOnEscape, true);
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
     * Closes the menu, the keyboard focus going back to the chip or the pencil
     * that opens it, as the menu's own Escape would.
     * @returns {void}
     */
    closeMenu() {
      this.menu = false;
      this.$nextTick(() => this.$el?.querySelector?.('.documentTranscriptSpeakerChange, [role=button]')?.focus());
    },
    /**
     * Closes the open menu on Escape, the key then going no further; with the
     * menu closed, Escape goes on to the views around.
     *
     * @param {KeyboardEvent} event the key
     * @returns {void}
     */
    closeMenuOnEscape(event) {
      if (event.key === 'Escape') {
        event.stopPropagation();
        this.closeMenu();
      }
    },
    /**
     * Asks to name the speaker after the chosen user, or after the typed name
     * as a guest's.
     * @returns {void}
     */
    name() {
      if (this.guestChosen) {
        this.$emit('name', {speaker: this.speaker, guestName: this.guestName});
      } else {
        this.$emit('name', {speaker: this.speaker, user: this.chosen});
      }
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
