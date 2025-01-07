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
  <div class="d-inline">
    <v-btn
      color="primary"
      elevation="0"
      outlined
      :disabled="isRestoreButtonDisabled"
      @click="$emit('restore-items')">
      <v-icon size="16" class="me-2">fa-undo</v-icon>
      {{ $t('trash.element.restore') }}
    </v-btn>
  </div>
</template>
<script>
export default {
  props: {
    bulkActionProgress: {
      type: Boolean,
      default: false
    }
  },
  computed: {
    isRestoreButtonDisabled() {
      return this.bulkActionProgress;
    }
  },
};
</script>