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
        <div class="mt-4 mb-2 text-header">{{ $t('documents.settings.filterOptions') }}</div>
        <div class="d-flex full-width align-center text-start">
          <div>{{ $t('documents.settings.filterOptions.title') }}</div>
          <v-spacer />
          <v-switch
            v-model="settings.allowFilteringPerCategory"
            class="ma-0 pt-2 width-fit-content" />
        </div>
        <div v-if="settings.allowFilteringPerCategory" class="d-flex full-width align-center text-start">
          <div>{{ $t('documents.settings.setMaximumSubcategoryDepth') }}</div>
          <v-spacer />
          <number-input
            v-model="settings.categoryDepth"
            :step="1"
            :min="0"
            :max="50" />
        </div>
        <div class="mt-4 mb-2 text-header">{{ $t('documents.settings.filterList') }}</div>
        <div class="d-flex align-center text-start">
          <div>{{ $t('documents.settings.filterListPerCategory') }}</div>
          <v-spacer />
          <v-switch
            v-model="filterPerCategories"
            class="ma-0 width-fit-content" />
        </div>
        <div v-if="filterPerCategories" class="mt-4">
          <category-suggester
            v-model="categoryId"
            class="mt-n2 mb-4 mx-0 pa-0"
            label=""
            access-permission />
          <v-list class="pa-0" dense>
            <v-list-item
              v-for="(c, index) in selectedCategories"
              :key="c.id"
              class="pa-0"
              dense>
              <v-list-item-icon class="me-2 my-auto">
                <v-icon size="24">{{ c.icon }}</v-icon>
              </v-list-item-icon>
              <v-list-item-content class="me-2 pa-0 text-truncate">
                <v-list-item-title class="text-truncate">
                  {{ c.name }}
                </v-list-item-title>
              </v-list-item-content>
              <v-list-item-action class="mx-0 my-auto">
                <v-btn
                  :title="$t('documents.settings.deleteCategory')"
                  icon
                  @click="removeItem(index, settings.categoryIds)">
                  <v-icon size="18" color="error">fa-trash</v-icon>
                </v-btn>
              </v-list-item-action>
            </v-list-item>
          </v-list>
        </div>

        <div class="d-flex align-center text-start">
          <div>{{ $t('activityStream.settings.filterExcludeCategory') }}</div>
          <v-spacer />
          <v-switch
            v-model="filterPerExcludeCategories"
            class="ma-0 width-fit-content" />
        </div>
        <div v-if="filterPerExcludeCategories" class="mt-4">
          <category-suggester
            v-model="excludeCategoryId"
            class="mt-n2 mb-4 mx-0 pa-0"
            label=""
            access-permission />
          <v-list class="pa-0" dense>
            <v-list-item
              v-for="(c, index) in selectedExcludeCategories"
              :key="c.id"
              class="pa-0"
              dense>
              <v-list-item-icon class="me-2 my-auto">
                <v-icon size="24">{{ c.icon }}</v-icon>
              </v-list-item-icon>
              <v-list-item-content class="me-2 pa-0 text-truncate">
                <v-list-item-title class="text-truncate">
                  {{ c.name }}
                </v-list-item-title>
              </v-list-item-content>
              <v-list-item-action class="mx-0 my-auto">
                <v-btn
                  :title="$t('activityStream.settings.deleteCategory')"
                  icon
                  @click="removeItem(index, settings.excludeCategoryIds)">
                  <v-icon size="18" color="error">fa-trash</v-icon>
                </v-btn>
              </v-list-item-action>
            </v-list-item>
          </v-list>
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
          :disabled="!modified"
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
    originalSettings: {},
    categoryId: null,
    excludeCategoryId: null,
    selectedCategories: [],
    selectedExcludeCategories: [],
    filterPerCategories: false,
    filterPerExcludeCategories: false,
  }),
  computed: {
    modified() {
      return JSON.stringify(this.settings) !== JSON.stringify(this.originalSettings);
    },
    categoryIds() {
      return this.settings.categoryIds;
    },
    excludeCategoryIds() {
      return this.settings.excludeCategoryIds;
    },
  },
  watch: {
    async categoryId() {
      if (this.categoryId) {
        if (!Array.isArray(this.settings.categoryIds)) {
          this.settings.categoryIds = [];
        }
        if (this.settings.categoryIds.indexOf(this.categoryId) < 0) {
          this.settings.categoryIds.push(this.categoryId);
        }
        await this.$nextTick();
        this.categoryId = null;
      }
    },
    async excludeCategoryId() {
      if (this.excludeCategoryId) {
        if (!Array.isArray(this.settings.excludeCategoryIds)) {
          this.settings.excludeCategoryIds = [];
        }
        if (this.settings.excludeCategoryIds.indexOf(this.excludeCategoryId) < 0) {
          this.settings.excludeCategoryIds.push(this.excludeCategoryId);
        }
        await this.$nextTick();
        this.excludeCategoryId = null;
      }
    },
    async categoryIds() {
      if (!this.categoryIds?.length) {
        this.selectedCategories = [];
      } else {
        this.selectedCategories = await Promise.all(this.categoryIds.map(id => this.$categoryService.getCategory(id)));
      }
    },
    filterPerCategories() {
      if (this.drawer && !this.filterPerCategories) {
        this.settings.categoryIds = [];
      }
    },
    async excludeCategoryIds() {
      if (!this.excludeCategoryIds?.length) {
        this.selectedExcludeCategories = [];
      } else {
        this.selectedExcludeCategories = await Promise.all(this.excludeCategoryIds.map(id => this.$categoryService.getCategory(id)));
      }
    },
    filterPerExcludeCategories() {
      if (this.drawer && !this.filterPerExcludeCategories) {
        this.settings.excludeCategoryIds = [];
      }
    },
  },
  created() {
    this.$root.$on('open-document-settings', this.open);
  },
  beforeDestroy() {
    this.$root.$off('open-document-settings', this.open);
  },
  methods: {
    open() {
      this.settings = Object.assign({}, this.$root.settings);
      this.originalSettings = JSON.parse(JSON.stringify(this.$root.settings));
      this.filterPerCategories = !!this.categoryIds?.length;
      this.filterPerExcludeCategories = !!this.excludeCategoryIds?.length;
      if (!this.settings.enabledViewList || this.settings.enabledViewList.length === 0) {
        this.settings.enabledViewList = this.viewList.map(item => item.id);
      }
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
    },
    removeItem(index, array) {
      array.splice(index, 1);
      this.settings = JSON.parse(JSON.stringify(this.settings));
    },
  },
};
</script>