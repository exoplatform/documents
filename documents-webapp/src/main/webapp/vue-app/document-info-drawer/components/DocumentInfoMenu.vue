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
  <v-menu
    v-model="menu"
    content-class="documentInfoMenu"
    left
    offset-y>
    <template #activator="{on, attrs}">
      <v-btn
        :aria-label="$t('documents.drawer.details.moreActions')"
        :title="$t('documents.drawer.details.moreActions')"
        icon
        v-bind="attrs"
        v-on="on">
        <v-icon size="18">fas fa-ellipsis-v</v-icon>
      </v-btn>
    </template>
    <v-list dense class="py-1">
      <documents-info-menu-item
        v-if="transcriptAvailable"
        :label="$t('documents.drawer.details.transcript')"
        icon="fas fa-closed-captioning"
        @click="$emit('transcript')" />
      <extension-registry-components
        :params="contributionParams"
        name="DocumentsInfo"
        type="documents-info-header"
        parent-element="div"
        element="div"
        class="documentInfoMenuContributions" />
      <v-divider class="documentInfoMenuDivider my-1" />
      <documents-info-menu-item
        :label="$t('documents.label.copy.link')"
        icon="fas fa-link"
        @click="$emit('copy-link')" />
      <documents-info-menu-item
        v-if="folderUrl"
        :label="$t('documents.drawer.details.openInDocuments')"
        icon="fas fa-folder-open"
        @click="openInDocuments" />
    </v-list>
  </v-menu>
</template>
<script>
export default {
  props: {
    file: {
      type: Object,
      default: null,
    },
    transcriptAvailable: {
      type: Boolean,
      default: false,
    },
  },
  data: () => ({
    menu: false,
  }),
  computed: {
    /**
     * Gives the contributions of other add-ons the file and where they are
     * shown, so a contribution can render as a labelled entry of the menu; one
     * that ignores it keeps its own look.
     * @returns {Object} the extension parameters
     */
    contributionParams() {
      return {
        file: this.file,
        inMenu: true,
      };
    },
    /**
     * Points at the file's folder in the Documents application, with the file
     * previewed; none when the page is that application, which already shows
     * the file, or when the file's path names no Documents page.
     * @returns {string} the url, or null
     */
    folderUrl() {
      if (!this.file?.path || document.querySelector('.documents-application')) {
        return null;
      }
      const folderPath = this.$documentsUtils.getParentFolderUrl(this.file);
      if (!folderPath || folderPath === window.location.pathname) {
        return null;
      }
      return `${folderPath}?documentPreviewId=${this.file.id}`;
    },
  },
  methods: {
    /**
     * Opens the file's folder in the Documents application, the file
     * previewed; a folder opens itself.
     * @returns {void}
     */
    openInDocuments() {
      if (this.file.folder) {
        this.$root.$emit('open-folder', this.file);
      } else {
        this.$documentsUtils.openLink(this.folderUrl, '_self');
      }
    },
  },
};
</script>
