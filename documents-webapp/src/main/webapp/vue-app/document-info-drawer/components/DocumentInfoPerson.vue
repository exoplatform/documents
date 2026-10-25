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
  <user-avatar
    v-if="username"
    :profile-id="username"
    :size="20"
    extra-class="text-truncate"
    avatar-class="me-1"
    fullname
    popover />
  <span v-else class="text-truncate">{{ $t('documents.drawer.details.system') }}</span>
</template>
<script>
export default {
  props: {
    identity: {
      type: Object,
      default: null,
    },
  },
  computed: {
    /**
     * The person's username, when the identity names one.
     * @returns {string} the username, or null for the system
     */
    username() {
      return this.identity?.remoteId || null;
    },
  },
};
</script>
