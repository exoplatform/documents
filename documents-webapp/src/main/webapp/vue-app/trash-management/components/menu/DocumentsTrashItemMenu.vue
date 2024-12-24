<!--
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
 *
-->

<template>
  <component
    :is="isMobile && 'v-bottom-sheet' || 'v-menu'"
    ref="trashActionMenu"
    v-model="menu"
    :attach="isMobile && '#vuetify-apps'"
    :left="!$vuetify.rtl"
    :right="$vuetify.rtl"
    transition="slide-x-reverse-transition"
    content-class="position-absolute application-menu z-index-modal"
    offset-y
    eager>
    <template #activator="{on, attrs}">
      <v-btn
        v-bind="attrs"
        v-on="on"
        :loading="loading"
        icon>
        <v-icon size="20">fa-ellipsis-v</v-icon>
      </v-btn>
    </template>
  </component>
</template>
<script>
export default {
  data: () => ({
    id: Math.random(), // NOSONAR
    menu: false,
    loading: false,
  }),
  computed: {
    isMobile() {
      return this.$vuetify.breakpoint.name === 'xs' || this.$vuetify.breakpoint.name === 'sm';
    }
  },
};
</script>