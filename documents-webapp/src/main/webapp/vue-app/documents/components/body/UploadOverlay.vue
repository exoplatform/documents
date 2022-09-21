<template>
  <div v-show="showOverlay" class="uploadOverlay">
    <div class="ma-4 d-flex documents-upload-body flex-column justify-center text-center text-color">
      <v-icon size="60" class="uploadIcon"> fa-file-upload </v-icon>
      <span class="uploadText">{{ $t('documents.upload.DropMessage') }}</span>
      <span class="uploadText">({{ $t('documents.upload.maxFileSize').replace('{0}', maxFileSize) }})</span>
    </div>
  </div>
</template>

<script>
export default {
  props: {
    maxFileSize: {
      type: Number,
      default: parseInt(`${eXo.env.portal.maxFileSize}`)
    },
  },

  data: () => ({
    showOverlay: false,
  }),

  created() {
    this.$root.$on('show-upload-overlay',this.show);
    this.$root.$on('hide-upload-overlay',this.hide);
  },
  methods: {
    show() {
      this.showOverlay=true;
      const tableEl =  this.getDropZoneE3lement();
      if (tableEl) {
        tableEl.classList.add('tableOverlayed');
      }  
    },
    hide() {
      this.showOverlay=false;
      const tableEl =  this.getDropZoneE3lement();
      if (tableEl) {
        tableEl.classList.remove('tableOverlayed');
      }
    },
    getDropZoneE3lement(){
      let tableEl = document.querySelector('.documents-table');
      if (!tableEl) {
        tableEl = document.querySelector('.documents-folder-table');
      }
      if (!tableEl) {
        tableEl = document.querySelector('.documents-no-body');
      }
      return tableEl;
    }
  }
};
</script>