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
  <v-app class="border-box-sizing" flat>
    <v-main class="white pa-4">
      <div class="d-flex align-center">
        <a :href="documentsBaseLink" class="body-1 text-uppercase text-sub-title">
          {{ $t('document.size.header.label') }}
        </a>
        <div v-if="inProgress">
          <v-progress-circular
            class="ms-3 me-1"
            :size="18"
            color="primary"
            indeterminate />
          {{ $t('document.size.computing.progress.label') }}
        </div>
      </div>
      <div v-if="initialized" class="d-flex flex-wrap">
        <v-tooltip :disabled="isMobile" top> 
          <template #activator="{ on, attrs }">
            <a
              :href="documentsBaseLink" 
              class="d-flex flex-wrap flex-column justify-space-around align-center col-6 pa-0 "
              v-bind="attrs"
              v-on="on">
              <div class="width-max-content font-weight-bold headline  ma-2 black--text v-slider--horizontal">
                <v-icon
                  size="28"
                  color="primary"
                  class="pe-1">
                  fas fa-folder-open
                </v-icon>
                {{ toSize.value }} {{ $t('document.size.label.unit.'+toSize.unit) }}
              </div>
              <div class="text-sub-title px-1">
                {{ documentsLocationMessage }}
              </div>
            </a>
          </template>
          <date-format
            :value="sizeData.toSizeDate"
            :format="dateFormat"
            class="ms-1" />
        </v-tooltip>
          
       
        <div class="d-flex flex-wrap flex-column justify-space-around align-center pa-0 col-6">
          <div class="width-max-content font-weight-bold subtitle-2 ma-2 pt-2 v-slider--horizontal">
            <v-icon
              size="20"
              :color="diffSize.value > 0 ? 'red':'green'"
              class="pe-1">
              fas fa-chart-line
            </v-icon>
            <span :class="diffSize.value > 0 ? 'red--text' : 'green--text'">{{ diffSize.value }} {{ $t('document.size.label.unit.'+diffSize.unit) }}</span> 
          </div>
          <div class="text-sub-title px-1">
            {{ documentsdaysMessage }}
          </div>
        </div>
      </div>
    </v-main> 
  </v-app>
</template>

<script>


export default {
  data: () => ({
    sizeData: {},
    inProgress: false,
    initialized: false,
    dateFormat: {   dateStyle: 'long',  timeStyle: 'short' }
  }),
  created() {
    this.getData();
  },

  computed: {
    documentsLocationMessage() {
      if (eXo.env.portal.spaceIdentityId){
        return this.$t('document.size.label.stored.space');
      } else {
        return this.$t('document.size.label.stored.drive');
      }
    },
    documentsdaysMessage() {
      return this.$t('document.size.label.period').replace('{0}', this.sizeData.diffDays);
    },
    diffSize(){
      const size = this.$documentsUtils.getSize(this.sizeData.toSize-this.sizeData.fromSize);
      if (size.value > 0){
        size.value = `+${size.value}`;
      }
      return {
        value: size.value,
        unit: size.unit
      };
    },
    toSize(){
      const size = this.$documentsUtils.getSize(this.sizeData.toSize);
      return {
        value: size.value,
        unit: size.unit
      };
    },
    documentsBaseLink (){
      if (eXo.env.portal.spaceIdentityId){
        return `${eXo.env.portal.context}/g/:spaces:${eXo.env.portal.spaceGroup}/${eXo.env.portal.spaceName}/documents`;
      } else {
        return `${eXo.env.portal.context}/${eXo.env.portal.portalName}/drives`;
      }
    }
  },

  methods: {
    updateSize() {
      this.inProgress=true;
      return this.$documentSizeService.updateSize()
        .then(data => {
          if (data) {
            this.sizeData=data;
            this.inProgress=false;
          }
        });
    },
    getData() {
      return this.$documentSizeService.getData()
        .then(data => {
          if (data) {
            this.sizeData=data;
            this.initialized=true;
            if (!data.todaySize){
              this.updateSize();
            }
          }
        });
    },
  },

};
</script>