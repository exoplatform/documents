<template>
  <div class="attachmentsSelectFromDrive">
    <!-- EMPTY state: secondary tile below the hero. -->
    <div
      v-if="!hasAttachments"
      class="attachmentsSecondaryTile d-flex flex-column align-center justify-center clickable"
      role="button"
      tabindex="0"
      :aria-label="$t('attachments.drawer.selectFromDocuments')"
      @click="openSelectFromDrivesDrawer"
      @keydown.enter.prevent="openSelectFromDrivesDrawer"
      @keydown.space.prevent="openSelectFromDrivesDrawer">
      <v-icon size="28" color="primary">fa-folder-open</v-icon>
      <span class="text-sub-title mt-2 text-center">{{ $t('attachments.drawer.selectFromDocuments') }}</span>
    </div>
    <!-- POPULATED state: compact "add" button. -->
    <v-btn
      v-else
      outlined
      class="attachmentsAddBarBtn"
      :aria-label="$t('attachments.drawer.selectFromDocuments')"
      @click="openSelectFromDrivesDrawer">
      <v-icon size="18" class="me-1">fa-folder-open</v-icon>
      <span>{{ $t('attachments.drawer.documents') }}</span>
    </v-btn>
  </div>
</template>

<script>
export default {
  props: {
    hasAttachments: {
      type: Boolean,
      default: false
    }
  },
  methods: {
    openSelectFromDrivesDrawer() {
      this.$root.$emit('open-select-from-drives');
    },
  }
};
</script>
