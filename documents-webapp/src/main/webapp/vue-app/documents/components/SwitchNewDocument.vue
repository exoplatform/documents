<template>
  <div class="switch-application VuetifyApp newAppSwitch">
    <div data-app="true" class="v-application white v-application--is-ltr theme--light">
      <div class="v-application--wrap">
        <div>
          <div class="switchPosition d-flex align-center">
            <v-label for="switch">
              <span class="text-color labelStyle body-2 pe-1">
                {{ $t('document.label.classicApp') }}
              </span>
            </v-label>
            <v-switch
              v-model="classicApp"
              class="mt-0 me-1" />
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
<script>
export default {
  data: () => ({
    classicApp: false,
  }),
  watch: {
    classicApp() {
      this.switchApp();
    }
  },

  methods: {
    switchApp() {
      if (this.classicApp) {
        if ( document.getElementById('UIJcrExplorerContainer').classList.contains('VuetifyApp') ){
          document.getElementById('UIJcrExplorerContainer').classList.remove('VuetifyApp');
        }
        $('#DocumentsApplication').hide();
        $('#OldDocumentsApplication').show();
      } else {
        if ( !document.getElementById('UIJcrExplorerContainer').classList.contains('VuetifyApp') ){
          document.getElementById('UIJcrExplorerContainer').classList.add('VuetifyApp');
        }
        $('#OldDocumentsApplication').hide();
        $('#DocumentsApplication').show();
      }
    },
  }
 
};
</script>