<template>
  <div
    v-if="fileCanEdit"
    :id="`document-action-menu-cel-${fileId}`"
    class="position-relative">
    <i
      icon
      small
      class="uiIcon uiIconVerticalDots d-none"
      @click="displayActionMenu = true">
    </i>
    <v-menu
      v-model="displayActionMenu"
      :attach="`#document-action-menu-cel-${fileId}`"
      transition="slide-x-reverse-transition"
      content-class="documentActionMenu"
      offset-y
      offset-x>
      <v-list class="pa-0" dense>
        <v-list-item
          class="px-2">
          <v-list-item-title class="subtitle-2 clickable" @click="editDoc(fileId)">
            <i class="uiIcon uiIconEdit pe-1"></i>
            <span>{{ $t('document.label.edit') }}</span>
          </v-list-item-title>
        </v-list-item>
      </v-list>
    </v-menu>
  </div>
</template>

<script>
export default {
  props: {
    file: {
      type: Object,
      default: null,
    },
  },
  data: () => ({
    displayActionMenu: false,
    waitTimeUntilCloseMenu: 200,
  }),
  computed: {
    fileId() {
      return this.file && this.file.id;
    },
    fileCanEdit(){
      const type = this.file && this.file.mimeType || '';
      return ( type.includes('word') || type.includes('presentation') || type.includes('sheet') );
    }
  },
  created() {
    $(document).on('mousedown', () => {
      if (this.displayActionMenu) {
        window.setTimeout(() => {
          this.displayActionMenu = false;
        }, this.waitTimeUntilCloseMenu);
      }
    });
  },
  methods: {
    editDoc(id){
      if (id) {
        window.open(`${eXo.env.portal.context}/${eXo.env.portal.portalName}/oeditor?docId=${id}&source=peview`, '_blank');
      }
    },
  }
};
</script>