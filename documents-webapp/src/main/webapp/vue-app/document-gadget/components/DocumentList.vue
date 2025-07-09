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
  <v-app>
    <v-hover v-model="hover">
      <widget-wrapper
        :loading="loading"
        extra-class="application-body fill-height">
        <template #title>
          <div class="d-flex flex-grow-1 flex-shrink-1 full-width align-center position-relative">
            <div
              v-if="!emptyWidget"
              class="widget-text-header text-none text-truncate d-flex align-center mb-5">
              {{ $t('documents.documentGadgetSettings.title') }}
            </div>
            <div
              :class="{
                'mt-2 me-2': emptyWidget,
                'l-0': $vuetify.rtl,
                'r-0': !$vuetify.rtl,
              }"
              class="position-absolute absolute-vertical-center z-index-one">
              <v-btn
                v-if="!emptyWidget"
                :icon="hoverEdit"
                :small="hoverEdit"
                height="auto"
                min-width="auto"
                class="pa-0"
                text>
                <span v-if="!hoverEdit" class="primary--text text-none">{{ $t('documents.documentGadgetSettings.seeMore') }}</span>
              </v-btn>
              <v-fab-transition hide-on-leave>
                <v-btn
                  v-show="hoverEdit"
                  :title="$t('documents.documentGadgetSettings.editTooltip')"
                  small
                  icon
                  @click="$root.$emit('document-gadget-settings')">
                  <v-icon size="18">fa-cog</v-icon>
                </v-btn>
              </v-fab-transition>
            </div>
          </div>
        </template>
        <template v-if="initialized" #default>
          <div>
            <v-list class="pa-0">
              <document-list-widget-item
                v-for="file in files"
                :key="file.id"
                :file="file" />
            </v-list>
          </div>
        </template>
      </widget-wrapper>
    </v-hover>
  </v-app>
</template>
<script>
export default {
  data: () => ({
    hover: false,
    loading: false,
    initialized: false,
    applicationMounted: false,
    files: [],
  }),
  computed: {
    hoverEdit() {
      return this.hover && this.$root.canEdit;
    },
    emptyWidget() {
      return !this.files?.length && this.initialized && this.applicationMounted;
    },
  },
  watch: {
    loading() {
      if (!this.loading) {
        this.initialized = true;
      }
    },
    initialized() {
      if (this.initialized) {
        this.$root.$applicationLoaded();
      }
    },
    emptyWidget() {
      if (this.emptyWidget && !this.$root.canEdit) {
        this.$root.$updateApplicationVisibility(false);
      }
    },
  },
  created() {
    this.getFiles();
  },
  mounted() {
    this.applicationMounted = true;
  },
  methods: {
    getFiles() {
      this.loading = true;
      const filter = {
        ownerId: eXo.env.portal.spaceIdentityId || eXo.env.portal.userIdentityId,
        listingType: 'TIMELINE',
      };
      return this.$documentFileService.getDocumentItems(filter, null, null, 0, 4, null).then(files => {
        this.files = files;
      }).finally(() => this.loading = false);
    },
  },
};
</script>