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
    :right="!$vuetify.rtl"
    :loading="loading"
    eager
    @closed="reset">
    <template #title>
      {{ $t('documents.documentGadget.settings.title') }}
    </template>
    <template v-if="drawer" #content>
      <div class="pa-5">
        <div class="mb-2 text-header">{{ $t('documents.documentGadget.settings.displayManagement') }}</div>
        <div class="font-weight-bold mb-2">{{ $t('documents.documentGadget.settings.viewOptions') }}</div>
        <v-radio-group
          v-model="viewOptions"
          class="mt-0"
          mandatory>
          <v-radio value="list">
            <template #label>
              <span class="ms-1">{{ $t('documents.documentGadget.settings.viewOptions.list') }}</span>
            </template>
          </v-radio>
          <v-radio value="cards">
            <template #label>
              <span class="ms-1">{{ $t('documents.documentGadget.settings.viewOptions.cards') }}</span>
            </template>
          </v-radio>
        </v-radio-group>
      </div>
    </template>
    <template #footer>
      <div class="d-flex align-center">
        <v-btn
          :disabled="loading"
          :title="$t('links.label.cancel')"
          class="btn ms-auto me-2"
          @click="close()">
          {{ $t('documents.button.cancel') }}
        </v-btn>
        <v-btn
          :loading="loading"
          color="primary"
          elevation="0"
          @click="save()">
          {{ $t('documents.save') }}
        </v-btn>
      </div>
    </template>
  </exo-drawer>
</template>
<script>
export default {
  data: () => ({
    drawer: false,
    loading: false,
    viewOptions: 'list'
  }),
  computed: {
  },
  created() {
    this.$root.$on('document-gadget-settings', this.open);
  },
  beforeDestroy() {
    this.$root.$off('document-gadget-settings', this.open);
  },
  methods: {
    open() {
      this.reset();
      this.$refs.drawer.open();
    },
    reset() {
      this.viewOptions = this.$root.viewOptions || 'list';
      this.loading = false;
    },
    close() {
      this.$refs.drawer.close();
    },
    async save() {
      this.loading = true;
      const formData = new FormData();
      formData.append('pageRef', this.$root.pageRef);
      formData.append('applicationId', this.$root.portletStorageId);
      const params = new URLSearchParams(formData).toString();
      await fetch(`/layout/rest/pages/application/preferences?${params}`, {
        method: 'PATCH',
        credentials: 'include',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          preferences: [{
            name: 'documentGadgetViewOptions',
            value: String(this.viewOptions),
          }],
        }),
      })
        .then(() => {
          this.$root.viewOptions = this.viewOptions || 'list';
          this.close();
        })
        .finally(() => this.loading = false);
    },
  },
};
</script>
