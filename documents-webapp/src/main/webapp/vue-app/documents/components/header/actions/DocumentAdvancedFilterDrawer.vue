<!--
* Copyright (C) 2023 eXo Platform SAS
*
*  This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU Affero General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU Affero General Public License
* along with this program.  If not, see <gnu.org/licenses>.
-->

<template>
  <exo-drawer 
    ref="DocumentAdvancedFilterDrawer"
    class="DocumentAdvancedFilterDrawer"
    right>
    <template slot="title">
      {{ $t('documents.advanced.filter.drawer.title') }}
    </template>
    <template slot="content">
      <v-card flat class="px-2 pt-4">      
        <div class="font-weight-bold text-start text-color body-2 py-4">{{ $t('documents.advanced.filter.drawer.file.types') }}</div>
        <v-list>
          <v-list-item class="my-n6">
            <v-list-item-action class="mx-3">
              <v-checkbox
                v-model="advancedFilter.fileType"
                value="documents" />
            </v-list-item-action>
            <v-list-item-content>
              <v-row class="ma-auto">
                <v-icon
                  :size="24"
                  :color="docIcon && docIcon.color">
                  {{ docIcon && docIcon.class }}
                </v-icon>
                <div class="pa-5">{{ $t('documents.file.type.document') }}</div>
              </v-row>
            </v-list-item-content>        
          </v-list-item>
          <v-list-item class="my-n6">
            <v-list-item-action class="mx-3">
              <v-checkbox
                v-model="advancedFilter.fileType"
                value="sheets" />
            </v-list-item-action>
            <v-list-item-content>
              <v-row class="ma-auto">
                <v-icon
                  :size="24"
                  :color="sheetIcon && sheetIcon.color">
                  {{ sheetIcon && sheetIcon.class }}
                </v-icon>
                <div class="pa-5">{{ $t('documents.file.type.sheet') }}</div>
              </v-row>
            </v-list-item-content>        
          </v-list-item>
          <v-list-item class="my-n6">
            <v-list-item-action class="mx-3">
              <v-checkbox
                v-model="advancedFilter.fileType"
                value="presentations" />
            </v-list-item-action>
            <v-list-item-content>
              <v-row class="ma-auto">
                <v-icon
                  :size="24"
                  :color="prezIcon && prezIcon.color">
                  {{ prezIcon && prezIcon.class }}
                </v-icon>
                <div class="pa-5">{{ $t('documents.file.type.presentation') }}</div>
              </v-row>
            </v-list-item-content>        
          </v-list-item>
          <v-list-item class="my-n6">
            <v-list-item-action class="mx-3">
              <v-checkbox
                v-model="advancedFilter.fileType"
                value="pdfs" />
            </v-list-item-action>
            <v-list-item-content>
              <v-row class="ma-auto">
                <v-icon
                  :size="24"
                  :color="pdfIcon && pdfIcon.color">
                  {{ pdfIcon && pdfIcon.class }}
                </v-icon>
                <div class="pa-5">{{ $t('documents.file.type.pdf') }}</div>
              </v-row>
            </v-list-item-content>        
          </v-list-item>
          <v-list-item class="my-n6">
            <v-list-item-action class="mx-3">
              <v-checkbox
                v-model="advancedFilter.fileType"
                value="images" />
            </v-list-item-action>
            <v-list-item-content>
              <v-row class="ma-auto">
                <v-icon
                  :size="24"
                  :color="imageIcon && imageIcon.color">
                  {{ imageIcon && imageIcon.class }}
                </v-icon>
                <div class="pa-5">{{ $t('documents.file.type.image') }}</div>
              </v-row>
            </v-list-item-content>        
          </v-list-item>
          <v-list-item class="my-n6">
            <v-list-item-action class="mx-3">
              <v-checkbox
                v-model="advancedFilter.fileType"
                value="videos" />
            </v-list-item-action>
            <v-list-item-content>
              <v-row class="ma-auto">
                <v-icon
                  :size="24"
                  :color="videoIcon && videoIcon.color">
                  {{ videoIcon && videoIcon.class }}
                </v-icon>
                <div class="pa-5">{{ $t('documents.file.type.video') }}</div>
              </v-row>
            </v-list-item-content>        
          </v-list-item>
        </v-list>

        <div class="font-weight-bold text-start text-color body-2  py-4">{{ $t('documents.advanced.filter.drawer.update.date') }}</div>

        <v-row class="mr-auto pa-5">
          <documents-select-period v-model="advancedFilter.selectedPeriod" />
        </v-row>

        <div class="font-weight-bold text-start text-color body-2 py-4">{{ $t('documents.advanced.filter.drawer.file.size') }}</div>
         
        <v-row class="mr-auto px-3">
          <v-col
            class="d-flex"
            cols="12"
            sm="12">
            <v-slider
              v-model="advancedFilter.minSize"
              class="align-center"
              :label="$t('documents.advanced.filter.drawer.min')"
              min="0"
              max="2000"
              thumb-label>
              <template #append>
                <v-text-field
                  v-model="advancedFilter.minSize"
                  suffix="MB"
                  class="sliderValue mt-0 pt-0 sizeValue"
                  hide-details
                  single-line
                  type="number" />
              </template>
            </v-slider>
          </v-col>
          <v-col
            class="d-flex"
            cols="12"
            sm="12">
            <v-slider
              v-model="advancedFilter.maxSize"
              class="align-center"
              :label="$t('documents.advanced.filter.drawer.max')"
              min="0"
              max="2000"
              thumb-label>
              <template #append>
                <v-text-field
                  v-model="advancedFilter.maxSize"
                  suffix="MB"
                  class="sliderValue mt-0 pt-0 sizeValue"
                  hide-details
                  single-line
                  type="number" />
              </template>
            </v-slider>
          </v-col>
        </v-row>
      </v-card> 
    </template>
    <template slot="footer">
      <div class="d-flex">
        <v-btn
          @click="init"
          class="btn me-2">
          {{ $t('documents.init') }}
        </v-btn>
        <v-spacer />
        <v-btn
          @click="close"
          class="btn me-2">
          {{ $t('documents.close') }}
        </v-btn>        
        <v-btn
          @click="confirm"
          class="btn btn-primary">
          {{ $t('documents.confirm') }}
        </v-btn>
      </div>
    </template>
  </exo-drawer>
</template>
<script>

export default {

  data: () => (
    {advancedFilter: {
      fileType: [],
      selectedPeriod: null,
      minSize: null,
      maxSize: null,
      docIcon: {},
      prezIcon: {},
      pdfIcon: {},
      videoIcon: {},
      imageIcon: {},
      sheetIcon: {},
    }}
  ),


  created() {
    this.$root.$on('open-advanced-filter-drawer', this.open);
    this.$root.$on('close-advanced-filter-drawer', this.close);
    this.getTypeIcons();
  },
  methods: {
    open() {
      this.$refs.DocumentAdvancedFilterDrawer.open();
    },
    close() {
      this.$refs.DocumentAdvancedFilterDrawer.close();
    },
    confirm() {
      this.$root.$emit('set-advanced-filter', this.advancedFilter);
      this.close();
    },
    init() {
      this.advancedFilter.fileType= [];
      this.advancedFilter.selectedPeriod= null;
      this.advancedFilter.minSize= null;
      this.advancedFilter.maxSize= null;
      this.confirm();
    },
    getTypeIcons() {
      const extensions = extensionRegistry.loadExtensions('documents', 'documents-icons-extension');
      this.docIcon= extensions[0].get('application/msword');
      this.prezIcon= extensions[0].get('application/vnd.ms-powerpoint');
      this.pdfIcon= extensions[0].get('application/pdf');
      this.videoIcon= extensions[0].get('video/mpeg');
      this.imageIcon= extensions[0].get('image/png');
      this.sheetIcon= extensions[0].get('officedocument.spreadsheetml.sheet');

    },
  }
  
};
</script>
