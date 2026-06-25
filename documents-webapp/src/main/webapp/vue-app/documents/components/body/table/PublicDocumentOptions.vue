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
  <div
    class="flex-wrap mt-4">
    <v-form
      ref="form"
      v-model="valid">
      <div class="d-flex flex-row">
        <div class="d-flex flex-column full-width ma-auto">
          <p class="text-body">
            {{ $t('documents.public.access.options.password.label') }}
          </p>
          <p
            v-if="showPasswordInput"
            class="text-caption grey--text text--darken-2 caption text-break mt-n3">
            {{ $t('documents.public.access.options.password.info') }}
          </p>
          <p
            v-else
            class="text-caption grey--text text--darken-2 caption text-break mt-n3">
            {{ $t('documents.public.access.options.password.add.info') }}
          </p>
          <div
            v-if="showPasswordInput"
            class="pt-3 mb-2">
            <v-label
              for="documentPassword">
              <span class="text-body">
                {{ $t('documents.public.link.new.password.message') }}
              </span>*
            </v-label>
            <v-text-field
              v-model="password"
              :title="$t('documents.public.link.new.password.message')"
              :placeholder="$t('documents.public.access.password.placeholder')"
              :type="passwordType"
              :append-icon="showPassword ? 'fas fa-eye-slash subtitle-1 mt-0' : 'fas fa-eye subtitle-1 mt-0'"
              :readonly="isSaving"
              prepend-inner-icon="fas fa-lock ms-n2 grey--text text--lighten-1"
              class="pt-2 login-password border-box-sizing"
              name="documentPassword"
              :rules="[v => passwordRegex.test(v) || '']"
              required
              outlined
              dense
              @input="checkPasswordValid"
              @blur="checkPasswordValid"
              @click:append="toggleShowPassword" />
            <p
              :class="startCheckPassword && !isPasswordValid? 'red--text text--darken-4': 'grey--text text--darken-2'"
              class="text-caption caption text-break mb-4">
              {{ $t('documents.public.link.password.policy.message') }}
            </p>
            <v-label
              for="confirmDocumentPassword">
              <span class="text-body">
                {{ $t('documents.public.link.confirm.password.message') }}
              </span>*
            </v-label>
            <v-text-field
              v-model="confirmPassword"
              :title="$t('documents.public.link.confirm.password.message')"
              :placeholder="$t('documents.public.link.confirm.password.placeholder')"
              :type="confirmPasswordType"
              :append-icon="showConfirmPassword ? 'fas fa-eye-slash subtitle-1 mt-0' : 'fas fa-eye subtitle-1 mt-0'"
              :readonly="isSaving"
              prepend-inner-icon="fas fa-lock ms-n2 grey--text text--lighten-1"
              class="pt-2 login-password border-box-sizing"
              name="confirmDocumentPassword"
              required
              :rules="confirmPasswordRules"
              outlined
              dense
              @click:append="toggleShowConfirmPassword" />
          </div>
        </div>
        <div class="d-flex flex-column ms-n11">
          <v-switch
            v-model="hasPassword"
            class="mt-0 me-1 mt-n1" />
        </div>
      </div>
      <div
        v-if="!showPasswordInput && existOldPassword && hasPassword"
        class="d-flex flex-row mb-3">
        <div class="d-flex flex-column full-width">
          <input
            :type="currentPasswordType"
            disabled
            readonly
            class="ps-0 mt-auto mb-auto elevation-0"
            :value="publicDocumentAccess.decodedPassword">
        </div>
        <div class="d-flex flex-column ms-n16">
          <v-tooltip bottom>
            <template #activator="{ on, attrs}">
              <v-btn
                v-bind="attrs"
                v-on="on"
                class="mt-0 me-1 mt-auto mb-auto"
                color="primary"
                icon
                @click="showCurrentPassword = !showCurrentPassword">
                <v-icon
                  size="18">
                  {{ showCurrentPassword ? 'fas fa-eye-slash' : 'fas fa-eye' }}
                </v-icon>
              </v-btn>
            </template>
            {{ $t('documents.public.access.password.modify.tooltip') }}
          </v-tooltip>
        </div>
        <div class="d-flex flex-column ms-n2">
          <v-tooltip bottom>
            <template #activator="{ on, attrs}">
              <v-btn
                v-bind="attrs"
                v-on="on"
                class="mt-0 me-1 mt-auto mb-auto"
                color="primary"
                icon
                @click="showPasswordInput = true">
                <v-icon
                  size="18">
                  fas fa-edit
                </v-icon>
              </v-btn>
            </template>
            {{ $t('documents.public.access.password.modify.tooltip') }}
          </v-tooltip>
        </div>
      </div>
      <div class="d-flex flex-row mt-3">
        <div class="d-flex flex-column full-width ma-auto">
          <p class="text-body">
            {{ $t('documents.public.access.options.expirationDate.label') }}
          </p>
          <p
            v-if="showExpirationDateInput"
            class="text-caption grey--text text--darken-2 caption text-break mt-n3">
            {{ $t('documents.public.access.options.expirationDate.info') }}
          </p>
          <p
            v-else
            class="text-caption grey--text text--darken-2 caption text-break mt-n3">
            {{ $t('documents.public.access.options.expirationDate.add.info') }}
          </p>
          <div
            v-if="showExpirationDateInput"
            class="mt-n4 mb-2">
            <v-radio-group
              v-model="expirationDateType"
              mandatory>
              <v-radio
                class="document-radio-button"
                value="specificDate">
                <template #label>
                  <p class="text-body-2 mt-2">
                    {{ $t('documents.public.access.options.expirationDate.label') }}
                  </p>
                </template>
              </v-radio>
              <v-menu
                v-if="expirationDateType === 'specificDate'"
                v-model="expirationDateMenu"
                :close-on-content-click="true"
                :nudge-right="40"
                transition="scale-transition"
                offset-y
                min-width="auto">
                <template #activator="{ on, attrs }">
                  <v-text-field
                    v-model="dateFormatted"
                    v-bind="attrs"
                    v-on="on"
                    :placeholder="$t('documents.public.access.choose.date.placeholder')"
                    class="pt-0 ps-8 pe-10 border-box-sizing"
                    append-icon="mdi-calendar"
                    :rules="[v => !!v || $t('documents.public.access.expiration.undefined')]"
                    required
                    readonly
                    outlined
                    dense />
                </template>
                <v-date-picker
                  v-model="expirationDate"
                  :min="new Date().toISOString().slice(0,10)"
                  :locale="lang"
                  required
                  @input="expirationDateMenu = false" />
              </v-menu>
              <v-radio
                class="document-radio-button"
                value="delayDate">
                <template #label>
                  <p class="text-body-2 mt-2">
                    {{ $t('documents.public.access.delay.date.label') }}
                  </p>
                </template>
              </v-radio>
              <div
                class="d-flex flex-row"
                v-if="expirationDateType === 'delayDate'">
                <div class="d-flex flex-column">
                  <v-text-field
                    v-model="delayTypeTimes"
                    class="pt-0 me-5 ms-8 border-box-sizing"
                    outlined
                    dense
                    type="number"
                    min="1" />
                </div>
                <div class="d-flex flex-column">
                  <v-select
                    v-model="delayType"
                    ref="delayType"
                    :items="delayTypeItems"
                    class="pt-0 me-10 border-box-sizing"
                    item-text="label"
                    item-value="value"
                    outlined
                    dense
                    @blur="$refs.delayType.blur()" />
                </div>
              </div>
            </v-radio-group>
          </div>
        </div>
        <div class="d-flex flex-column ms-n11">
          <v-switch
            v-model="showExpirationDateInput"
            class="mt-0 me-1 mt-n1" />
        </div>
      </div>
      <div class="d-flex flex-row mt-3">
        <div class="d-flex flex-column full-width pb-4">
          <v-label for="publicAccess">
            <span class="text-body mr-6">
              {{ $t('document.visibility.publicAccess.message') }}
            </span>
            <p class="text-subtitle pe-8"> {{ $t('document.visibility.publicAccess.choice.info') }} </p>
          </v-label>
        </div>
        <div class="d-flex flex-column">
          <v-tooltip bottom>
            <template #activator="{ on, attrs}">
              <v-btn
                v-bind="attrs"
                v-on="on"
                class="ms-n9 mt-n1"
                color="primary"
                icon
                @click="copyPublicAccessLink">
                <v-icon
                  size="18">
                  fas fa-clone
                </v-icon>
              </v-btn>
            </template>
            {{ $t('document.visibility.publicAccess.copyLink.message') }}
          </v-tooltip>
        </div>
      </div>
    </v-form>
  </div>
</template>
<script>
export default {
  props: {
    file: {
      type: Object,
      default: null,
    },
    existingPublicAccess: {
      type: Object,
      default: null,
    },
  },
  data: () => ({
    expirationDate: null,
    password: null,
    confirmPassword: null,
    publicDocumentAccess: null,
    showPasswordInput: false,
    showExpirationDateInput: false,
    showPassword: false,
    showConfirmPassword: false,
    isSaving: false,
    passwordRulesNotValid: false,
    passwordRegex: /((?=.*\d)(?=.*[a-z])(?=.*[A-Z]).{9,256})/,
    confirmPasswordRules: [],
    valid: false,
    expirationDatePicker: false,
    expirationDateType: 'specificDate',
    delayTypeTimes: 1,
    delayType: 'day',
    startCheckPassword: false,
    expirationDateMenu: false,
    lang: eXo.env.portal.language,
    dateFormat: {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
    },
    hasPassword: false,
    existOldPassword: false,
    showCurrentPassword: false,
    initialized: false
  }),
  created() {
    document.addEventListener('mousedown', this.closeDatePickerMenu);
    this.confirmPasswordRules = [v => !!v && v === this.password
        || this.$t('documents.public.access.password.not.identical')];
    this.$root.$on('open-public-document-options-drawer', this.open);
  },
  computed: {
    fileId() {
      return this.file?.id;
    },
    isPasswordValid() {
      return this.checkPasswordValid();
    },
    delayTypeItems() {
      return this.$t && [
        {label: this.$t('documents.public.access.delayType.day.label'), value: 'day'},
        {label: this.$t('documents.public.access.delayType.week.label'), value: 'week'},
        {label: this.$t('documents.public.access.delayType.month.label'), value: 'month'},
        {label: this.$t('documents.public.access.delayType.year.label'), value: 'year'},
      ];
    },
    passwordType() {
      return this.showPassword ? 'text' :'password';
    },
    confirmPasswordType() {
      return this.showConfirmPassword ? 'text' :'password';
    },
    currentPasswordType() {
      return this.showCurrentPassword && 'text' || 'password';
    },
    dateFormatted() {
      return this.computeDate(this.expirationDate);
    },
    publicLinkUrl() {
      const publicLinkUrl = `${window.location.origin}/${eXo.env.portal.containerName}/download-document/`;
      return this.fileId? `${publicLinkUrl}${this.fileId}` : '';
    },
  },
  watch: {
    showExpirationDateInput() {
      if (this.initialized) {
        this.$emit('change', this.getValidationState());
      }
    },
    hasPassword(value) {
      if (!value) {
        this.showPasswordInput = false;
      } else if (!this.existOldPassword) {
        this.showPasswordInput = true;
      }
      if (this.initialized) {
        this.$emit('change', this.getValidationState());
      }
    },
    showPasswordInput() {
      if (this.initialized) {
        this.$emit('change', this.getValidationState());
      }
    },
    password() {
      if (this.initialized) {
        this.$emit('change', this.getValidationState());
      }
    },
    confirmPassword() {
      if (this.initialized) {
        this.$emit('change', this.getValidationState());
      }
    },
    expirationDate() {
      if (this.initialized) {
        this.$emit('change', this.getValidationState());
      }
    },
    expirationDateType() {
      if (this.initialized) {
        this.$emit('change', this.getValidationState());
      }
    },
    existingPublicAccess: {
      immediate: true,
      handler(access) {
        if (access) {
          this.publicDocumentAccess = access;
          this.existOldPassword = access.hasPassword;
          this.hasPassword = access.hasPassword;
          this.showPasswordInput = false;
          this.showExpirationDateInput = !!access.expirationDate;
          const currentExpirationDate = access.expirationDate?.time || access.expirationDate;
          this.expirationDate = this.showExpirationDateInput && new Date(currentExpirationDate).toISOString().slice(0, 10) || null;
        } else {
          this.publicDocumentAccess = null;
          this.existOldPassword = false;
          this.hasPassword = false;
          this.showPasswordInput = false;
          this.showExpirationDateInput = false;
          this.expirationDate = null;
        }
        this.initialized = true;
      },
    },
  },
  methods: {
    closeDatePickerMenu() {
      if (this.expirationDateMenu) {
        setTimeout(() => {
          this.expirationDateMenu = false;
        }, 200);
      }
    },
    checkPasswordValid() {
      this.startCheckPassword = this.showPasswordInput;
      const passwordValue = this.password;
      return passwordValue && this.passwordRegex.test(passwordValue);
    },
    toggleShowPassword() {
      this.showPassword = !this.showPassword;
    },
    toggleShowConfirmPassword() {
      this.showConfirmPassword = !this.showConfirmPassword;
    },
    open(publicDocumentAccess) {
      this.confirmPassword = null;
      this.publicDocumentAccess = publicDocumentAccess;
      this.existOldPassword = publicDocumentAccess?.hasPassword;
      this.hasPassword = this.existOldPassword;
      this.showPasswordInput = false;
      this.showExpirationDateInput = !!publicDocumentAccess?.expirationDate;
      const currentExpirationDate = publicDocumentAccess?.expirationDate?.time || publicDocumentAccess?.expirationDate;
      this.expirationDate = this.showExpirationDateInput && new Date(currentExpirationDate).toISOString().slice(0, 10) || null;
      this.$refs.publicDocumentOptionsDrawer.open();
    },
    validateAccessOptions() {
      this.checkPasswordValid();
      return this.$refs.form.validate();
    },
    cancel() {
      this.$refs.form.reset();
      this.$refs.form.resetValidation();
      this.$refs.publicDocumentOptionsDrawer.close();
    },
    getExpirationDelayDate() {
      const delayDate = new Date(new Date());
      switch (this.delayType) {
      case 'day':
        return delayDate.setDate(delayDate.getDate() + parseInt(this.delayTypeTimes));
      case 'week':
        return delayDate.setDate(delayDate.getDate() + this.delayTypeTimes * 7);
      case 'month':
        return delayDate.setDate(delayDate.getDate() + this.delayTypeTimes * 30);
      case 'year':
        return delayDate.setDate(delayDate.getDate() + this.delayTypeTimes * 365);
      }
    },
    getValidationState() {
      let passwordValid;
      if (this.showPasswordInput) {
        passwordValid = !!this.password && this.passwordRegex.test(this.password)
                                        && !!this.confirmPassword
                                        && this.password === this.confirmPassword;
      } else {
        passwordValid = !this.hasPassword || !!this.publicDocumentAccess?.decodedPassword;
      }
      const expirationValid = !this.showExpirationDateInput
          || (this.expirationDateType === 'delayDate' && !!this.delayTypeTimes)
          || !!this.expirationDate;
      return passwordValid && expirationValid;
    },
    getAccessOptions() {
      this.checkPasswordValid();
      const options = { hasPassword: false, password: null, expirationDate: 0 };
      if (this.showExpirationDateInput) {
        if (this.expirationDate && this.expirationDateType === 'specificDate') {
          const date = new Date(this.expirationDate).setHours(23, 59, 59, 0o00);
          options.expirationDate = new Date(date).getTime();
        } else if (this.expirationDateType === 'delayDate') {
          options.expirationDate = this.getExpirationDelayDate();
        }
      }
      options.hasPassword = this.hasPassword;
      options.password = this.hasPassword
        ? (this.password || this.publicDocumentAccess?.decodedPassword)
        : null;
      return options;
    },
    closed() {
      this.password = null;
      this.expirationDate = null;
      this.startCheckPassword = false;
      this.publicDocumentAccess = {};
    },
    computeDate(value) {
      if (value && String(value).trim()) {
        const dateObj = this.$dateUtil.getDateObjectFromString(String(value).trim(), true);
        return dateObj.toLocaleDateString(this.lang, this.dateFormat).replaceAll('/','-');
      } else {
        return null;
      }
    },
    copyPublicAccessLink() {
      navigator.clipboard.writeText(this.publicLinkUrl).then(() => {
        this.$root.$emit('show-alert', {
          type: 'success',
          message: this.$t('documents.label.public.link.copied')
        });
      }).catch(() => {
        this.$root.$emit('show-alert', {
          type: 'error',
          message: this.$t('document.public.access.copyLink.error.message')
        });
      });
    },
  }
};
</script>
