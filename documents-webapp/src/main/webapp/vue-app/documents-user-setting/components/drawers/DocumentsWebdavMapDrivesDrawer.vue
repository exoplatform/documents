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
    id="documentsWebdavDrawer"
    :loading="loading"
    :right="!$vuetify.rtl"
    go-back-button>
    <template #title>
      {{ $t('UserSettings.documents.webdav.mapDrive.title') }}
    </template>
    <template v-if="drawer" #content>
      <div class="d-flex flex-column align-start pa-5">
        <v-stepper
          v-model="stepper"
          :class="{
            'pe-3' : isMobile,
          }"
          class="ma-0 py-0 full-width"
          vertical
          flat>
          <v-stepper-step
            :step="1"
            class="ma-0 pa-0"
            editable>
            {{ $t('UserSettings.documents.webdav.selectTheDriveToMap') }}
          </v-stepper-step>
          <v-stepper-content :step="1" class="py-2 px-0 ma-0 no-border">
            <v-radio-group v-model="driveType">
              <v-radio
                :label="$t('UserSettings.documents.webdav.allDrives')"
                value="ALL"
                on-icon="fa-lg far fa-dot-circle"
                off-icon="fa-lg far fa-circle"
                @click="driveType = 'ALL'" />
              <v-radio
                :label="$t('UserSettings.documents.webdav.personalDrive')"
                value="PERSONAL"
                on-icon="fa-lg far fa-dot-circle"
                off-icon="fa-lg far fa-circle"
                @click="driveType = 'PERSONAL'" />
              <v-radio
                :label="$t('UserSettings.documents.webdav.spaceDrive')"
                value="SPACE"
                on-icon="fa-lg far fa-dot-circle"
                off-icon="fa-lg far fa-circle"
                @click="driveType = 'SPACE'" />
              <identity-suggester
                v-if="driveType === 'SPACE'"
                v-model="spaceIdentity"
                :labels="spaceSuggesterLabels"
                :include-users="false"
                :width="220"
                class="mt-n2"
                include-spaces />
            </v-radio-group>
          </v-stepper-content>
          <v-stepper-step
            :step="2"
            class="ma-0 pa-0"
            editable>
            {{ $t('UserSettings.documents.webdav.mapNetworkDrive') }}
          </v-stepper-step>
          <v-stepper-content :step="2" class="py-2 px-0 ma-0 no-border">
            <div class="d-flex mt-4">
              <div>1. {{ $t('UserSettings.documents.webdav.mapNetworkDeviceStep1') }}</div>
              <div v-if="canCopy" class="ms-auto">
                <v-tooltip left>
                  <template #activator="{on, attrs}">
                    <v-btn
                      v-bind="attrs"
                      v-on="on"
                      :aria-label="hrefTooltip"
                      class="mt-n2"
                      icon
                      @click="copyHref">
                      <v-icon size="16">fa fa-copy</v-icon>
                    </v-btn>
                  </template>
                  <span>{{ hrefTooltip }}</span>
                </v-tooltip>
              </div>
            </div>
            <div v-if="!canCopy" class="full-width mt-2">
              <v-text-field
                v-model="href"
                prepend-inner-icon="fas fa-link icon-default-color ms-n2"
                class="pa-0 full-width"
                outlined
                readonly
                dense />
            </div>
            <div class="d-flex flex-column align-start text-start mt-2">
              <div>2. {{ $t('UserSettings.documents.webdav.mapNetworkDeviceStep2') }}</div>
              <div class="text-subtitle pa-0 mt-2">
                <div v-for="l in tipLabels" :key="l">{{ $t(l) }}</div>
              </div>
            </div>
            <v-img
              v-if="step2ImageSrc"
              :src="step2ImageSrc"
              max-height="175"
              class="mt-2"
              contain />
            <div class="d-flex flex-column align-start text-start mt-2">
              <div>3. {{ $t('UserSettings.documents.webdav.mapNetworkDeviceStep3') }}</div>
            </div>
            <v-img
              v-if="step3ImageSrc"
              :src="step3ImageSrc"
              max-height="175"
              class="mt-2"
              contain />
            <template v-if="password">
              <div class="d-flex flex-column align-start text-start mt-2">
                <div>4. {{ $t('UserSettings.documents.webdav.mapNetworkDeviceStep4') }}</div>
              </div>
              <documents-credential-inputs
                :password="password"
                class="mt-2 full-width text-start" />
            </template>
            <div v-else class="d-flex flex-column align-start text-start mt-2">
              <div>4. {{ $t('UserSettings.documents.webdav.mapNetworkDeviceStep4.2') }}</div>
            </div>
            <v-img
              v-if="step4ImageSrc"
              :src="step4ImageSrc"
              max-height="175"
              class="mt-2"
              contain />
          </v-stepper-content>
        </v-stepper>
      </div>
    </template>
    <template v-if="drawer" #footer>
      <div class="d-flex align-center">
        <v-btn
          v-if="stepper > 1"
          :title="$t('UserSettings.documents.webdav.previous')"
          :disabled="saving"
          class="btn me-2 hidden-xs-only"
          @click="stepper--">
          {{ $t('UserSettings.documents.webdav.previous') }}
        </v-btn>
        <v-btn
          :title="$t('UserSettings.documents.webdav.close')"
          class="btn ms-auto me-2"
          @click="close">
          {{ $t('UserSettings.documents.webdav.close') }}
        </v-btn>
        <v-btn
          v-if="stepper < 2"
          :disabled="disabledNextStep"
          :loading="saving"
          class="btn primary"
          @click="stepper++">
          {{ $t('UserSettings.documents.webdav.next') }}
        </v-btn>
      </div>
    </template>
  </exo-drawer>
</template>
<script>
export default {
  data: () => ({
    drawer: false,
    password: null,
    stepper: 1,
    driveType: 'ALL',
    userIdentity: null,
    spaceIdentity: null,
    spaceIdentityId: null,
    spaceIdentityRemoteId: null,
    hrefCopied: false,
    canCopy: false,
    tipsByOs: {
      windows: {
        labels: [
          'UserSettings.documents.webdav.mapNetworkDeviceStep2.windowsOs.tip1',
          'UserSettings.documents.webdav.mapNetworkDeviceStep2.windowsOs.tip2',
          'UserSettings.documents.webdav.mapNetworkDeviceStep2.windowsOs.tip3',
          'UserSettings.documents.webdav.mapNetworkDeviceStep2.windowsOs.tip4',
        ],
        images: {
          step2: '/documents-portlet/images/addNetworkLocation-windowsOs.webp',
          step3: '/documents-portlet/images/addNetworkForm-windowsOs.webp',
          step4: '/documents-portlet/images/addNetworkCredentials-windowsOs.webp',
        },
      },
      linux: {
        labels: [
          'UserSettings.documents.webdav.mapNetworkDeviceStep2.linuxOs.tip1',
          'UserSettings.documents.webdav.mapNetworkDeviceStep2.linuxOs.tip2',
          'UserSettings.documents.webdav.mapNetworkDeviceStep2.linuxOs.tip3',
          'UserSettings.documents.webdav.mapNetworkDeviceStep2.linuxOs.tip4',
        ],
        images: {
          step2: '/documents-portlet/images/addNetworkLocation-linuxOs.webp',
          step4: '/documents-portlet/images/addNetworkCredentials-linuxOs.webp',
        },
      },
      mac: {
        labels: [
          'UserSettings.documents.webdav.mapNetworkDeviceStep2.macOs.tip1',
          'UserSettings.documents.webdav.mapNetworkDeviceStep2.macOs.tip2',
          'UserSettings.documents.webdav.mapNetworkDeviceStep2.macOs.tip3',
        ],
        images: {
          step2: '/documents-portlet/images/addNetworkLocation-macOs.webp',
          step3: '/documents-portlet/images/addNetworkForm-macOs.webp',
          step4: '/documents-portlet/images/addNetworkCredentials-macOs.webp',
        },
      },
    },
  }),
  computed: {
    isMobile() {
      return this.$vuetify.breakpoint.mobile;
    },
    userFullName() {
      return this.userIdentity?.profile?.fullname;
    },
    tips() {
      if (this.$utils.isLinuxOs()) {
        return this.tipsByOs.linux;
      } else if (this.$utils.isMacOs()) {
        return this.tipsByOs.mac;
      } else {
        // Default if none matches
        return this.tipsByOs.windows;
      }
    },
    tipLabels() {
      return this.tips.labels;
    },
    step2ImageSrc() {
      return this.tips.images?.step2;
    },
    step3ImageSrc() {
      return this.tips.images?.step3;
    },
    step4ImageSrc() {
      return this.tips.images?.step4;
    },
    disabledNextStep() {
      return this.driveType === 'SPACE' && !this.spaceIdentityId;
    },
    spaceSuggesterLabels() {
      return {
        placeholder: this.$t('UserSettings.documents.webdav.spaceSearchPlaceholder'),
        noDataLabel: this.$t('UserSettings.documents.webdav.spaceNoDataLabel'),
      };
    },
    href() {
      // The personal drive is addressed by the user full name, a Space by its
      // pretty name — never by the Space display name, which may hold a '/'
      // and would split the drive into two path segments
      if (this.driveType === 'ALL') {
        return `${window.location.origin}/webdav/drives`;
      } else if (this.driveType === 'PERSONAL') {
        return `${window.location.origin}/webdav/drives/d/${this.userFullName} (${eXo.env.portal.userIdentityId})`;
      } else if (this.driveType === 'SPACE' && this.spaceIdentityId) {
        return `${window.location.origin}/webdav/drives/d/${this.spaceIdentityRemoteId} (${this.spaceIdentityId})`;
      } else {
        return null;
      }
    },
    hrefTooltip() {
      return this.hrefCopied ? this.$t('UserSettings.documents.webdav.copied') : this.$t('UserSettings.documents.webdav.copy');
    },
  },
  watch: {
    driveType() {
      this.spaceIdentity = null;
    },
    async spaceIdentity() {
      this.spaceIdentityId = null;
      this.spaceIdentityRemoteId = null;
      if (this.spaceIdentity) {
        const identity = await this.$identityService.getIdentityByProviderIdAndRemoteId(this.spaceIdentity.providerId, this.spaceIdentity.remoteId);
        this.spaceIdentityId = identity?.id;
        this.spaceIdentityRemoteId = identity?.remoteId || this.spaceIdentity.remoteId;
      }
    },
  },
  methods: {
    close() {
      this.$refs.drawer.close();
    },
    async open(password) {
      this.password = password;
      this.stepper = 1;
      this.spaceIdentity = null;
      this.driveType = 'ALL';
      this.$refs.drawer.open();
      if (this.userIdentity == null) {
        this.userIdentity = await this.$identityService.getIdentityByProviderIdAndRemoteId('organization', eXo.env.portal.userName);
      }
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
    },
    copyHref() {
      navigator.clipboard.writeText(this.href);
      this.hrefCopied = true;
      window.setTimeout(() => this.hrefCopied = false, 2000);
    },
  },
};
</script>