<!--

  Copyright (C) 2026 eXo Platform SAS.

  This program is free software; you can redistribute it and/or
  modify it under the terms of the GNU Affero General Public License
  as published by the Free Software Foundation; either version 3
  of the License, or (at your option) any later version.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with this program; if not, see<http://www.gnu.org/licenses/>.

-->
<template>
  <v-dialog
    v-model="dialog"
    persistent
    width="500px"
    max-width="100vw"
    content-class="uiPopup layout-drawer"
    @click:outside="cancel"
    @keydown.esc="cancel">
    <v-card
      id="DocumentsKeepInsideConfirmDialog"
      class="elevation-12 transparent">
      <div class="ignore-vuetify-classes popupHeader ClearFix layout-drawer">
        <a
          href="#"
          class="uiIconClose pull-right"
          :aria-label="$t('documents.spaceSettings.keepInside.confirm.cancel')"
          role="button"
          @click.prevent="cancel"></a>
        <span id="DocumentsKeepInsideTitle" class="ignore-vuetify-classes text-title">
          {{ $t('documents.spaceSettings.keepInside.confirm.title') }}
        </span>
      </div>
      <v-card-text v-if="loading" class="d-flex justify-center py-5">
        <v-progress-circular indeterminate color="primary" />
      </v-card-text>
      <v-card-text v-else-if="counts" class="text-color">
        <p
          v-if="publicLinks"
          id="DocumentsKeepInsidePublicLinks"
          class="mb-2">
          {{ publicLinksLine }}
          <a
            id="DocumentsKeepInsideSeeDocuments"
            href="#"
            class="primary--text"
            role="button"
            @click.prevent="$emit('see-documents', publicLinks)">
            {{ $t('documents.spaceSettings.keepInside.confirm.seeDocuments') }}
          </a>
        </p>
        <p
          v-if="sharedOutside"
          id="DocumentsKeepInsideSharedOutside"
          class="mb-2">
          {{ sharedOutsideLine }}
        </p>
        <p id="DocumentsKeepInsideRestrictions" class="mb-2">
          {{ $t('documents.spaceSettings.keepInside.confirm.restrictions') }}
        </p>
        <p
          v-if="publicLinks"
          id="DocumentsKeepInsideIrreversible"
          class="mb-0 font-weight-bold">
          {{ $t('documents.spaceSettings.keepInside.confirm.irreversible') }}
        </p>
      </v-card-text>
      <v-card-actions>
        <v-spacer />
        <button
          id="DocumentsKeepInsideConfirmButton"
          :disabled="loading || !counts"
          class="ignore-vuetify-classes btn btn-primary me-2"
          @click="confirm">
          {{ $t('documents.spaceSettings.keepInside.confirm.ok') }}
        </button>
        <button
          id="DocumentsKeepInsideCancelButton"
          class="ignore-vuetify-classes btn ms-2"
          @click="cancel">
          {{ $t('documents.spaceSettings.keepInside.confirm.cancel') }}
        </button>
        <v-spacer />
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>
<script>
// The markup, the classes and the modal events of social's exo-confirm-dialog,
// which renders its message as sanitized HTML with no slot: the 'See
// documents' link could not open the list from it
export default {
  props: {
    spaceId: {
      type: [String, Number],
      default: null,
    },
  },
  data: () => ({
    dialog: false,
    loading: false,
    counts: null,
  }),
  watch: {
    // as the shared confirm dialog does, for the page's other modal-aware apps
    dialog() {
      document.dispatchEvent(new CustomEvent(this.dialog ? 'modalOpened' : 'modalClosed'));
    },
  },
  computed: {
    publicLinks() {
      return this.counts?.publicLinks || 0;
    },
    sharedOutside() {
      return this.counts?.sharedOutside || 0;
    },
    publicLinksLine() {
      return this.publicLinks === 1
        ? this.$t('documents.spaceSettings.keepInside.confirm.onePublicLink')
        : this.$t('documents.spaceSettings.keepInside.confirm.publicLinks', {0: this.publicLinks});
    },
    // the documents still shared outside the space once the setting is on
    sharedOutsideLine() {
      return this.sharedOutside === 1
        ? this.$t('documents.spaceSettings.keepInside.confirm.oneSharedOutside')
        : this.$t('documents.spaceSettings.keepInside.confirm.sharedOutside', {0: this.sharedOutside});
    },
  },
  methods: {
    // reads the counts the confirmation shows; a failed read closes it with
    // 'error', so that nothing is applied without them
    open() {
      this.counts = null;
      this.loading = true;
      this.dialog = true;
      // not reactive: a later opening makes the answer of this one stale
      const opening = this.opening = (this.opening || 0) + 1;
      return this.$spaceDocumentsExposureService.getExposure(this.spaceId)
        .then(counts => {
          if (opening === this.opening && this.dialog) {
            this.counts = counts;
          }
        })
        .catch(error => {
          if (opening === this.opening && this.dialog) {
            this.dialog = false;
            this.$emit('error', error);
          }
        })
        .finally(() => {
          if (opening === this.opening) {
            this.loading = false;
          }
        });
    },
    confirm() {
      if (!this.counts) {
        return;
      }
      this.dialog = false;
      this.$emit('confirm');
    },
    cancel() {
      if (!this.dialog) {
        return;
      }
      this.dialog = false;
      this.$emit('cancel');
    },
    // hides the confirmation while the list of documents is open, keeping
    // its counts, so that no dialog covers the drawer
    suspend() {
      this.dialog = false;
    },
    resume() {
      if (this.counts) {
        this.dialog = true;
      }
    },
  },
};
</script>
