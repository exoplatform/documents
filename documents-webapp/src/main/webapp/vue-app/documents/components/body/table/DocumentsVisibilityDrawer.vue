<template>
  <exo-drawer 
    ref="documentVisibilityDrawer"
    class="documentVisibilityDrawer"
    @closed="close"
    right>
    <template slot="title">
      {{ visibilityTitle }}
    </template>
    <template slot="content">
      <v-list-item>
        <v-list-item-content class="my-1">
          <exo-user-avatar
            :identity="ownerIdentity"
            avatar-class="me-2"
            size="42"
            bold-title>
            <template slot="subTitle">
              <span class="caption font-italic">
                {{ $t('documents.label.owner') }}
              </span>
            </template>
          </exo-user-avatar>
        </v-list-item-content>
      </v-list-item>

      <v-divider dark class="mx-4" />
    </template>
  </exo-drawer>
</template>
<script>

export default {
  props: {
    file: {
      type: Object,
      default: null,
    },
    fileName: {
      type: String,
      default: '',
    },
  },
  data: () => ({
    ownerIdentity: [],
  }),
  computed: {
    visibilityTitle(){
      return this.$t('documents.label.visibilityTitle').replace('{0}', this.fileName);
    },
  },
  mounted(){
    this.$userService.getUser(this.file.creatorIdentity.remoteId).then(user => {
      this.ownerIdentity = user;
    });
  },
  methods: {
    open() {
      this.$refs.documentVisibilityDrawer.open();
    },
    close() {
      this.$refs.documentVisibilityDrawer.close();
    },
  }
};
</script>
