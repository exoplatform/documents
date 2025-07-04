/*
 * Copyright (C) 2025 eXo Platform SAS.
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
 * along with this program; if not, write to the Free Software Foundation,
 */


<template>
  <v-tooltip :disabled="showPast" bottom>
    <template #activator="{on, attrs}">
      <div
        class="my-10px mx-2"
        :class="showPast?'clickable':'non-clickable'"
        v-bind="attrs"
        v-on="on"
        @click="past()">
        <v-icon
          :class="!showPast?'text-disabled-color':''"
          size="16"
          class="pe-1">
          fas fa-paste
        </v-icon>
        <span class="ps-1 text-body" :class="showPast?'menu-text-color':'text-disabled-color'">{{ $t('documents.label.paste') }}</span>
      </div>
    </template>
    <span>{{ noPastMassage }}</span> 
  </v-tooltip>
</template>
<script>
export default {
  props: {
    file: {
      type: Object,
      default: null,
    }
  },
  data () {
    return {
      showPast: false,
      noPastMassage: '',
    };
  },

  created () {
    this.$root.$on('copied-element', this.checkCanPast());
    this.checkCanPast();
  },
  methods: {
    past() {
      const text = localStorage.getItem('documentCopiedItem');
      if (text) {
        const obj = JSON.parse(text);
        this.$root.$emit('past-document', obj.id,this.file.id);
      } 

    },
    checkCanPast() {
      const text = localStorage.getItem('documentCopiedItem');
      if (text) {
        const obj = JSON.parse(text);
        if (this.file.path.includes(obj.path)){
          this.noPastMassage = this.$t('documents.tooltip.paste.notAllowed');
          this.showPast = false;
        } else {
          this.showPast = true;
          this.noPastMassage = '';
        }
      } else {        
        this.showPast = false;
        this.noPastMassage = this.$t('documents.tooltip.paste.nothingtoPast');
      }
    },
  }
};
</script>