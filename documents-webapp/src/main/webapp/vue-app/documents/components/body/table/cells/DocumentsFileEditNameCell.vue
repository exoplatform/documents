<template>
  <v-text-field
    ref="autoFocusInput1"
    v-model="fileName"
    height="35px"
    type="text"
    class="documentEditName text-color font-weight-bold width-full pt-0 pl-1"
    :rules="nameRules"
    required
    autofocus
    outlined
    dense
    @focus="editTitle()"
    @blur="cancelEditNameMode()"
    @keyup="checkInput($event,fileName)"
    @contextmenu.stop.prevent>
    <div slot="append" class="d-flex">
      <v-divider v-if="!isMobile" vertical />
      <v-icon
        class="primary--text ma-1 px-1"
        :title="$t('documents.save')"
        small
        @click="checkInput(13,fileName)"
        @contextmenu.stop.prevent>
        fa-check
      </v-icon>
      <v-divider vertical />
      <v-icon
        class="clickable ma-1 px-1"
        :title="$t('documents.close')"
        color="red"
        small
        @click="cancelEditNameMode(fileName)"
        @contextmenu.stop.prevent>
        fa-times
      </v-icon>
    </div>
  </v-text-field>
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
    fileName: {
      type: String,
      default: '',
    },
    fileType: {
      type: String,
      default: '',
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
    nameRegex: /[<\\>:"/|?*]/
  }),
  created() {
    this.nameRules = [v => !!v.trim(), v => !this.nameRegex.test(v)];
    this.$root.$on('document-renamed', (file) => {
      if (file.id === this.file.id) {
        this.file.name = this.fileName.concat(this.fileType);
        this.$root.$emit('cancel-edit-mode', this.file);
      }
    });
  },
  methods: {
    editTitle(){
      this.editNameMode=true;
      this.$root.$emit('update-file-name', this.file);
    },
    cancelEditNameMode(){
      if (this.file.id===-1){
        this.$root.$emit('cancel-add-folder', this.file); 
      }
      this.editNameMode=false;
      this.$root.$emit('cancel-edit-mode', this.file);
    },
    checkInput: function(e,newTitle) {
      if (e.keyCode === 13 || e === 13) {
        if (this.file.folder && this.file.id === -1) {
          if (this.nameRegex.test(newTitle)) {
            this.$root.$emit('alert-message', this.$t('document.valid.name.error.message'),'warning');
            return;
          }
          this.$root.$emit('documents-create-folder', newTitle);
        } else {
          this.renameFile(newTitle);
        }
      }
      if (e.keyCode === 27) {
        this.cancelEditNameMode(newTitle);
      }
    },
    renameFile(newTitle){
      if (this.nameRegex.test(newTitle)) {
        this.$root.$emit('alert-message',this.$t('document.valid.name.error.message'),'warning');
        return;
      }
      //concat the file type to the new tilte when renaming file
      this.$root.$emit('documents-rename', this.file, newTitle.concat(this.fileType));
    }
  },
};
</script>
