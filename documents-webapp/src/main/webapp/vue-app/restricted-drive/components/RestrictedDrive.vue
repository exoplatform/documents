<!--

 Copyright (C) 2026 eXo Platform SAS.

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU Affero General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU Affero General Public License for more details.

 You should have received a copy of the GNU Affero General Public License
 along with this program. If not, see <http://www.gnu.org/licenses/>.

-->
<template>
  <v-app class="singlePageApplication">
    <v-card
      class="restricted-drive application-body py-12 text-center"
      flat>
      <v-card-text>
        <v-icon color="primary" class="fa-7x">fa-door-open</v-icon>
      </v-card-text>
      <v-card-text
        class="text-title"
        v-sanitized-html="title" />
      <v-card-text
        class="text-body"
        v-sanitized-html="subtitle" />
    </v-card>
  </v-app>
</template>
<script>
export default {
  computed: {
    title() {
      return this.$t('documents.restrictedDrive.title');
    },
    subtitle() {
      return this.$t('documents.restrictedDrive.subtitle');
    },
  },
};
</script>