<template>
  <div
    v-if="!isMobile"
    class="align-center ms-1 d-inline-flex">
    <exo-user-avatar
      :profile-id="file.modifierIdentity.remoteId"
      :size="28"
      popover
      avatar
      class="me-1"
      popover-left-position
      align-top />
    <v-tooltip
      open-on-hover
      bottom>
      <template #activator="{ on, attrs }">
        <span
          v-bind="attrs"
          v-on="on"
          @click="showVersionHistory">
          <date-format
            :value="lastUpdated"
            :format="dateFormat"
            class="document-time text-light-color text-no-wrap" />
        </span>
      </template>
      <date-format
        :value="lastUpdated"
        :format="fullDateFormat" />
    </v-tooltip>
  </div>
</template>

<script>
export default {
  props: {
    file: {
      type: Object,
      default: null,
    },
    isMobile: {
      type: Boolean,
      default: false
    },
    extension: {
      type: Object,
      default: null,
    },
  },
  data: () => ({
    fullDateFormat: {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    },
    dateFormat: {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
    },
  }),
  computed: {
    lastUpdated() {
      return this.file && (this.file.modifiedDate || this.file.createdDate) || '';
    },
  },
  methods: {
    showVersionHistory() {
      this.$root.$emit('show-version-history', this.file);
    }
  }
};
</script>