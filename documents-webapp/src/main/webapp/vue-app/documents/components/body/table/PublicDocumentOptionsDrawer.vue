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
  <exo-drawer
    ref="publicDocumentOptionsDrawer"
    class="publicDocumentOptionsDrawer"
    @closed="closed"
    right>
    <template slot="title">
      <p class="ma-auto">
        {{ $t('documents.public.access.options.message') }}
      </p>
    </template>
    <template slot="content">
      <div
        class="flex-wrap pa-4 mt-5">
        <v-form
          ref="form"
          v-model="valid">
          <div class="d-flex flex-row">
            <div class="d-flex flex-column full-width ma-auto">
              <p>
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
                class="pe-4 ps-4 pt-3 mb-2">
                <v-label
                  for="documentPassword">
                  <span class="text-body-2 font-weight-bold">
                    {{ $t('documents.public.link.new.password.message') }}
                  </span>*
                </v-label>
                <v-text-field
                  v-model="publicDocumentAccessOptionsObject.password"
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
                  <span class="text-body-2 font-weight-bold">
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
              <p>
                {{ $t('documents.public.access.password.modify.message') }}
              </p>
            </div>
            <div class="d-flex flex-column ms-n11">
              <v-tooltip bottom>
                <template #activator="{ on, attrs}">
                  <v-btn
                    v-bind="attrs"
                    v-on="on"
                    class="mt-0 me-1 mt-n2"
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
          <div class="d-flex flex-row">
            <div class="d-flex flex-column full-width ma-auto">
              <p>
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
                class="pe-4 ps-4 mt-n4 mb-2">
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
                        v-model="expirationDate"
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
        </v-form>
      </div>
    </template>
    <template slot="footer">
      <div class="float-right">
        <v-btn
          :loading="isSaving"
          @click="cancel"
          class="btn">
          {{ $t('documents.button.cancel') }}
        </v-btn>
        <v-btn
          :loading="isSaving"
          :disabled="!valid"
          @click="savePublicAccessOptions"
          class="btn btn-primary me-2">
          {{ $t('documents.save') }}
        </v-btn>
      </div>
    </template>
  </exo-drawer>
</template>
<script>
export default {
  data: () => ({
    expirationDate: null,
    publicDocumentAccessOptionsObject: {},
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
    existOldPassword: false
  }),
  created() {
    document.addEventListener('mousedown', this.closeDatePickerMenu);
    this.confirmPasswordRules = [v => !!v && v === this.publicDocumentAccessOptionsObject?.password
        || this.$t('documents.public.access.password.not.identical')];
    this.$root.$on('open-public-document-options-drawer', this.open);
  },
  computed: {
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
    }
  },
  watch: {
    hasPassword(value) {
      if (!value) {
        this.showPasswordInput = false;
      } else if (!this.existOldPassword) {
        this.showPasswordInput = true;
      }
    },
    showExpirationDateInput() {
      if (!this.showExpirationDateInput) {
        this.expirationDate = null;
      }
    },
    showPasswordInput() {
      if (!this.showPasswordInput) {
        this.startCheckPassword = false;
      }
    },
    delayTypeTimes(value) {
      if (value > 6 && this.delayType === 'day') {
        this.delayType = 'week';
        this.delayTypeTimes = 1;
      } else if (value > 3 && this.delayType === 'week') {
        this.delayType = 'month';
        this.delayTypeTimes = 1;
      } else if (value > 11 && this.delayType === 'month') {
        this.delayType = 'year';
        this.delayTypeTimes = 1;
      }
    }
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
      const passwordValue = this.publicDocumentAccessOptionsObject?.password;
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
    },
    getExpirationDelayDate() {
      const delayDate = new Date(new Date());
      switch (this.delayType) {
      case 'day':
        return delayDate.setDate(delayDate.getDate() + this.delayTypeTimes);
      case 'week':
        return delayDate.setDate(delayDate.getDate() + this.delayTypeTimes * 7);
      case 'month':
        return delayDate.setDate(delayDate.getDate() + this.delayTypeTimes * 30);
      case 'year':
        return delayDate.setDate(delayDate.getDate() + this.delayTypeTimes * 365);
      }
    },
    savePublicAccessOptions() {
      if (!this.validateAccessOptions()) {
        return;
      }
      this.publicDocumentAccessOptionsObject.expirationDate = 0;
      if (this.expirationDate && this.expirationDateType === 'specificDate') {
        const date = new Date(this.expirationDate).setHours(23, 59, 59, 0o00);
        this.publicDocumentAccessOptionsObject.expirationDate = new Date(date).getTime();
      } else if (this.expirationDateType === 'delayDate') {
        this.publicDocumentAccessOptionsObject.expirationDate = this.getExpirationDelayDate();
      }
      this.publicDocumentAccessOptionsObject.hasPassword = this.hasPassword;
      this.$root.$emit('set-document-public-access-options', this.publicDocumentAccessOptionsObject);
      this.$refs.publicDocumentOptionsDrawer.close();
    },
    closed() {
      this.publicDocumentAccessOptionsObject = {};
      this.expirationDate = null;
      this.startCheckPassword = false;
      this.publicDocumentAccess = {};
    }
  }
};
</script>