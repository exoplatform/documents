<!--
* Copyright (C) 2022 eXo Platform SAS
*
*  This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU Affero General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU Affero General Public License
* along with this program.  If not, see <gnu.org/licenses>.
-->
<template>
  <div class="d-flex">
    <div class="profile-popover user-wrapper pl-4 pt-2 pb-2">
      <a
        class="d-flex not-clickable flex-nowrap flex-grow-1 text-truncate container--fluid align-center">
        <v-icon
          v-if="user.providerId ==='group'"
          size="19"
          class="fas fa-users" />
        <v-avatar
          v-else
          size="32"
          class="ma-0">
          <img
            :src="avatarUrl"
            :alt="displayName"
            class="object-fit-cover ma-auto"
            loading="lazy"
            role="presentation">
        </v-avatar>
        <div v-if="displayName || $slots.subTitle" class="ms-2 overflow-hidden">
          <p
            v-if="displayName"
            class="text-truncate subtitle-2 text-color text-left mb-0 font-weight-bold">
            {{ displayName }}
          </p>
        </div>
      </a>
    </div>
    <v-spacer />
    <div
      class="ma-auto d-flex pe-2">
      <v-icon
        v-if="userVisibility && userVisibility === 'edit' || user.permission === 'edit'"
        color="grey lighten-1"
        :size="16">
        fas fa-edit
      </v-icon>
      <v-icon
        v-else
        color="grey lighten-1"
        :size="16">
        fas fa-eye
      </v-icon>
      <documents-visibility-menu
        :is-mobile="isMobile"
        @visibility-user="visibilityUser" />
      <v-divider vertical />
      <v-icon
        :title="$t('documents.label.visibility.remove')"
        :size="16"
        color="grey lighten-1"
        class="pe-5 iconStyle"
        @click="$emit('remove-user', user)">
        fas fa-trash
      </v-icon>
    </div>
  </div>
</template>

<script>

export default {
  props: {
    user: {
      type: Object,
      default: () => ({}),
    },
    isMobile: {
      type: Boolean,
      default: false
    }
  },
  data() {
    return {
      userVisibility: null,
    };
  },
  computed: {
    avatarUrl() {
      const profile = this.user && (this.user.profile || this.user.space);
      return profile && (profile.avatarUrl || profile.avatar) || this.user  && this.user.avatar ;
    },
    displayName() {
      const profile = this.user && (this.user.profile || this.user.space);
      return profile && (profile.displayName || profile.fullName && profile.fullName.substring(0,profile.fullName.lastIndexOf(' ('))) || this.user &&  (this.user.name || this.user.displayName);
    },
  },
  methods: {
    visibilityUser(value){
      this.userVisibility=value;
      this.user.permission=value;
      this.$emit('set-visibility', this.user);
    }
  }
};
</script>
