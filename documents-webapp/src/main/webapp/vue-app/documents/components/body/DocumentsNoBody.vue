<template>
  <div class="documents-body">
    <div class="ma-4 d-flex flex-column justify-center text-center text-color">
      <v-img
        :src="emptyDocs"
        class="mx-auto mb-4 overflow-visible"
        overflow="visible"
        :max-height="!isMobile ? 55 : 46"
        :max-width="!isMobile ? 61 : 51"
        contain
        eager>
        <v-icon :size="!isMobile ? 32 : 23" class="closeIcon text-light-color white float-right">fas fa-times-circle</v-icon>
      </v-img>
      <div>
        <p v-if="!isMobile" class="text-light-color">
          <span v-sanitized-html="noContentLabel"></span>
        </p>
        <div v-if="isMobile" class="text-light-color">
          <p>
            {{ $t('documents.label.no-content').replace('{0}', '') }}
          </p>
          <p> <strong>{{ spaceDisplayName }}</strong></p>
        </div>

        <a @click="openDrawer" class="text-decoration-underline">{{ $t('documents.label.addNewFile') }}</a>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  props: {
    isMobile: {
      type: Boolean,
      default: false
    }
  },
  data: () => ({
    emptyDocs: '/documents-portlet/images/docs.png',
    spaceDisplayName: eXo.env.portal.spaceDisplayName,
  }),
  computed: {
    noContentLabel() {
      return this.$t && this.$t('documents.label.no-content', {
        '0': `<strong>${this.spaceDisplayName}</strong>`,
      });
    },
  },
  methods: {
    openDrawer() {
      document.dispatchEvent(new CustomEvent('open-attachments-app-drawer'));
    },
  }
};
</script>