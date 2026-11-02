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
    v-if="isFavorite || sign"
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
    <v-btn
      v-if="sign && canManageAccess"
      :title="signLabel"
      :aria-label="signLabel"
      :class="isFavorite && 'ms-2'"
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
      v-else-if="sign"
      :title="signLabel"
      :aria-label="signLabel"
      :class="[dark ? 'white--text' : 'iconStyle', isFavorite && 'ms-2']"
      class="documents-file-sign documents-file-sharing"
      role="img"
      size="13">
      {{ sign.icon }}
    </v-icon>
  </span>
</template>
<script>
import {SIGNS, FOLDER_MODE, getSharingSign, canManageAccess as mayManageAccess, openManageAccess} from '../../../../js/DocumentsSharingSign.js';

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
  },
  computed: {
    isFavorite() {
      return !this.file?.folder && !!this.file?.metadatas?.favorites?.length;
    },
    favoriteLabel() {
      return this.$t('documents.label.sign.favorite');
    },
    sign() {
      const signId = getSharingSign(this.file, this.documentsSharingReference);
      return signId && SIGNS[signId];
    },
    signLabel() {
      return this.sign && this.$t(this.sign.labelKey);
    },
    canManageAccess() {
      return mayManageAccess(this.file, this.$shareDocumentSuspended);
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
