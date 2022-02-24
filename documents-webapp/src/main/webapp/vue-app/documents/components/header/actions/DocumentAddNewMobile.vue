<template>
  <exo-drawer 
    ref="documentAddNewMobileMenu"
    :bottom="true">
    <template slot="content">
      <v-list dense>
        <v-list-item
          @click="addFolder"
          class="px-2">
          <v-icon
            size="19"
            class="clickable pr-2">
            fa-folder
          </v-icon>
          <span v-if="!isMobile" class="body-2 text-color">{{ $t('documents.button.addNewFolder') }}</span>
        </v-list-item>
        <v-list-item
          @click="openDrawer"
          class="px-2">
          <v-icon
            size="19"
            class="clickable pr-2">
            fa-file-alt
          </v-icon>
          <span v-if="!isMobile" class="body-2 text-color">{{ $t('documents.button.addNewFile') }}</span>
        </v-list-item>
      </v-list>
      <exo-drawer />
    </template>
  </exo-drawer>
</template>
<script>

export default {

  methods: {
    open() {
      this.$refs.documentAddNewMobileMenu.open();
    },
    close() {
      this.$refs.documentAddNewMobileMenu.close();
    },
    openDrawer() {
      this.$root.$emit('documents-open-drawer');
      this.close();
    },
    addFolder() {
      this.$root.$emit('documents-add-folder');
      this.close();
    },
  }
};
</script>
