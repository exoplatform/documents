<template>
  <v-scale-transition>
    <select
      id="filterDocumentsSelect"
      v-model="primaryFilter"
      v-if="!isMobile"
      name="documentsFilter"
      class="selectPrimaryFilter input-block-level ignore-vuetify-classes  pa-0 my-auto ml-2"
      @change="changeDocumentsFilter">
      <option
        v-for="item in filterDocuments"
        :key="item.name"
        :value="item.name">
        {{ $t('documents.filter.'+item.name.toLowerCase()) }}
      </option>
    </select>
    <button
      v-if="isMobile"
      :class="btnClass"
      class="px-3 width-max-content"
      @click="openDrawer()">
      <v-icon size="16" class="filterIcon"> fa-sliders-h </v-icon>
      <span v-if="filterNumber>0">({{ filterNumber }})</span>    
    </button>
  </v-scale-transition>
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
      return fNum;
    },
    btnClass(){
      if (this.filterNumber>0 || this.query){
        return 'mobile-filter-button';
      }
      return 'btn py-3';
    }
  },
  methods: {
    changeDocumentsFilter(){
      this.$root.$emit('documents-filter', this.primaryFilter.toLowerCase());
      this.$root.$emit('set-mobile-filter', this.primaryFilter);
    },
    openDrawer(){
      this.$root.$emit('open-mobile-filter-menu',true);
    },  
  },
};
</script>