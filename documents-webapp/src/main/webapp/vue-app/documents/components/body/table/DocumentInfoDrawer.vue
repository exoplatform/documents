<template>
  <exo-drawer 
    ref="documentInfoDrawer"
    class="documentInfoDrawer"
    @closed="close"
    right>
    <template slot="title">
      {{ $t('documents.drawer.details.title') }}
    </template>
    <template v-if="file" slot="content">
      <v-list-item>
        <v-list-item-content class="ma-1">
          <a class="text-center not-clickable d-flex align-center">
            <v-spacer />
            <v-icon :color="iconColor">{{ iconClass }}</v-icon>
            <span
              class="fileName font-weight-bold text-color ms-2 px-2">
              {{ file.name }}
            </span>
            <documents-favorite-action v-if="!file.folder" :file="file" />
            <v-spacer />
          </a>
        </v-list-item-content>
      </v-list-item>

      <v-divider dark />

      <template>
        <v-list-item>
          <v-list-item-content class="mt-4 mx-4">
            <v-list-item-title>
              <a
                class="fileDetails text-color d-flex">
                <span class="text-center not-clickable font-weight-bold">{{ $t('documents.drawer.details.modified') }}:</span>
                <date-format
                  :value="lastUpdated"
                  :format="fullDateFormat"
                  class="document-date not-clickable text-no-wrap mx-1" />
                {{ $t('documents.drawer.details.by') }}
                <exo-user-avatar
                  v-if="identityModifier && !isCurrentUserModifier"
                  :identity="identityModifier"
                  avatar-class="me-2"
                  size="42"
                  fullname
                  popover
                  bold-title
                  link-style
                  class="text-decoration-underline text-truncate font-weight-bold mx-1"
                  username-class />
                <p v-else class="text-decoration-underline primary--text not-clickable font-weight-bold mx-1">
                  {{ infoDrawerModifierLabel }}
                </p>
              </a>
            </v-list-item-title>
          </v-list-item-content>
        </v-list-item>

        <v-list-item>
          <v-list-item-content class="mx-4">
            <v-list-item-title>
              <a
                class="fileDetails text-color d-flex ">
                <span class="text-center not-clickable font-weight-bold">
                  {{ $t('documents.drawer.details.created') }}:</span>
                <date-format
                  :value="fileCreated"
                  :format="fullDateFormat"
                  class="document-date not-clickable text-no-wrap mx-1" />
                {{ $t('documents.drawer.details.by') }}

                <exo-user-avatar
                  v-if="identityCreated && !isCurrentUserCreator"
                  :identity="identityCreated"
                  avatar-class="me-2"
                  size="42"
                  fullname
                  popover
                  bold-title
                  link-style
                  extra-class="text-decoration-underline"
                  class="text-decoration-underline text-truncate font-weight-bold mx-1"
                  username-class />
                <p v-else class="text-decoration-underline not-clickable primary--text font-weight-bold mx-1">
                  {{ infoDrawerCreatorLabel }}
                </p>
              </a>
            </v-list-item-title>
          </v-list-item-content>
        </v-list-item>

        <v-list-item>
          <v-list-item-content class="mx-4">
            <v-list-item-title>
              <a
                class="fileDetails not-clickable text-color d-flex">
                <span class="text-center font-weight-bold">{{ $t('documents.drawer.details.size') }}:</span>
                <documents-file-size-cell class="mx-1 text-color" :file="file" />
              </a>
            </v-list-item-title>
          </v-list-item-content>
        </v-list-item>
      </template>
    </template>
  </exo-drawer>
</template>
<script>

export default {
  data: () => ({
    fullDateFormat: {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    },
    currentUser: eXo.env.portal.userName,
    file: null,
    fileName: null,
    fileType: null,
    icon: null,
  }),
  computed: {
    iconColor(){
      return this.icon && this.icon.color;
    },
    iconClass(){
      return this.icon && this.icon.class;
    },
    lastUpdated() {
      return this.file && (this.file.modifiedDate || this.file.createdDate) || '';
    },
    fileCreated() {
      return this.file && this.file.createdDate || '';
    },
    infoDrawerCreatorLabel() {
      return this.currentUser === this.file?.creatorIdentity?.remoteId ?
        this.$t('documents.drawer.details.me') :
        this.$t('documents.drawer.details.system');
    },
    infoDrawerModifierLabel() {
      return this.currentUser === this.file?.modifierIdentity?.remoteId ?
        this.$t('documents.drawer.details.me') :
        this.$t('documents.drawer.details.system');
    },
    isCurrentUserModifier() {
      return this.currentUser === this.file?.modifierIdentity?.remoteId;
    },
    isCurrentUserCreator() {
      return this.currentUser === this.file?.creatorIdentity?.remoteId;
    },
    identityModifier(){
      return this.file?.modifierIdentity;
    },
    identityCreated(){
      return this.file?.creatorIdentity;
    },
    isMobile() {
      return this.$vuetify.breakpoint.name === 'xs' || this.$vuetify.breakpoint.name === 'sm';
    },
  },
  created() {
    this.$root.$on('open-info-drawer', this.open);
    this.$root.$on('close-info-drawer', this.close);
  },
  methods: {
    open(file, fileName, fileType, icon) {
      this.file = file;
      this.fileName = fileName;
      this.fileType = fileType;
      this.icon = icon;
      this.$refs.documentInfoDrawer.open();
    },
    close() {
      this.$refs.documentInfoDrawer.close();
    },
  }
};
</script>
