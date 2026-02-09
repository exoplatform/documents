<!--
 Copyright (C) 2025 eXo Platform SAS.

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
  <div class="mx-auto d-flex flex-column align-center justify-center full-width full-height">
    <v-icon
      color="tertiary"
      size="60">
      fas fa-file-alt
    </v-icon>
    <span class="mt-5">{{ title }}</span>
  </div>
</template>
<script>
export default {
  props: {
    title: {
      type: String,
      default: null,
    },
  },
};
</script>