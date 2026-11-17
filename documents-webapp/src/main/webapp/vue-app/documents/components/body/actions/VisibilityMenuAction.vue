<template>
  <v-tooltip
    :disabled="canManageAccess"
    content-class="accessManagementLimitedTooltip"
    bottom>
    <template #activator="{ on, attrs }">
      <div
        v-bind="attrs"
        class="visibilityMenuAction"
        v-on="on">
        <document-action-item
          icon="fas fa-shield-alt"
          :label="$t('documents.label.visibility')"
          :disabled="!canManageAccess"
          :icon-extra-class="canManageAccess ? null : 'icon-disabled-color'"
          :label-extra-class="canManageAccess ? null : 'text-disabled-color'"
          @click="changeVisibility" />
      </div>
    </template>
    <span>{{ $t('documents.tooltip.accessManagementLimited') }}</span>
  </v-tooltip>
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
    }
  },
  data: () => ({
    viewTab: 'RECENT',
    spaceId: eXo.env.portal.spaceId,
  }),
  computed: {
    canManageAccess() {
      return this.file?.acl?.canManageAccess !== false;
    },
  },
  methods: {
    changeVisibility(){
      if (!this.canManageAccess) {
        return;
      }
      this.$root.$emit('open-visibility-drawer', this.file);
      this.getDocumentView();
      document.dispatchEvent(new CustomEvent('manage-access', {
        detail: {
          'category': this.file.folder ? 'Folder' : 'Document',
          'spaceId': this.spaceId,
          'view': this.viewTab
        }
      }));
      if (this.isMobile) {
        this.$root.$emit('close-file-action-menu');
      }
    },
    getDocumentView() {
      const currentUrlSearchParams = window.location.search;
      const queryParams = new URLSearchParams(currentUrlSearchParams);
      if (queryParams.has('view')) {
        const view = queryParams.get('view');
        this.viewTab = view.toLowerCase() === 'folder' ? 'Folder' : 'RECENT';
      }
    }
  }
};
</script>