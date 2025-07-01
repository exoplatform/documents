<!--
 *
 * Copyright (C) 2025 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <gnu.org/licenses>.
 *
-->
<template>
  <exo-drawer
    id="DocumentSettingsDrawer"
    ref="drawer"
    v-model="drawer"
    :loading="saving"
    allow-expand
    right>
    <template #title>
      {{ $t('documents.settings.drawer.title') }}
    </template>
    <template v-if="drawer" #content>
      <div class="pa-5" flat>
        <div class="mb-2 text-header">{{ $t('documents.settings.displayOptions') }}</div>
        <div class="mb-2 font-weight-bold">{{ $t('documents.settings.defaultView') }}</div>
        <v-radio-group
          v-model="settings.defaultView"
          class="pa-0 ma-0 full-width"
          mandatory>
          <v-radio
            v-for="view in viewList"
            :key="view.id"
            :label="$t(`${getLabel(view.id)}`)"
            :value="view.id"
            :disabled="!view.enabled" />
        </v-radio-group>
        <div class="d-flex align-center text-start">
                  <div>{{ $t('documents.settings.collapsedTreeView') }}</div>
                  <v-spacer />
                  <v-switch
                    v-model="settings.collapsedTreeView"
                    class="ma-0 width-fit-content" />
                </div>
      </div>
    </template>
    <template #footer>
      <div class="d-flex">
        <v-btn
          :disabled="saving"
          class="btn ms-auto me-2"
          @click="close">
          {{ $t('documents.button.cancel') }}
        </v-btn>
        <v-btn
          :disabled="disabled"
          :loading="saving"
          class="btn btn-primary"
          elevation="0"
          @click="save">
          {{ $t('documents.label.apply') }}
        </v-btn>
      </div>
    </template>
  </exo-drawer>
</template>
<script>
export default {
  props: {
    viewList: {
      type: Array,
      default: () => []
    },
  },
  data: () => ({
    drawer: false,
    saving: false,
    settings: {},
  }),

  created() {
    this.$root.$on('open-document-settings', this.open);
  },
  beforeDestroy() {
    this.$root.$off('open-document-settings', this.open);
  },
  methods: {
    open() {
      this.settings = Object.assign({}, this.$root.settings);
      this.settings.defaultView = this.$root.settings.defaultView || this.settings.enabledViewList[0]?.id;
      this.settings.collapsedTreeView = this.$root.settings.collapsedTreeView !== null ? this.$root.settings.collapsedTreeView : true;
      this.$refs.drawer.open();
    },
    close() {
      this.$refs.drawer.close();
    },
    getLabel(view) {
      return this.viewList.find(item => item.id === view)?.name || view;
    }, 
    changeEnabledList() {
      this.settings.enabledViewList = this.viewList.filter(item => item.enabled).map(item => item.id);
      if (!this.settings.enabledViewList.includes(this.settings.defaultView)){
        this.settings.defaultView = this.settings.enabledViewList[0];
      }
    }, 
    async save() {
      this.saving = true;
      try {
        const formData = new FormData();
        this.$root.settings = this.settings;
        formData.append('settings', JSON.stringify(this.settings));
        const urlParams = new URLSearchParams(formData).toString();
        const response = await fetch(this.$root.settingsSaveUrl, {
          method: 'POST',
          credentials: 'include',
          headers: {
            'Content-Type': 'application/x-www-form-urlencoded',
          },
          body: urlParams,
        });
        if (response?.ok) {
          this.$root.$emit('alert-message', this.$t('documents.settings.savedSuccessfully'), 'success');
          this.$root.$emit('documents-settings-updated', this.settings);
          this.close();
        } else {
          this.$root.$emit('alert-message', this.$t('documents.settings.saveError'), 'error');
        }
      } finally {
        this.saving = false;
      }
    }
  },
};
</script>