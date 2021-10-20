<template>
  <v-data-table
    ref="dataTable"
    :headers="headers"
    :items="items"
    :items-per-page="pageSize"
    :loading="loading"
    :options.sync="options"
    :locale="lang"
    :single-select="false"
    show-select
    hide-default-footer
    disable-pagination
    disable-filtering
    class="documents-table border-box-sizing">
    <template
      v-for="header in extendedCells"
      v-slot:[`item.${header.value}`]="{item}">
      <documents-table-cell
        :key="header.value"
        :extension="header.cellExtension"
        :file="item" />
    </template>
    <template v-if="hasMore" slot="footer">
      <v-flex class="d-flex py-2 border-box-sizing">
        <v-btn
          :loading="loading"
          :disabled="loading"
          class="btn mx-auto"
          @click="limit += pageSize">
          {{ $t('documents.loadMore') }}
        </v-btn>
      </v-flex>
    </template>
  </v-data-table>
</template>

<script>
export default {
  props: {
    limit: {
      type: Number,
      default: 20
    },
    pageSize: {
      type: Number,
      default: 20
    },
    files: {
      type: Array,
      default: () => [
        {
          id: '11122',
          name: 'Reporting  Projet - Test - Test - 22/09/2021.xlsx',
          description: 'Reporting  Projet - Test - Test - 22/09/2021',
          datasource: 'jcr',
          driveId: 'driveId',
          forlderId: 'forlderId',
          parentFileId: 'parentFileId',
          ownerIdentity: Vue.prototype.$currentUserIdentity,
          creatorIdentity: Vue.prototype.$currentUserIdentity,
          acl: {
            canEdit: true,
            canAccess: true,
            canShare: true,
            canDelete: true,
          },
          createdDate: Date.now(),
          modifiedDate: Date.now(),
          size: 1024 * 1024 + 512,
          mimeType: 'image/png',
          versions: {
            number: 0,
            versions: [],
          },
          auditTrails: {
            offset: 0,
            limit: 1,
            size: 0,
            trails: [
              {
                id: 112,
                actionType: 'shared',
                userIdentity: Vue.prototype.$currentUserIdentity,
                targetIdentity: Vue.prototype.$currentUserIdentity,
                date: Date.now(),
                properties: {
                  
                }
              },
            ],
          },
          metadatas: {
            favorites: [
              {
                'name': '1',
                'properties': null,
                'id': 140,
                'objectType': 'file',
                'audienceId': 1,
                'creatorId': 1,
                'parentObjectId': '',
                'objectId': '11122'
              }
            ]
          },
        }
      ],
    },
  },
  data: () => ({
    lang: eXo.env.portal.language,
    options: {},
    loading: false,
    sortBy: 0,
    sortDirection: 'desc',
    headerExtensionApp: 'Documents',
    headerExtensionType: 'timelineViewHeader',
    headerExtensions: {},
  }),
  computed: {
    extendedCells() {
      return this.headers && this.headers.filter(header => header.cellExtension.componentOptions);
    },
    items() {
      return this.files && this.files.slice() || [];
    },
    hasMore() {
      return (this.loading && this.limit > this.pageSize) || this.limit === this.items.length;
    },
    headers() {
      const sortedHeaderExtensions = Object.values(this.headerExtensions).sort((ext1, ext2) => ext1.rank - ext2.rank);
      const headers = [];
      sortedHeaderExtensions.forEach(headerExtension => {
        headers.push({
          text: headerExtension.labelKey && this.$t(headerExtension.labelKey) || '',
          align: headerExtension.align || 'center',
          sortable: headerExtension.sortable || false,
          value: headerExtension.id,
          class: headerExtension.cssClass || '',
          width: headerExtension.width || 'auto',
          cellExtension: headerExtension,
        });
      });
      return headers;
    },
  },
  watch: {
    limit() {
      this.refresh();
    },
  },
  created() {
    document.addEventListener(`extension-${this.headerExtensionApp}-${this.headerExtensionType}-updated`, this.refreshHeaderExtensions);
    this.refreshHeaderExtensions();
  },
  methods: {
    refresh() {
      // TODO
    },
    refreshHeaderExtensions() {
      const extensions = extensionRegistry.loadExtensions(this.headerExtensionApp, this.headerExtensionType);
      let changed = false;
      extensions.forEach(extension => {
        if (extension.id && (!this.headerExtensions[extension.id] || this.headerExtensions[extension.id] !== extension)) {
          this.headerExtensions[extension.id] = extension;
          changed = true;
        }
      });
      // force update of attribute to re-render switch new extension id
      if (changed) {
        this.headerExtensions = Object.assign({}, this.headerExtensions);
      }
    },
  },
};
</script>