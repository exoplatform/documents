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
        <div class="text-header px-2 pb-0 pt-2">{{ $t('documents.advanced.filter.drawer.file.types') }}</div>
        <v-list>
          <v-list-item class="ps-2">
            <v-list-item-action class="me-2 ms-0 my-0">
              <v-checkbox
                ripple="false"
                dense
                v-model="tempAdvancedFilter.fileType"
                class="ma-auto typeCheckbox"
                value="documents" />
            </v-list-item-action>
            <v-list-item-content>
              <v-row class="ma-auto pa-0">
                <v-icon
                  :size="16"
                  class="my-auto"
                  :color="docIcon && docIcon.color">
                  {{ docIcon && docIcon.class }}
                </v-icon>
                <div class="px-2 my-auto pt-1">{{ $t('documents.file.type.document') }}</div>
              </v-row>
            </v-list-item-content>        
          </v-list-item>
          <v-list-item class="ps-2">
            <v-list-item-action class="me-2 ms-0 my-0">
              <v-checkbox
                ripple="false"
                dense
                v-model="tempAdvancedFilter.fileType"
                class="ma-auto typeCheckbox"
                value="sheets" />
            </v-list-item-action>
            <v-list-item-content>
              <v-row class="ma-auto pa-0">
                <v-icon
                  :size="16"
                  :color="sheetIcon && sheetIcon.color">
                  {{ sheetIcon && sheetIcon.class }}
                </v-icon>
                <div class="px-2 my-auto pt-1 fileTypeLabel">{{ $t('documents.file.type.sheet') }}</div>
              </v-row>
            </v-list-item-content>        
          </v-list-item>
          <v-list-item class="ps-2">
            <v-list-item-action class="me-2 ms-0 my-0">
              <v-checkbox
                ripple="false"
                dense
                v-model="tempAdvancedFilter.fileType"
                class="ma-auto typeCheckbox"
                value="presentations" />
            </v-list-item-action>
            <v-list-item-content>
              <v-row class="ma-auto pa-0">
                <v-icon
                  :size="16"
                  :color="prezIcon && prezIcon.color">
                  {{ prezIcon && prezIcon.class }}
                </v-icon>
                <div class="px-2 my-auto pt-1 fileTypeLabel">{{ $t('documents.file.type.presentation') }}</div>
              </v-row>
            </v-list-item-content>        
          </v-list-item>
          <v-list-item class="ps-2">
            <v-list-item-action class="me-2 ms-0 my-0">
              <v-checkbox
                ripple="false"
                dense
                v-model="tempAdvancedFilter.fileType"
                class="ma-auto typeCheckbox"
                value="pdfs" />
            </v-list-item-action>
            <v-list-item-content>
              <v-row class="ma-auto pa-0">
                <v-icon
                  :size="16"
                  :color="pdfIcon && pdfIcon.color">
                  {{ pdfIcon && pdfIcon.class }}
                </v-icon>
                <div class="px-2 my-auto pt-1 fileTypeLabel">{{ $t('documents.file.type.pdf') }}</div>
              </v-row>
            </v-list-item-content>        
          </v-list-item>
          <v-list-item class="ps-2">
            <v-list-item-action class="me-2 ms-0 my-0">
              <v-checkbox
                ripple="false"
                dense
                v-model="tempAdvancedFilter.fileType"
                class="ma-auto typeCheckbox"
                value="images" />
            </v-list-item-action>
            <v-list-item-content>
              <v-row class="ma-auto pa-0">
                <v-icon
                  :size="16"
                  :color="imageIcon && imageIcon.color">
                  {{ imageIcon && imageIcon.class }}
                </v-icon>
                <div class="px-2 my-auto pt-1 fileTypeLabel">{{ $t('documents.file.type.image') }}</div>
              </v-row>
            </v-list-item-content>        
          </v-list-item>
          <v-list-item class="ps-2">
            <v-list-item-action class="me-2 ms-0 my-0">
              <v-checkbox
                ripple="false"
                dense
                v-model="tempAdvancedFilter.fileType"
                class="ma-auto typeCheckbox"
                value="videos" />
            </v-list-item-action>
            <v-list-item-content>
              <v-row class="ma-auto pa-0">
                <v-icon
                  :size="16"
                  :color="videoIcon && videoIcon.color">
                  {{ videoIcon && videoIcon.class }}
                </v-icon>
                <div class="px-2 my-auto pt-1 fileTypeLabel">{{ $t('documents.file.type.video') }}</div>
              </v-row>
            </v-list-item-content>        
          </v-list-item>
        </v-list>

        <div class="text-header  px-2 py-2">{{ $t('documents.advanced.filter.drawer.update.date') }}</div>

        <select-period
          v-model="tempAdvancedFilter.selectedPeriod"
          :labels="{
            from: $t('documents.label.from'),
            to: $t('documents.label.to'),
            today: $t('documents.label.today'),
            thisWeek: $t('documents.label.thisWeek'),
            thisMonth: $t('documents.label.thisMonth'),
            thisQuarter: $t('documents.label.thisQuarter'),
            thisSemester: $t('documents.label.thisSemester'),
            thisYear: $t('documents.label.thisYear'),
          }"
          :placeholder="$t('documents.advanced.filter.drawer.range.placeholder')"
          default-period=""
          hide-time />

        <div class="text-header px-2 pb-1 pt-7">{{ $t('documents.advanced.filter.drawer.file.size') }}</div>
         
        <div class="d-flex px-3">
          <div class="text-header pt-4 pe-2">{{ $t('documents.advanced.filter.drawer.min') }}</div>
          <v-text-field
            v-model="tempAdvancedFilter.minSize"
            class="py-2"
            :suffix="$t('documents.label.mega')"
            outlined
            dense
            type="number"
            min="0" />
          <v-spacer />
          <div class="text-header pt-4 pe-2 ps-8">{{ $t('documents.advanced.filter.drawer.max') }}</div>
          <v-text-field
            v-model="tempAdvancedFilter.maxSize"
            class="py-2"
            :suffix="$t('documents.label.mega')"
            outlined
            dense
            type="number"
            min="0" />
        </div>

        <div class="text-header  px-2 pt-3 pb-0">{{ $t('documents.advanced.filter.drawer.advanced.options') }}</div>

        <v-list>
          <v-list-item class="ps-2">
            <v-list-item-action class="me-2 ms-0 my-0">
              <v-checkbox
                ripple="false"
                dense
                v-model="tempAdvancedFilter.showHidden"
                class="ma-auto typeCheckbox" />
            </v-list-item-action>
            <v-list-item-content>
              <v-row class="ma-auto pa-0">
                <div class="my-auto px-0">{{ $t('documents.advanced.filter.drawer.show.hidden') }}</div>
              </v-row>
            </v-list-item-content>        
          </v-list-item>
        </v-list>
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
    {
      advancedFilter: {
        fileType: [],
        selectedPeriod: null,
        minSize: null,
        maxSize: null,
        showHidden: false,
      },
      docIcon: {},
      prezIcon: {},
      pdfIcon: {},
      videoIcon: {},
      imageIcon: {},
      sheetIcon: {},
      tempAdvancedFilter: {
        fileType: [],
        selectedPeriod: null,
        minSize: null,
        maxSize: null,
        showHidden: false,
      },
    }
  ),


  created() {
    this.$root.$on('open-advanced-filter-drawer', this.open);
    this.$root.$on('close-advanced-filter-drawer', this.close);
    this.getTypeIcons();
  },
  methods: {
    open() {
      this.$refs.DocumentAdvancedFilterDrawer.open();
      this.tempAdvancedFilter = JSON.parse(JSON.stringify(this.advancedFilter));
    },
    close() {
      this.tempAdvancedFilter.fileType= [];
      this.tempAdvancedFilter.selectedPeriod= null;
      this.tempAdvancedFilter.minSize= null;
      this.tempAdvancedFilter.maxSize= null;
      this.$refs.DocumentAdvancedFilterDrawer.close();
    },
    confirm() {
      this.advancedFilter = JSON.parse(JSON.stringify(this.tempAdvancedFilter));
      this.$root.$emit('set-advanced-filter', this.advancedFilter);
      this.close();
    },
    init() {
      this.advancedFilter.fileType= [];
      this.advancedFilter.selectedPeriod= null;
      this.advancedFilter.minSize= null;
      this.advancedFilter.maxSize= null;
      this.tempAdvancedFilter = JSON.parse(JSON.stringify(this.advancedFilter));
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
