<template>
  <exo-drawer
    ref="drawer"
    v-model="drawer"
    :confirm-close="modified"
    :confirm-close-labels="confirmCloseLabels"
    class="documentsWidgetFormDrawer"
    go-back-button
    right
    @closed="reset">
    <template slot="title">
      {{ $t('documentsWidget.label.addDocumentsWidgetDrawerTitle') }}
    </template>
    <template v-if="documentsWidget && drawer" #content>
      <v-form
        ref="form"
        autocomplete="off"
        class="pa-4">
        <div class="d-flex align-center mb-2 flex-grow-1 flex-shrink-1 text-truncate text-color">
          {{ $t('documentsWidget.label.documentsWidgetName') }}
        </div>
        <translation-text-field
          ref="documentsWidgetNameInput"
          id="documentsWidgetNameInput"
          v-model="documentsWidget.name"
          :rules="rules.name"
          :placeholder="$t('documentsWidget.label.documentsWidgetNamePlaceholder')"
          :maxlength="maxNameLength"
          drawer-title="documentsWidget.label.nameTranslation"
          class="width-auto flex-grow-1 mb-4"
          no-expand-icon
          back-icon
          autofocus
          required />

        <div class="d-flex align-center mb-2 flex-grow-1 flex-shrink-1 text-truncate text-color">
          {{ $t('documentsWidget.label.description') }}
        </div>
        <translation-text-field
          ref="documentsWidgetDescriptionInput"
          id="documentsWidgetDescriptionInput"
          v-model="documentsWidget.description"
          :rules="rules.description"
          :placeholder="$t('documentsWidget.label.descriptionPlaceholder')"
          :maxlength="maxDescriptionLength"
          :required="false"
          drawer-title="documentsWidget.label.descriptionTranslation"
          class="width-auto flex-grow-1 mb-4"
          no-expand-icon
          back-icon />

        <div class="d-flex align-center mb-2 flex-grow-1 flex-shrink-1 text-truncate text-color">
          {{ $t('documentsWidget.label.url') }}
        </div>
        <v-text-field
          id="documentsWidgetUrlInput"
          name="documentsWidgetUrlInput"
          ref="documentsWidgetUrlInput"
          v-model="documentsWidget.url"
          :placeholder="$t('documentsWidget.label.enterUrl')"
          :rules="rules.url"
          class="border-box-sizing width-auto pt-0 mb-4"
          type="text"
          outlined
          dense
          mandatory />

        <div class="d-flex mb-4">
          <div class="d-flex align-center flex-grow-1 flex-shrink-1 text-truncate text-color">
            {{ $t('documentsWidget.label.openInSameTab') }}
          </div>
          <v-switch
            v-model="documentsWidget.sameTab"
            class="my-0 me-n2"
            dense
            hide-details />
        </div>

        <div class="d-flex flex-column mb-4">
          <div class="d-flex align-center flex-grow-1 flex-shrink-1 text-truncate text-color">
            {{ $t('documentsWidget.label.updateIcon') }}
          </div>
          <documents-widget-icon-input
            v-model="documentsWidget.iconUploadId"
            :documents-widget="documentsWidget"
            class="mt-2"
            @reset="resetIcon"
            @src="documentsWidget.iconSrc = $event" />
        </div>
      </v-form>
    </template>
    <template #footer>
      <div class="d-flex align-center justify-end">
        <v-btn
          class="btn me-2"
          @click="close">
          {{ $t('documentsWidget.label.cancel') }}
        </v-btn>
        <v-btn
          :disabled="disabled"
          class="btn primary"
          @click="apply">
          {{ edit && $t('documentsWidget.label.update') || $t('documentsWidget.label.add') }}
        </v-btn>
      </div>
    </template>
  </exo-drawer>
</template>

<script>
export default {
  data: () => ({
    documentsWidget: null,
    originalDocumentsWidget: null,
    drawer: false,
    edit: false,
    canValidate: false,
    index: -1,
    valid: true,
    maxNameLength: 50,
    maxDescriptionLength: 50,
  }),
  computed: {
    disabled() {
      return !this.valid
        || !this.modified
        || this.documentsWidget?.name?.[this.$root.defaultLanguage]?.length > this.maxNameLength
        || this.documentsWidget?.description?.[this.$root.defaultLanguage]?.length > this.maxDescriptionLength;
    },
    modified() {
      return this.originalDocumentsWidget && JSON.stringify(this.originalDocumentsWidget) !== JSON.stringify(this.documentsWidget || {});
    },
    confirmCloseLabels() {
      return {
        title: this.$t('documentsWidget.title.confirmCloseModification'),
        message: this.$t('documentsWidget.message.confirmCloseModification'),
        ok: this.$t('confirm.yes'),
        cancel: this.$t('confirm.no'),
      };
    },
    rules() {
      return {
        name: [
          v => !!v?.length || ' ',
          v => !v?.length || v.length < this.maxNameLength || this.$t('documentsWidget.input.exceedsMaxLength', {
            0: this.maxNameLength,
          }),
        ],
        description: [
          v => !v?.length || v.length < this.maxDescriptionLength || this.$t('documentsWidget.input.exceedsMaxLength', {
            0: this.maxDescriptionLength,
          }),
        ],
        url: [
          v => !!v?.length || ' ',
          v => {
            try {
              return !!this.$documentsWidgetService.toDocumentsWidgetUrl(v)?.length || this.$t('documentsWidget.input.invalidDocumentsWidget');
            } catch (e) {
              return this.$t('documentsWidget.input.invalidDocumentsWidget');
            }
          },
        ],
      };
    },
  },
  watch: {
    documentsWidget: {
      deep: true,
      handler() {
        if ((this.canValidate || this.edit) && this.$refs.form) {
          this.valid = this.$refs.form.validate();
          this.$refs.form.resetValidation();
        }
      },
    },
    drawer() {
      if (this.drawer) {
        this.valid = false;
        window.setTimeout(() => {
          if (this.edit) {
            this.valid = false;
          } else {
            this.canValidate = true;
          }
        }, 200);
      }
    }
  },
  created() {
    this.$root.$on('documents-widget-form-drawer', this.open);
  },
  mounted() {
    document.querySelector('#vuetify-apps').appendChild(this.$el);
  },
  beforeDestroy() {
    this.$root.$off('documents-widget-form-drawer', this.open);
  },
  methods: {
    open(documentsWidget, edit, index) {
      if (!documentsWidget) {
        documentsWidget = {};
        documentsWidget.name = {};
        documentsWidget.name[this.$root.defaultLanguage] = '';
        documentsWidget.description = {};
        documentsWidget.description[this.$root.defaultLanguage] = '';
      }
      if (!documentsWidget.name?.[this.$root.defaultLanguage]) {
        documentsWidget.name[this.$root.defaultLanguage] = documentsWidget.name['en'] || '';
      }
      if (!documentsWidget.description?.[this.$root.defaultLanguage]) {
        documentsWidget.description[this.$root.defaultLanguage] = documentsWidget.description['en'] || '';
      }
      if (!documentsWidget.iconSrc) {
        documentsWidget.iconSrc = null;
      }
      this.documentsWidget = JSON.parse(JSON.stringify(documentsWidget));
      this.originalDocumentsWidget = JSON.parse(JSON.stringify(documentsWidget));
      this.edit = edit;
      this.index = index;
      this.canValidate = false;
      this.valid = false;
      this.$refs.drawer.open();
    },
    reset() {
      this.documentsWidget = null;
      this.originalDocumentsWidget = null;
    },
    close() {
      this.originalDocumentsWidget = null;
      return this.$nextTick().then(() => this.$refs.drawer.close());
    },
    resetIcon() {
      this.documentsWidget.iconUrl = null;
      this.documentsWidget.iconSrc = null;
      this.documentsWidget.iconUploadId = null;
      this.documentsWidget.iconFileId = 0;
    },
    apply() {
      this.canValidate = true;
      this.valid = this.$refs.form.validate();
      if (!this.valid) {
        return;
      }
      if (this.edit) {
        this.$emit('documents-widget-edit', this.documentsWidget, this.index);
      } else {
        this.$emit('documents-widget-add', this.documentsWidget);
      }
      this.close();
    },
  },
};
</script>
