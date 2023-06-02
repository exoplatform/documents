<!--
 * Copyright (C) 2023 eXo Platform SAS.
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
  <div>
    <div>
      <div
        v-if="isAccessGranted"
        class="center mb-6">
        <p>
          <v-icon
            class="mt-n1"
            size="20"
            :color="iconColor">
            {{ iconClass }}
          </v-icon>
          {{ decodeURIComponent(this.params.documentName) }}
        </p>
        <p v-if="isDownloading">
          {{ $t('document.public.access.download.inProgress.message') }}
        </p>
      </div>
      <p
        v-if="!isAccessGranted"
        class="red--text center text-body-1">
        <v-icon
          size="20"
          color="red">
          fas fa-times-circle
        </v-icon>
        {{ $t('download.public.link.not.active.message') }}
      </p>
      <v-form v-else-if="!isLinkExpired">
        <div v-if="requirePassword">
          <v-label
            for="documentPassword">
            <span class="text-body-2">
              {{ $t('download.public.link.fill.password.message') }}
            </span>
          </v-label>
          <v-text-field
            v-if="requirePassword"
            v-model="password"
            :title="$t('portal.login.Password')"
            :placeholder="$t('portal.login.Password')"
            :type="passwordType"
            :append-icon="showPassword ? 'fas fa-eye-slash subtitle-1 mt-0' : 'fas fa-eye subtitle-1 mt-0'"
            :readonly="isDownloading"
            prepend-inner-icon="fas fa-lock ms-n2 grey--text text--lighten-1"
            class="pt-2 login-password border-box-sizing"
            name="documentPassword"
            required="required"
            outlined
            dense
            @click:append="toggleShow" />
        </div>
        <div class="center">
          <v-btn
            :loading="isDownloading"
            class="btn btn-primary mt-4"
            @click="downloadDocument">
            {{ $t('document.public.access.download.label') }}
          </v-btn>
        </div>
      </v-form>
      <p
        v-else
        class="red--text center text-body-1">
        <v-icon
          size="20"
          color="red">
          fas fa-times-circle
        </v-icon>
        {{ $t('download.public.link.expired.message') }}
      </p>
    </div>
  </div>
</template>

<script>

import {downloadPublicDocument} from '../../documents/js/DocumentFileService.js';

export default {
  data: () => ({
    isDownloading: false,
    iconColor: '#476A9C',
    iconClass: 'fas fa-file',
    password: null,
    showPassword: false
  }),
  props: {
    params: {
      type: Object,
      default: null,
    },
  },
  computed: {
    passwordType(){
      return this.showPassword ? 'text' :'password';
    },
    requirePassword() {
      return this.isPublicAccessActive && this.params?.isTokenLocked;
    },
    isLinkExpired() {
      return this.params?.isTokenExpired;
    },
    isPublicAccessActive() {
      return this.params?.hasPublicLink;
    },
    isAccessGranted() {
      return this.params?.documentName && this.params?.hasPublicLink;
    }
  },
  methods: {
    downloadDocument() {
      this.isDownloading = true;
      downloadPublicDocument(this.params?.nodeId, this.password).then((response) => {
        return response.blob();
      }).then(blob => {
        const element = document.createElement('a');
        element.href = URL.createObjectURL(blob);
        element.setAttribute('download', this.params?.documentName);
        element.click();
        element.remove();
      }).finally(() => {
        this.isDownloading = false;
      });
    },
    toggleShow() {
      this.showPassword = !this.showPassword;
    },
    getFileIcon(mimeType) {
      const extensions = extensionRegistry.loadExtensions('documents', 'documents-icons-extension');
      let extension;
      if (!mimeType) {
        extension = extensions[0].get('folder');
      } else {
        extension = extensions[0].get(mimeType);
        if (!extension) {
          extension = extensions[0].get('file');
        }
      }
      this.iconColor = extension.color;
      this.iconClass = extension.class;
    },
  },
  created() {
    this.getFileIcon(this.params.documentType);
  },
  mounted() {
    this.downloadDocument();
  }
};
</script>