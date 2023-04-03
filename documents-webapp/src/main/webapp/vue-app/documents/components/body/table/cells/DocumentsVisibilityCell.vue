<template>
  <v-tooltip bottom>
    <template #activator="{ on, attrs }">
      <v-btn
        class="visibility-btn"
        :class="btnClass"
        icon
        @click="changeVisibility"
        @contextmenu.stop.prevent="preventOpenContextMenu">
        <v-icon
          color="grey"
          dark
          v-bind="attrs"
          v-on="on"
          size="13"
          class="px-2 iconStyle">
          {{ icon.icon }}
        </v-icon>
      </v-btn>
    </template>
    <span class="center">{{ $shareDocumentSuspended ? shareDocumentSuspendedLabel : icon.title }}</span>
  </v-tooltip>
</template>

<script>
export default {
  props: {
    file: {
      type: Object,
      default: null,
    },
    selectedView: {
      type: String,
      default: null
    },
    isMobile: {
      type: Boolean,
      default: false
    },
  },
  data: () => ({
    unit: 'bytes',
    sharedDocumentSuspended: true,
  }),
  computed: {
    icon() {
      if (this.file.folder && this.file.id < 0) {
        return {
          icon: 'fas fa-layer-group',
          title: this.$t('documents.label.visibility.all'),
        };
      }
      if (this.isSharedWithCurrentSpace && !this.file.acl.canEdit) {
        const collaborators = this.file.acl.collaborators.filter(e => e.identity.id === eXo.env.portal.spaceIdentityId );
        return collaborators[0].permission === 'read'?
          {
            icon: 'fas fa-eye',
            title: this.$t('documents.label.visibility.specific.manger'),
          }
          :
          {
            icon: 'fas fa-layer-group',
            title: this.$t('documents.label.visibility.all'),
          }; 
      }
      switch (this.file.acl.visibilityChoice) {
      case 'SPECIFIC_COLLABORATOR':
        return {
          icon: 'fas fa-user-lock',
          title: this.$t('documents.label.visibility.specific.collaborator'),
        };
      case 'ALL_MEMBERS':
        return this.file.acl.allMembersCanEdit ?
          {
            icon: 'fas fa-layer-group',
            title: this.$t('documents.label.visibility.all'),
          }
          :
          {
            icon: 'fas fa-eye',
            title: this.$t('documents.label.visibility.specific.manger'),
          };
      default:
        return {
          icon: 'fas fa-layer-group',
          title: this.$t('documents.label.visibility.all'),
        };
      }
    },
    isSharedWithCurrentSpace(){
      const spaceIdentityId = eXo.env.portal.spaceIdentityId;
      const spaceName = eXo.env.portal.spaceName;
      const collaborators = this.file.acl.collaborators;
      if (spaceIdentityId && collaborators.length > 0){
        for (const collaborator of collaborators) {
          if (collaborator.identity.id === spaceIdentityId && collaborator.identity.remoteId === spaceName) {
            return true;
          }
        }
      }
      return false;
    },
    btnClass(){
      return this.isMobile && 'ms-2' || 'me-4' ;
    },
  },
  created() {
    this.$transferRulesService.getDocumentsTransferRules().then(rules => {
      this.sharedDocumentSuspended = rules.sharedDocumentStatus === 'true';
    });
  },
  methods: {
    preventOpenContextMenu() {
      this.$root.$emit('prevent-action-context-menu');
    },
    changeVisibility() {
      if (!this.file.acl.canEdit || this.$shareDocumentSuspended) {
        return;
      }
      this.$root.$emit('open-visibility-drawer', this.file);
      document.dispatchEvent(new CustomEvent('manage-access', {
        detail: {
          'category': this.file.folder ? 'Folder' : 'Document',
          'spaceId': eXo.env.portal.spaceId,
          'view': this.selectedView === 'timeline' ? 'recentView': 'folderView',
        }
      }));
    }
  }
};
</script>
