<template>
  <div>
    <v-text-field
      ref="autoFocusInput1"
      v-model="file.name"
      type="text"
      :rules="nameRules"
      @focus="editTitle()"
      @blur="cancelEditNameMode()"
      class="documentEditName font-weight-bold text-color width-full pt-0"
      required
      autofocus
      outlined
      dense
      @keyup="checkInput($event,file.name)">
      <div slot="append" class="d-flex">
        <v-divider v-if="!isMobile" vertical />
        <v-icon
          class="primary--text ma-1 px-1"
          small
          @click="checkInput(13,file.name)">
          fa-check
        </v-icon>
        <v-divider vertical />
        <v-icon
          class="clickable ma-1 px-1"
          color="red"
          small
          @click="cancelEditNameMode(file.name)">
          fa-times
        </v-icon>
      </div>
    </v-text-field>
  </div>
</template>
<script>
export default {
  props: {
    file: {
      type: Object,
      default: null,
    },
    editNameMode: {
      type: Boolean,
      default: false,
    },
    isMobile: {
      type: Boolean,
      default: false,
    },
  },
  data: () => ({
    editName: false,
    nameRules: [],
    workspace: 'collaboration',
    oldPath: '',
  }),
  created() {
    this.nameRules = [v => !v || (v.split('.')[0].length > 1)];
  },
  methods: {
    editTitle(){
      this.editNameMode=true;
      this.$root.$emit('update-file-name', this.file);
    },
    cancelEditNameMode(){
      this.editNameMode=false;
      this.$root.$emit('cancel-edit-mode', this.file);
    },
    checkInput: function(e,newTitle) {
      if (e.keyCode === 13 || e === 13) {
        this.renameFile(newTitle);
      }
      if (e.keyCode === 27) {
        this.cancelEditNameMode(newTitle);
      }
    },
    renameFile(newTitle){
      if (newTitle!=null){
        newTitle = newTitle.split('.')[0];
        this.$attachmentService.getAttachmentById(this.file.id)
          .then(attachment => {
            const path = attachment.path;
            this.oldPath = this.workspace.concat(':', path);
          })
          .catch(e => console.error(e))
          .finally(() => {
            return this.$documentFileService.renameFile(newTitle,this.oldPath).then( () => {
              this.$root.$emit('cancel-edit-mode', this.file);
            }).catch(e => {
              console.error('Error when updating title', e);
            });
          });
      }
    }
  },
};
</script>