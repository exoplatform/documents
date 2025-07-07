<!--

  Copyright (C) 2025 eXo Platform SAS

  This program is free software: you can redistribute it and/or modify
  it under the terms of the GNU Affero General Public License as published by
  the Free Software Foundation, either version 3 of the License, or
  (at your option) any later version.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU Affero General Public License
  along with this program.  If not, see <gnu.org/licenses>.

-->
<template>
  <v-dialog
    ref="dialog"
    v-model="dialog"
    content-class="uiPopup"
    max-width="100vw"
    width="420"
    persistent>
    <v-card class="elevation-12 transparent">
      <div class="ignore-vuetify-classes popupHeader transparent ClearFix">
        <v-btn
          class="pull-right"
          icon
          small
          @click="close">
          <v-icon size="16">fa-times</v-icon>
        </v-btn>
        <span class="ignore-vuetify-classes text-title">
          {{ $t('documents.label.openInDesktop.dialog.title') }}
        </span>
      </div>
      <v-card-text>
        <div>
          {{ $t('documents.label.openInDesktop.dialog.description') }}
        </div>
        <div class="mt-4 mb-2">
          {{ $t('documents.label.openInDesktop.dialog.userName') }}
        </div>
        <v-text-field
          v-model="userName"
          class="pa-0"
          outlined
          readonly>
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
        <div class="my-2">
          {{ $t('documents.label.openInDesktop.dialog.password') }}
        </div>
        <v-text-field
          v-model="password"
          :type="passwordType"
          class="pa-0"
          outlined
          readonly>
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
      </v-card-text>
      <v-card-actions>
        <v-spacer />
        <v-btn
          :href="href"
          class="btn btn-primary me-2"
          @click="openFile">
          {{ $t('documents.label.openInDesktop.dialog.openFile') }}
        </v-btn>
        <v-btn
          class="btn ms-2"
          @click="close">
          {{ $t('documents.label.openInDesktop.dialog.cancel') }}
        </v-btn>
        <v-spacer />
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>
<script>
export default {
  props: {
    value: {
      type: Boolean,
      default: false,
    },
    href: {
      type: String,
      default: null,
    },
  },
  data: () => ({
    dialog: false,
    userName: eXo.env.portal.userName,
    password: null,
    passwordType: 'password',
    canCopy: false,
    userNameCopied: false,
    passwordCopied: false,
  }),
  computed: {
    userNameTooltip() {
      return this.userNameCopied ? this.$t('documents.label.openInDesktop.dialog.copied') : this.$t('documents.label.openInDesktop.dialog.copy');
    },
    passwordHidden() {
      return this.passwordType === 'password';
    },
    passwordIcon() {
      return this.passwordHidden ? 'fa-eye' : 'fa-eye-slash';
    },
    passwordTypeTooltip() {
      return this.passwordHidden ? this.$t('documents.label.openInDesktop.dialog.viewPassword') : this.$t('documents.label.openInDesktop.dialog.hidePassword');
    },
    passwordTooltip() {
      return this.passwordCopied ? this.$t('documents.label.openInDesktop.dialog.copied') : this.$t('documents.label.openInDesktop.dialog.copy');
    },
  },
  watch: {
    dialog() {
      if (this.dialog) {
        document.dispatchEvent(new CustomEvent('modalOpened'));
      } else {
        document.dispatchEvent(new CustomEvent('modalClosed'));
      }
      this.$emit('input', this.dialog);
    },
  },
  methods: {
    async open() {
      const resp = await fetch('/social/rest/digest', {credentials: 'include'});
      this.password = await resp.text();
      this.dialog = true;
      if (!navigator?.clipboard?.writeText
          && navigator?.permissions?.query) {
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
    close() {
      this.dialog = false;
    },
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