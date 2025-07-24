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
  <div>
    <div
      class="clickable py-10px px-4"
      @click="hide(!file.hidden)">
      <v-icon
        v-if="file.hidden"
        size="16"
        class="pe-1">
        fas fa-eye-slash
      </v-icon>
      <v-icon
        v-else
        size="16"
        class="pe-1">
        fas fa-eye
      </v-icon>
      <span v-if="file.hidden" class="ps-1 text-body menu-text-color">{{ $t('documents.label.unhide') }}</span>
      <span v-else class="ps-1 text-body menu-text-color">{{ $t('documents.label.hide') }}</span>
    </div>
  </div>  
</template>
<script>
export default {
  props: {
    file: {
      type: Object,
      default: null,
    }
  },

  methods: {
    hide(hidden) {
      this.file.hidden = hidden;
      return this.$documentFileService.updateVisibility(this.$root.ownerId,this.file)
        .then(() => {
          this.$root.$emit('hide-element',  this.file);
          const message = this.file.hidden ? this.$t('documents.alert.success.document.hidden') : this.$t('documents.alert.success.document.unhidden');
          if (this.isMobile){
            this.displayAlert(message);
          } else {
            this.$root.$emit('alert-message',  message, 'success');
          }
        }).catch(() => {
          const message = this.file.hidden ? this.$t('documents.alert.error.document.hidden') : this.$t('documents.alert.error.document.unhidden');
          this.$root.$emit('alert-message',  message, 'error');
        });
    }
  },
};
</script>