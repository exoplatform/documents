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
  <div class="d-flex flex-column align-start">
    <div class="my-2 font-weight-bold">
      {{ $t('UserSettings.documents.webdav.username') }}
    </div>
    <v-text-field
      v-model="userName"
      prepend-inner-icon="fas fa-user icon-default-color ms-n2"
      class="pa-0 full-width"
      outlined
      readonly
      dense>
      <template v-if="canCopy" #append>
        <v-tooltip left>
          <template #activator="{on, attrs}">
            <v-btn
              v-bind="attrs"
              v-on="on"
              :aria-label="userNameTooltip"
              class="mt-n2"
              icon
              @click="copyUserName">
              <v-icon size="16">fa fa-copy</v-icon>
            </v-btn>
          </template>
          <span>{{ userNameTooltip }}</span>
        </v-tooltip>
      </template>
    </v-text-field>
    <div class="my-2 font-weight-bold">
      {{ $t('UserSettings.documents.webdav.apiKey') }}
    </div>
    <v-text-field
      v-model="password"
      :type="passwordType"
      prepend-inner-icon="fas fa-key icon-default-color ms-n2"
      class="pa-0 full-width"
      outlined
      readonly
      dense>
      <template v-if="canCopy" #append>
        <v-tooltip bottom>
          <template #activator="{on, attrs}">
            <v-btn
              v-bind="attrs"
              v-on="on"
              :aria-label="passwordTypeTooltip"
              class="mt-n2"
              icon
              @click="switchPasswordType">
              <v-icon size="16">{{ passwordIcon }}</v-icon>
            </v-btn>
          </template>
          <span>{{ passwordTypeTooltip }}</span>
        </v-tooltip>
        <v-tooltip left>
          <template #activator="{on, attrs}">
            <v-btn
              v-bind="attrs"
              v-on="on"
              :aria-label="passwordTooltip"
              class="mt-n2"
              icon
              @click="copyPassword">
              <v-icon size="16">fa fa-copy</v-icon>
            </v-btn>
          </template>
          <span>{{ passwordTooltip }}</span>
        </v-tooltip>
      </template>
    </v-text-field>
  </div>
</template>
<script>
export default {
  props: {
    password: {
      type: String,
      default: null,
    },
  },
  data: () => ({
    userName: eXo.env.portal.userName,
    passwordType: 'password',
    canCopy: false,
    userNameCopied: false,
    passwordCopied: false,
  }),
  computed: {
    userNameTooltip() {
      return this.userNameCopied ? this.$t('UserSettings.documents.webdav.copied') : this.$t('UserSettings.documents.webdav.copy');
    },
    passwordHidden() {
      return this.passwordType === 'password';
    },
    passwordIcon() {
      return this.passwordHidden ? 'fa-eye' : 'fa-eye-slash';
    },
    passwordTypeTooltip() {
      return this.passwordHidden ? this.$t('UserSettings.documents.webdav.viewPassword') : this.$t('UserSettings.documents.webdav.hidePassword');
    },
    passwordTooltip() {
      return this.passwordCopied ? this.$t('UserSettings.documents.webdav.copied') : this.$t('UserSettings.documents.webdav.copy');
    },
  },
  watch: {
    loading() {
      this.$emit('loading', this.loading);
    },
  },
  async created() {
    if (!navigator?.clipboard?.writeText && navigator?.permissions?.query) {
      const status = await navigator.permissions.query({name: 'clipboard-write'});
      if (status?.state !== 'granted') {
        this.canCopy = false;
      } else {
        this.canCopy = !!navigator?.clipboard?.writeText;
      }
    } else {
      this.canCopy = !!navigator?.clipboard?.writeText;
    }
    this.passwordType = this.canCopy ? 'password' : 'text';
  },
  methods: {
    switchPasswordType() {
      this.passwordType = this.passwordHidden ? 'text' : 'password';
    },
    copyUserName() {
      navigator.clipboard.writeText(this.userName);
      this.userNameCopied = true;
      window.setTimeout(() => this.userNameCopied = false, 2000);
    },
    copyPassword() {
      navigator.clipboard.writeText(this.password);
      this.passwordCopied = true;
      window.setTimeout(() => this.passwordCopied = false, 2000);
    },
  },
};
</script>