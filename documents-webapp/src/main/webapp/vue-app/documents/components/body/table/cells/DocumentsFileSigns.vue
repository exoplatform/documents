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
  <span
    v-if="isFavorite || signs.length"
    class="documents-file-signs d-inline-flex align-center">
    <v-icon
      v-if="isFavorite"
      :title="favoriteLabel"
      :aria-label="favoriteLabel"
      :color="dark ? 'white' : 'yellow darken-2'"
      class="documents-file-sign documents-file-favorite"
      role="img"
      size="13">
      fas fa-star
    </v-icon>
    <template v-for="(sign, index) in signs">
      <v-btn
        v-if="canManageAccess"
        :key="sign.id"
        :title="sign.label"
        :aria-label="sign.label"
        :class="(isFavorite || index) && 'ms-2'"
        class="documents-file-sign documents-file-sharing"
        icon
        x-small
        @click.stop.prevent="manageAccess">
        <v-icon
          :class="dark ? 'white--text' : 'iconStyle'"
          size="13">
          {{ sign.icon }}
        </v-icon>
      </v-btn>
      <v-icon
        v-else
        :key="sign.id"
        :title="sign.label"
        :aria-label="sign.label"
        :class="[dark ? 'white--text' : 'iconStyle', (isFavorite || index) && 'ms-2']"
        class="documents-file-sign documents-file-sharing"
        role="img"
        size="13">
        {{ sign.icon }}
      </v-icon>
    </template>
  </span>
</template>
<script>
import {FOLDER_MODE, getSharingSigns, canManageAccess as mayManageAccess, openManageAccess} from '../../../../js/DocumentsSharingSign.js';

export default {
  inject: {
    documentsSharingReference: {
      default: null,
    },
  },
  props: {
    file: {
      type: Object,
      default: null,
    },
    dark: {
      type: Boolean,
      default: false,
    },
    noSharing: {
      type: Boolean,
      default: false,
    },
  },
  computed: {
    isFavorite() {
      return !this.file?.folder && !!this.file?.metadatas?.favorites?.length;
    },
    favoriteLabel() {
      return this.$t('documents.label.sign.favorite');
    },
    signs() {
      if (this.noSharing) {
        return [];
      }
      return getSharingSigns(this.file, this.documentsSharingReference)
        .map(sign => ({id: sign.id, icon: sign.icon, label: this.$t(sign.labelKey)}));
    },
    canManageAccess() {
      return mayManageAccess(this.file);
    },
  },
  methods: {
    /**
     * Opens the access drawer of the file, as its sharing sign was clicked.
     *
     * @returns {void}
     */
    manageAccess() {
      openManageAccess(this.$root, this.file, this.documentsSharingReference?.mode === FOLDER_MODE ? 'folderView' : 'recentView');
    },
  },
};
</script>
