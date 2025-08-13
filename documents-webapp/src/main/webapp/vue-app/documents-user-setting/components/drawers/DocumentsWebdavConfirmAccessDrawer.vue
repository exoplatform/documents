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
  <exo-drawer
    ref="drawer"
    v-model="drawer"
    id="documentsWebdavConfirmAccessDrawer"
    :loading="loading"
    :right="!$vuetify.rtl"
    go-back-button>
    <template #title>
      {{ $t('UserSettings.documents.webdav.confirmAccess.title') }}
    </template>
    <template v-if="drawer" #content>
      <div class="d-flex flex-column justify-center align-center full-width pa-5">
        <template v-if="emailSent">
          <div class="full-width text-start">
            {{ $t('UserSettings.documents.webdav.confirmAccess.checkEmail') }}
          </div>
          <v-text-field
            id="otpCode"
            v-model="otpCode"
            :title="$t('UserSettings.documents.webdav.confirmAccess.inputTitle')"
            :placeholder="$t('UserSettings.documents.webdav.confirmAccess.inputPlaceholder')"
            prepend-inner-icon="fas fa-lock icon-default-color ms-n2"
            class="border-box-sizing full-width py-4"
            name="otpCode"
            aria-required="true"
            type="text"
            tabindex="0"
            required="required"
            autofocus="autofocus"
            outlined
            dense />
          <div class="d-flex">
            <v-btn
              :disabled="loading"
              :loading="loading && operation === 'sendingEmail'"
              class="btn"
              @click="sendEmail">
              {{ $t('UserSettings.documents.webdav.resend') }}
            </v-btn>
            <div class="px-2"></div>
            <v-btn
              :disabled="loading || !otpCode"
              :loading="loading && operation === 'verifying'"
              class="btn btn-primary"
              @click="verify">
              {{ $t('UserSettings.documents.webdav.verify') }}
            </v-btn>
          </div>
        </template>
        <div v-else>
          {{ $t('UserSettings.documents.webdav.confirmAccess.sendingEmail') }}
        </div>
      </div>
    </template>
  </exo-drawer>
</template>
<script>
export default {
  data: () => ({
    drawer: false,
    loading: false,
    emailSent: false,
    otpMethod: 'email',
    otpCode: null,
    password: null,
    operation: null,
    nextStepDrawer: null,
    renew: false,
  }),
  methods: {
    open(nextStepDrawer, renew) {
      if (this.password && !renew) {
        nextStepDrawer.open(this.password);
      } else {
        this.nextStepDrawer = nextStepDrawer;
        this.renew = renew;
        this.otpCode = null;
        this.emailSent = false;
        this.sendEmail();
        this.$refs.drawer.open();
      }
    },
    async sendEmail() {
      this.operation = 'sendingEmail';
      this.loading = true;
      try {
        await this.$apiKeyService.sendOtpCode(this.otpMethod);
      } finally {
        this.loading = false;
        this.emailSent = true;
      }
    },
    async verify() {
      this.operation = 'verifying';
      this.loading = true;
      try {
        this.password = await this.$apiKeyService.getPassword(this.otpMethod, this.otpCode, this.renew);
        this.nextStepDrawer.open(this.password);
        window.setTimeout(() => this.$refs.drawer.close(), 200);
      } catch {
        this.$root.$emit('alert-message', this.$t('UserSettings.documents.webdav.otpCodeInvalid'), 'error');
      } finally {
        this.loading = false;
      }
    },
  },
};
</script>