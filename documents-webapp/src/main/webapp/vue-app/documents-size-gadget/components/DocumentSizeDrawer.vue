<template>
  <exo-drawer
    ref="drawer"
    v-model="drawer"
    right>
    <template slot="title">
     {{ $t('documents.label.largeDocumentHeader.title') }}
    </template>
    <template #content>
      <div class="ma-5">
        <span class="subtitle-1 mx-5">
          {{ $t('documents.label.largeDocumentHeader.header') }}
        </span>
      </div>

      <div v-for="document in documents" :key="document" class="ml-5 d-flex">
        <v-list-item-avatar
          :class="smallAttachmentIcon ? 'me-0' :'me-3'"
          class="border-radius">
          <span class="list-complete-item">
            <div
              :class="smallAttachmentIcon && 'smallAttachmentIcon'"
              class="fileType">
              <v-icon
                size="41"
                :color="getIcon(document).color">
                {{ getIcon(document).class }}
              </v-icon>
            </div>
          </span>
        </v-list-item-avatar>
        <v-list-item-content>
          <v-list-item-title class="uploadedFileTitle" :title="document.name">
            {{ document.name }}
          </v-list-item-title>
          <v-list-item-subtitle>
            {{ getSize(document).value }} {{ $t('document.size.label.unit.'+getSize(document).unit) }}
          </v-list-item-subtitle>
        </v-list-item-content>
      </div>
    </template>
  </exo-drawer>
</template>


<script>
export default {
  data: () => ({
    documents: [],
  }),
  created() {
    this.$root.$on('documents-size-drawer', this.open);
  },
  methods: {
    open() {
      this.$documentSizeService.getBiggestDocuments(0,10).then(data => {
        this.documents=data;
        this.$refs.drawer.open();
      });
    },
    close() {
      return this.$nextTick().then(() => this.$refs.drawer.close());
    },
    getIcon(document) {
      const type = document && document.mimeType || '';
      if (type.includes('pdf')) {
        return {
          class: 'fas fa-file-pdf',
          color: '#FF0000',
        };
      } else if (type.includes('presentation') || type.includes('powerpoint')) {
        return {
          class: 'fas fa-file-powerpoint',
          color: '#CB4B32',
        };
      } else if (type.includes('sheet') || type.includes('excel') || type.includes('csv')) {
        return {
          class: 'fas fa-file-excel',
          color: '#217345',
        };
      } else if (type.includes('word') || type.includes('opendocument') || type.includes('rtf')) {
        return {
          class: 'fas fa-file-word',
          color: '#2A5699',
        };
      } else if (type.includes('plain')) {
        return {
          class: 'fas fa-file-alt',
          color: '#385989',
        };
      } else if (type.includes('image')) {
        return {
          class: 'fas fa-file-image',
          color: '#999999',
        };
      } else if (type.includes('video') || type.includes('octet-stream') || type.includes('ogg')) {
        return {
          class: 'fas fa-file-video',
          color: '#79577A',
        };
      } else if (type.includes('zip') || type.includes('war') || type.includes('rar')) {
        return {
          class: 'fas fa-file-archive',
          color: '#717272',
        };
      } else if (type.includes('illustrator') || type.includes('eps')) {
        return {
          class: 'fas fa-file-contract',
          color: '#E79E24',
        };
      } else if (type.includes('html') || type.includes('xml') || type.includes('css')) {
        return {
          class: 'fas fa-file-code',
          color: '#6cf500',
        };
      } else {
        return {
          class: 'fas fa-file',
          color: '#476A9C',
        };
      }
    },
    getSize(document) {
      const size = this.$documentsUtils.getSize(document.sizeWithVersions);
      return {
        value: size.value,
        unit: size.unit
      };
    },
  }
};
</script>
