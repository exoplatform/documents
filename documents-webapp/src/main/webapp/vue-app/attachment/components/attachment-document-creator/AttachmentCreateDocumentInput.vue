<template>
  <div class="attachmentsCreateDocument">
    <!-- Both states are plain buttons that open the level-2 "Add a document"
         drawer (relayed by AttachmentsDrawer to the nested drawer's open()).
         Empty state = a secondary tile; populated state = a compact button. -->
    <div
      v-if="!hasAttachments"
      class="attachmentsSecondaryTile d-flex flex-column align-center justify-center clickable"
      role="button"
      tabindex="0"
      :aria-label="$t('attachments.drawer.createDocument')"
      @click="openCreateDocumentDrawer()"
      @keyup.enter="openCreateDocumentDrawer()">
      <v-icon size="28" color="primary">far fa-file-word</v-icon>
      <span class="text-sub-title mt-2 text-center">{{ $t('attachments.drawer.createDocument') }}</span>
    </div>
    <v-btn
      v-else
      outlined
      class="attachmentsAddBarBtn"
      :aria-label="$t('attachments.drawer.createDocument')"
      @click="openCreateDocumentDrawer()">
      <v-icon size="18" class="me-1">far fa-file-word</v-icon>
      <span>{{ $t('attachments.new.document') }}</span>
    </v-btn>
  </div>
</template>

<script>
export default {
  props: {
    hasAttachments: {
      type: Boolean,
      default: false
    },
  },
  methods: {
    openCreateDocumentDrawer() {
      this.$root.$emit('open-create-document-drawer');
    },
  }
};
</script>
