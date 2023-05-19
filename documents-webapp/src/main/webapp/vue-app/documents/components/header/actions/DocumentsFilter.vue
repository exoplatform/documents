<template>
  <v-row>
    <select
      id="filterDocumentsSelect"
      v-model="primaryFilter"
      v-if="!isMobile"
      name="documentsFilter"
      class="selectPrimaryFilter input-block-level ignore-vuetify-classes  pa-0 my-auto mx-1"
      @change="changeDocumentsFilter">
      <option
        v-for="item in filterDocuments"
        :key="item.name"
        :value="item.name">
        {{ $t('documents.filter.'+item.name.toLowerCase()) }}
      </option>
    </select>

    <button
      :class="btnClass"
      class="btn pa-2 width-max-content mx-1"
      @click="openDrawer()">
      <v-icon size="16" class="filterIcon"> fa-sliders-h </v-icon>
      <span v-if="!isMobile">{{ $t('documents.label.filter') }}</span>    
      <span v-if="filterNumber>0">({{ filterNumber }})</span>    
    </button>
  </v-row>
</template>
<script>
export default {
  props: {
    isMobile: {
      type: Boolean,
      default: false
    },
    primaryFilter: {
      type: String,
      default: 'all',
    },
    fileType: {
      type: Array,
      default: () => []
    },
    afterDate: {
      type: Number,
      default: null,
    },
    beforDate: {
      type: Number,
      default: null,
    },
    minSize: {
      type: Number,
      default: null,
    },
    maxSize: {
      type: Number,
      default: null,
    },
  },
  data: () => ({
    filterDocuments: [{name: 'all'},{name: 'favorites'}],
    query: '',
    extended: false,
  }),
  created() {
    this.$root.$on('set-documents-filter', data => {
      this.primaryFilter= data;
    });
    this.$root.$on('set-documents-search', data => {
      this.query= data.query;
      this.extended= data.extended;
    });
  },
  computed: {
    filterNumber(){
      let fNum = 0;
      if (this.primaryFilter.toLowerCase()!=='all') {
        fNum++;
      }
      if (this.extended && this.query) {
        fNum++;
      }
      if (this.fileType?.length>0) {
        fNum++;
      }
      if (this.afterDate && this.beforDate) {
        fNum++;
      }
      if (this.minSize) {
        fNum++;
      }
      if (this.maxSize) {
        fNum++;
      }
      return fNum;
    },
    btnClass(){
      if (this.filterNumber>0 || this.query){
        return 'filter-active-button';
      }
      return '';
    }
  },
  methods: {
    changeDocumentsFilter(){
      this.$root.$emit('documents-filter', this.primaryFilter.toLowerCase());
      this.$root.$emit('set-mobile-filter', this.primaryFilter);
    },
    openDrawer(){
      if (this.isMobile){
        this.$root.$emit('open-mobile-filter-menu',true);
      } else {
        this.$root.$emit('open-advanced-filter-drawer');
      }
      
    },  
  },
};
</script>