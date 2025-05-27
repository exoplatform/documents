<template>
  <div>
    <div
      class="clickable my-10px mx-2"
      @click="hide(!file.hidden)">
      <v-icon
        v-if="file.hidden"
        size="16"
        class="pe-1">
        fas fa-eye-slash
      </v-icon>
      <v-icon
        v-else
        size="16"
        class="pe-1">
        fas fa-eye
      </v-icon>
      <span v-if="file.hidden" class="ps-1 text-body menu-text-color">{{ $t('documents.label.unhide') }}</span>
      <span v-else class="ps-1 text-body menu-text-color">{{ $t('documents.label.hide') }}</span>
    </div>
  </div>  
</template>
<script>
export default {
  props: {
    file: {
      type: Object,
      default: null,
    }
  },

  methods: {
    hide(hidden) {
      const ownerId = eXo.env.portal.spaceIdentityId || eXo.env.portal.userIdentityId;
      this.file.hidden = hidden;
      return this.$documentFileService.updateVisibility(ownerId,this.file)
        .then(() => {
          const message = this.file.hidden ? this.$t('documents.alert.success.document.hidden') : this.$t('documents.alert.success.document.unhidden');
          if (this.isMobile){
            this.displayAlert(message);
          } else {
            this.$root.$emit('alert-message',  message, 'success');
          }
        }).catch(() => {
          const message = this.file.hidden ? this.$t('documents.alert.error.document.hidden') : this.$t('documents.alert.error.document.unhidden');
          this.$root.$emit('alert-message',  message, 'error');
        });
    }
  },
};
</script>