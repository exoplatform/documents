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
  <v-hover v-slot="{ hover }">
    <v-card
      v-on="!href && {
        click: openFile,
      }"
      :id="id"
      :elevation="hover ? 4 : 0"
      :class="{
        'border-color-transparent': hover,
        'border-color': !hover,
      }"
      :loading="loading"
      :href="href"
      max-height="210px"
      max-width="100%"
      height="160px"
      width="180px"
      class="overflow-hidden d-flex flex-column content-box-sizing">
      <div class="d-flex flex-grow-0 flex-shrink-0 align-center justify-center">
        <v-icon
          :color="fileIconColor"
          class="mt-5"
          size="80">
          {{ fileIcon }}
        </v-icon>
      </div>
      <v-tooltip bottom>
        <template #activator="{on, attrs}">
          <v-card
            v-on="on"
            v-bind="attrs"
            class="d-flex flex-grow-1 flex-shrink-1 flex-column no-border-radius align-center justify-center"
            height="36"
            flat>
            <div
              class="d-flex justify-center font-weight-bold text-truncate-2 full-width border-box-sizing px-5">
              <div class="text-truncate">{{ fileName }}</div>
              <div>{{ fileExtension }}</div>
            </div>
          </v-card>
        </template>
        <span>{{ file.name }}</span>
      </v-tooltip>
    </v-card>
  </v-hover>
</template>
<script>
export default {
  props: {
    file: {
      type: Object,
      default: null,
    },
    localFolderPath: {
      type: Object,
      default: null,
    },
    officeLink: {
      type: Boolean,
      default: false,
    },
  },
  data: () => ({
    fileProtocol: 'file:///',
    href: null,
  }),
  computed: {
    extension() {
      return this.$documentsIconsExtension?.[0]?.get?.(this.file?.mimeType);
    },
    canPreview() {
      return this.extension?.canPreview;
    },
    fileIcon() {
      return this.extension?.class || 'fas fa-file';
    },
    fileIconColor() {
      return this.extension?.color || 'secondary';
    },
    fileNameParts() {
      return this.file?.name?.split?.('.') || [];
    },
    fileName() {
      return this.fileNameParts.length > 1 ? this.fileNameParts.slice(0, this.fileNameParts.length - 1).join('.') : this.file?.name;
    },
    fileExtension() {
      return this.fileNameParts.length > 1 ? `.${this.fileNameParts.slice(this.fileNameParts.length - 1)}` : '';
    },
    isMobile() {
      return this.$vuetify.breakpoint.smAndDown;
    },
  },
  watch: {
    officeLink() {
      this.initHref();
    },
    extension() {
      this.initHref();
    },
  },
  created() {
    this.initHref();
  },
  methods: {
    async initHref() {
      if (this.officeLink && this.extension?.protocol) {
        const fileLocalPath = await this.$documentOfflineService.getLocalFilePath(this.file);
        this.href = `${this.extension?.protocol}${this.fileProtocol}${this.localFolderPath}/${fileLocalPath}`;
      }
    },
    openFile() {
      if (this.canPreview) {
        this.$emit('preview', this.file, this.extension);
      } else {
        this.$emit('download', this.file, this.extension);
      }
    },
  },
};
</script>