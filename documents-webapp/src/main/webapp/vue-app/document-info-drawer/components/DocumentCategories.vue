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
  <div v-if="categoriesCount" class="d-flex mb-auto pt-2px d-inline text-no-wrap">
    <v-fade-transition
      v-for="(c, index) in filteredCategories"
      :key="c.id">
      <div v-if="!initialized || !loading || loadedCategories[c.id]">
        <category-chip
          :ref="`category${index}`"
          :category="c"
          chip-class="flex-shrink-0 me-2 body-1"
          breadcrumb
          small
          @select="selectCategory" />
      </div>
    </v-fade-transition>
    <v-btn
      v-if="remainingCount > 0"
      ref="moreButton"
      class="flex-shrink-0 flex-grow-0 px-0 text-subtitle-font-size"
      height="24"
      width="24"
      icon
      @click="openMoreDrawer">
      <span class="primary--text text-subtitle-font-size">
        {{ $t('categories.remainingCount', {
          0: remainingCount,
        }) }}
      </span>
    </v-btn>
    <categories-list-drawer
      v-if="moreDrawer"
      ref="drawer"
      @select="selectCategory" />
  </div>
  <div
    v-else
    class="d-flex mb-auto px-0 pb-0 pt-2px d-inline clickable documentCategories"
    @click="addCategory">
    <v-icon
      size="21"
      class="pe-1">
      fa-th-large
    </v-icon>
    <a class="font-weight-bold ms-2 clickable pt-1 documentCategories">{{ $t('documents.label.addCategories') }}</a>
  </div>
</template>
<script>
export default {
  props: {
    file: {
      type: Object,
      default: null,
    },
  },
  data: () => ({
    categories: null,
    moreDrawer: false,
    loading: false,
    initialized: false,
    loadedCategories: {},
  }),
  computed: {
    filteredCategories() {
      return this.categories?.slice?.(0, 2) || [];
    },
    categoriesCount() {
      return this.categories?.length || 0;
    },
    remainingCount() {
      return this.categoriesCount - 2;
    },
  },
  created() {
    this.refreshCategories();
  },
  methods: {
    async refreshCategories() {
      this.loading = true;
      try {
        if (this.file?.categoryIds?.length) {
          const categories = await Promise.all(
            this.file.categoryIds
              .map(id => this.$categoryService.getCategory(id).catch(() => null))
          );
          this.categories = categories.filter(c => c);
        } else {
          this.categories = [];
        }
      } finally {
        this.initialized = true;
        await this.$nextTick();
        this.loading = false;
        this.categories.forEach(c => this.$set(this.loadedCategories, c.id, true));
      }
    },
    async selectCategory(category) {
      this.$refs?.drawer?.close();
      await this.$nextTick();
      document.dispatchEvent(new CustomEvent('document-category-selected', {detail: {
        categoryId: category.id
      }}));
    },
    async openMoreDrawer() {
      this.moreDrawer = true;
      await this.$nextTick();
      this.$refs?.drawer?.open?.(this.categories);
    },
    addCategory() {
      document.dispatchEvent(new CustomEvent('category-form-drawer-open', {detail: {
        objectType: 'document',
        objectId: this.file.id,
        categoryIds: [],
      }}));
    },
  },
};
</script>