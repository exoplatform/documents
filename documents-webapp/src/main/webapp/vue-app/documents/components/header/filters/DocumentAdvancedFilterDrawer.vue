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
      <v-card flat class="px-2 pt-2">      
        <div class="font-weight-bold text-start text-color body-2 pa-2 mb-5">{{ $t('documents.advanced.filter.drawer.file.types') }}</div>
        <v-list>
          <v-list-item class="my-n11">
            <v-list-item-action class="mx-3">
              <v-checkbox
                v-model="advancedFilter.fileType"
                class="ma-auto typeCheckbox"
                value="documents" />
            </v-list-item-action>
            <v-list-item-content>
              <v-row class="ma-auto">
                <v-icon
                  :size="26"
                  :color="docIcon && docIcon.color">
                  {{ docIcon && docIcon.class }}
                </v-icon>
                <div class="pa-5">{{ $t('documents.file.type.document') }}</div>
              </v-row>
            </v-list-item-content>        
          </v-list-item>
          <v-list-item class="my-n11">
            <v-list-item-action class="mx-3">
              <v-checkbox
                v-model="advancedFilter.fileType"
                class="ma-auto typeCheckbox"
                value="sheets" />
            </v-list-item-action>
            <v-list-item-content>
              <v-row class="ma-auto">
                <v-icon
                  :size="26"
                  :color="sheetIcon && sheetIcon.color">
                  {{ sheetIcon && sheetIcon.class }}
                </v-icon>
                <div class="pa-5 fileTypeLabel">{{ $t('documents.file.type.sheet') }}</div>
              </v-row>
            </v-list-item-content>        
          </v-list-item>
          <v-list-item class="my-n11">
            <v-list-item-action class="mx-3">
              <v-checkbox
                v-model="advancedFilter.fileType"
                class="ma-auto typeCheckbox"
                value="presentations" />
            </v-list-item-action>
            <v-list-item-content>
              <v-row class="ma-auto">
                <v-icon
                  :size="26"
                  :color="prezIcon && prezIcon.color">
                  {{ prezIcon && prezIcon.class }}
                </v-icon>
                <div class="pa-5 fileTypeLabel">{{ $t('documents.file.type.presentation') }}</div>
              </v-row>
            </v-list-item-content>        
          </v-list-item>
          <v-list-item class="my-n11">
            <v-list-item-action class="mx-3">
              <v-checkbox
                v-model="advancedFilter.fileType"
                class="ma-auto typeCheckbox"
                value="pdfs" />
            </v-list-item-action>
            <v-list-item-content>
              <v-row class="ma-auto">
                <v-icon
                  :size="26"
                  :color="pdfIcon && pdfIcon.color">
                  {{ pdfIcon && pdfIcon.class }}
                </v-icon>
                <div class="pa-5 fileTypeLabel">{{ $t('documents.file.type.pdf') }}</div>
              </v-row>
            </v-list-item-content>        
          </v-list-item>
          <v-list-item class="my-n11">
            <v-list-item-action class="mx-3">
              <v-checkbox
                v-model="advancedFilter.fileType"
                class="ma-auto typeCheckbox"
                value="images" />
            </v-list-item-action>
            <v-list-item-content>
              <v-row class="ma-auto">
                <v-icon
                  :size="26"
                  :color="imageIcon && imageIcon.color">
                  {{ imageIcon && imageIcon.class }}
                </v-icon>
                <div class="pa-5 fileTypeLabel">{{ $t('documents.file.type.image') }}</div>
              </v-row>
            </v-list-item-content>        
          </v-list-item>
          <v-list-item class="my-n11">
            <v-list-item-action class="mx-3">
              <v-checkbox
                v-model="advancedFilter.fileType"
                class="ma-auto typeCheckbox"
                value="videos" />
            </v-list-item-action>
            <v-list-item-content>
              <v-row class="ma-auto">
                <v-icon
                  :size="26"
                  :color="videoIcon && videoIcon.color">
                  {{ videoIcon && videoIcon.class }}
                </v-icon>
                <div class="pa-5 fileTypeLabel">{{ $t('documents.file.type.video') }}</div>
              </v-row>
            </v-list-item-content>        
          </v-list-item>
        </v-list>

        <div class="font-weight-bold text-start text-color body-2  px-2 pb-4 pt-6">{{ $t('documents.advanced.filter.drawer.update.date') }}</div>

        <documents-select-period v-model="advancedFilter.selectedPeriod" />

        <div class="font-weight-bold text-start text-color body-2 px-2 pb-4 pt-4">{{ $t('documents.advanced.filter.drawer.file.size') }}</div>
         
        <div class="d-flex ps-3">
          <div class="font-weight-bold text-start text-color body-2 pt-4 pe-2">{{ $t('documents.advanced.filter.drawer.min') }}:</div>
          <v-text-field
            v-model="advancedFilter.minSize"
            class="me-2 py-2"
            :suffix="$t('documents.label.mega')"
            outlined
            dense
            type="number"
            min="0" />
          <div class="font-weight-bold text-start text-color body-2 pt-4 pe-2 ps-2">{{ $t('documents.advanced.filter.drawer.max') }}:</div>
          <v-text-field
            v-model="advancedFilter.maxSize"
            class="me-2 py-2"
            :suffix="$t('documents.label.mega')"
            outlined
            dense
            type="number"
            min="0" />
        </div>
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