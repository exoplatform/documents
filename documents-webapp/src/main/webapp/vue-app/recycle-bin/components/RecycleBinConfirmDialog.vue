<!--

 Copyright (C) 2026 eXo Platform SAS.

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU Affero General Public License
 as published by the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 GNU Affero General Public License for more details.

 You should have received a copy of the GNU Affero General Public License
 along with this program. If not, see <gnu.org/licenses>.

-->
<template>
  <v-dialog
    v-model="dialog"
    content-class="uiPopup layout-drawer"
    width="400px"
    max-width="100vw">
    <v-card
      :id="dialogId"
      :aria-labelledby="`${dialogId}Title`"
      :aria-describedby="`${dialogId}Message`"
      class="elevation-12 transparent"
      role="alertdialog"
      aria-modal="true">
      <div class="ignore-vuetify-classes popupHeader ClearFix layout-drawer">
        <a
          :aria-label="$t('recycleBin.button.cancel')"
          class="uiIconClose pull-right"
          role="button"
          tabindex="0"
          @click="close"
          @keyup.enter="close"></a>
        <span :id="`${dialogId}Title`" class="ignore-vuetify-classes text-title">{{ title }}</span>
      </div>
      <v-card-text :id="`${dialogId}Message`">{{ message }}</v-card-text>
      <v-card-actions>
        <v-spacer />
        <button
          class="ignore-vuetify-classes btn me-2 recycle-bin-cancel"
          type="button"
          @click="close">
          {{ $t('recycleBin.button.cancel') }}
        </button>
        <button
          class="ignore-vuetify-classes btn btn-primary ms-2 recycle-bin-confirm"
          type="button"
          @click="confirm">
          {{ $t('recycleBin.button.confirm') }}
        </button>
        <v-spacer />
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>
<script>
export default {
  props: {
    dialogId: {
      type: String,
      required: true,
    },
    title: {
      type: String,
      default: null,
    },
    message: {
      type: String,
      default: null,
    },
  },
  data: () => ({
    dialog: false,
    // The element that had the focus when the dialog opened
    opener: null,
  }),
  watch: {
    dialog() {
      // Tells the drawers under it that a modal is displayed, as the shared dialog does.
      document.dispatchEvent(new CustomEvent(this.dialog && 'modalOpened' || 'modalClosed'));
      if (!this.dialog) {
        this.giveFocusBack();
      }
    },
  },
  methods: {
    open() {
      this.opener = document.activeElement;
      this.dialog = true;
    },
    // The dialog holds the focus while it is open and does not give it back:
    // it returns to where it was, once the dialog has let it go.
    giveFocusBack() {
      const opener = this.opener;
      this.opener = null;
      this.$nextTick(() => window.setTimeout(() => {
        if (opener?.isConnected && opener.focus) {
          opener.focus();
        }
      }));
    },
    close() {
      this.dialog = false;
      this.$emit('closed');
    },
    confirm() {
      this.dialog = false;
      this.$emit('confirm');
    },
  },
};
</script>
