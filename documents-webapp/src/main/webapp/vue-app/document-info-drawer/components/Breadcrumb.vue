<template>
  <div v-if="documentsBreadcrumbToDisplay.length">
    <div class="d-flex align-center">
      <div
        id="breadcrumb-list-items"
        data-isfolder="true"
        class="pa-1 d-flex width-fit-content">
        <div
          v-for="(folder, index) in documentsBreadcrumbToDisplay"
          :key="index"
          :data-fileId="folder.id"
          class="d-flex text-truncate">
          <v-tooltip max-width="300" bottom>
            <template #activator="{ on, attrs }">
              <v-btn
                height="20px"
                min-width="25px"
                class="pa-0 flex-shrink-1 text-truncate clickable"
                text
                v-bind="attrs"
                v-on="on"
                @click="openFolder(folder)">
                <v-icon
                  v-if="folder.ellipsis"
                  size="16"
                  class="pe-1 not-clickable">
                  fas fa-ellipsis-h
                </v-icon>
                <a
                  class="text-truncate clickable font-weight-bold"
                  v-else-if="index>0"
                  >
                  {{ folder.name }}
                </a>
                <v-icon
                  v-else
                  size="16"
                  class="pe-1">
                  fas fa-home
                </v-icon>
                <v-icon
                  v-if="folder.symlink"
                  size="10"
                  class="pe-1">
                  mdi-link-variant
                </v-icon>
                
              </v-btn>
            </template>
            <span class="caption">
              {{ getName(folder) }}
              <v-icon
                v-if="folder.symlink"
                size="10"
                class="pe-1">
                mdi-link-variant
              </v-icon>
            </span>
          </v-tooltip>
          <v-icon
            v-if="index < documentsBreadcrumbToDisplay.length-1"
            size="12"
            class="px-3">
            fa-chevron-right
          </v-icon>
        </div>
      </div>
    </div>
  </div>
</template>
<script>

export default {
  props: {
    folderId: {
      type: String,
      default: '',
    },
    isMobile: {
      type: Boolean,
      default: false
    }
  }, 

  data: () => ({
    documentsBreadcrumb: [],
    documentsBreadcrumbToDisplay: [],
    folderPath: '',
    ownerId: eXo.env.portal.spaceIdentityId || eXo.env.portal.userIdentityId
  }),
  created() {
    this.$root.$on('update-breadcrumb',fileId => {
      this.getBreadCrumbs(fileId);
    }); 
    this.getBreadCrumbs(this.folderId);
  },
  methods: {
    openFolder(folder) {
      if (folder.ellipsis) {
        return;
      }
      this.$root.$emit('open-folder', folder);
    },
    getBreadCrumbs(fileId) {
      return this.$documentFileService
        .getBreadCrumbs(fileId,this.ownerId)
        .then(breadCrumbs => {
          this.documentsBreadcrumb = breadCrumbs;
          this.documentsBreadcrumbToDisplay = this.getDocumentsBreadcrumbToDisplay();
        })
        .finally(() => this.loading = false);
    },
    getDocumentsBreadcrumbToDisplay() {
      if (!this.documentsBreadcrumb || this.documentsBreadcrumb.length <= 2) {
        return this.documentsBreadcrumb || [];
      } else {
        const length = this.documentsBreadcrumb.length;
        const documentsBreadcrumbToDisplay = [this.documentsBreadcrumb[0], ... this.documentsBreadcrumb.slice(length - 2, length)];
        documentsBreadcrumbToDisplay[1] = Object.assign({}, documentsBreadcrumbToDisplay[1], {
          name: '...',
          ellipsis: true,
        });
        return documentsBreadcrumbToDisplay;
      }
    },
    getName(folder){
      if (folder.ellipsis){
        return `[${this.documentsBreadcrumb.slice(1, this.documentsBreadcrumb.length - 1).map(item => item.name).join(' , ')}]`;
      }
      if (folder.name==='Private'){
        return `${this.$t('documents.label.access')} ${this.$t('documents.label.userHomeDocuments')}`;
      } else if (folder.name==='Documents'){
        return `${this.$t('documents.label.access')} ${this.$t('documents.label.spaceHomeDocuments')}`;
      }
      return `${this.$t('documents.label.access')} ${folder.name}`;
    },
  }
};
</script>
