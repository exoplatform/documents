<template>
  <exo-drawer 
    ref="documentInfoDrawer"
    class="documentInfoDrawer"
    @closed="close"
    right>
    <template slot="title">
      {{ $t('documents.drawer.details.title') }}
    </template>
    <template slot="content">
      <v-list-item>
        <v-list-item-content class="ma-1">
          <a class="fileName text-center not-clickable d-flex align-center">
            <v-spacer />
            <v-icon :color="iconColor">{{ iconClass }}</v-icon>
            <span
              class="text-truncate font-weight-bold text-color ms-2 px-2">
              {{ file.name }}
            </span>
            <documents-favorite-action :file="file" />
            <v-spacer />
          </a>
        </v-list-item-content>
      </v-list-item>

      <v-divider dark />

      <template>
        <v-list-item>
          <v-list-item-content class="mt-4 ma-1">
            <v-list-item-title>
              <a
                class="fileDetails text-color d-flex">
                <span class="text-center not-clickable font-weight-bold">{{ $t('documents.drawer.details.modified') }}:</span>
                <date-format
                  :value="lastUpdated"
                  :format="fullDateFormat"
                  class="document-date not-clickable text-no-wrap mx-1" />
                {{ $t('documents.drawer.details.by') }}
                <p class="text-decoration-underline text-truncate font-weight-bold mx-1" v-sanitized-html="profileLinkUpdated"></p>
              </a>
            </v-list-item-title>
          </v-list-item-content>
        </v-list-item>

        <v-list-item>
          <v-list-item-content class="ma-1">
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
                <p class="text-decoration-underline text-truncate font-weight-bold mx-1" v-sanitized-html="profileLinkCreated"></p>
              </a>
            </v-list-item-title>
          </v-list-item-content>
        </v-list-item>

        <v-list-item>
          <v-list-item-content class="ma-1">
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
  props: {
    file: {
      type: Object,
      default: null,
    },
    icon: {
      type: Array,
      default: null,
    },
    fileName: {
      type: String,
      default: '',
    },
    fileType: {
      type: String,
      default: '',
    },
    isMobile: {
      type: Boolean,
      default: false,
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
    currentUser: eXo.env.portal.userName,
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
    urlUpdated() {
      return this.file && this.file.modifierIdentity.remoteId ? `${eXo.env.portal.context}/${eXo.env.portal.portalName}/profile/${this.file.modifierIdentity.remoteId}` : '#';
    },
    urlCreated() {
      return this.file && this.file.creatorIdentity.remoteId ? `${eXo.env.portal.context}/${eXo.env.portal.portalName}/profile/${this.file.creatorIdentity.remoteId}` : '#';
    },
    profileLinkUpdated() {
      return `<a href="${this.urlUpdated}"><strong>${this.identityModifier}</strong></a>`;
    },
    profileLinkCreated() {
      return `<a href="${this.urlCreated}"><strong>${this.identityCreated}</strong></a>`;
    },
    identityModifier(){
      return this.currentUser === this.file.modifierIdentity.remoteId ? this.$t('documents.drawer.details.me') : this.file.modifierIdentity.name;
    },
    identityCreated(){
      return this.currentUser === this.file.creatorIdentity.remoteId ? this.$t('documents.drawer.details.me') : this.file.creatorIdentity.name;

    },

  },
  methods: {
    open() {
      this.$refs.documentInfoDrawer.open();
    },
    close() {
      this.$root.$emit('close-info-drawer',this.file.id);
      this.$refs.documentInfoDrawer.close();
    },
  }
};
</script>
