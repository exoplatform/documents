<template>
  <v-tooltip bottom>
    <template #activator="{ on, attrs }">
      <v-btn
        class="ms-2 visibility-btn"
        icon
        @click="changeVisibility">
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
    <span class="center">{{ icon.title }}</span>
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
    }
  },
  data: () => ({
    unit: 'bytes',
  }),
  computed: {
    icon() {
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
  },
  methods: {
    changeVisibility() {
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
