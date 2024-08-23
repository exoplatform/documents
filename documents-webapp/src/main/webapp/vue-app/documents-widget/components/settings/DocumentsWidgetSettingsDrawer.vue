<template>
  <exo-drawer
    ref="drawer"
    v-model="drawer"
    :right="!$vuetify.rtl"
    :allow-expand="!$root.isMobile"
    :loading="loading || saving"
    :confirm-close="modified"
    :confirm-close-labels="confirmCloseLabels"
    class="documentsWidgetSettingsDrawer"
    eager
    @opened="stepper = 1"
    @closed="reset"
    @expand-updated="expanded = $event">
    <template slot="title">
      {{ $t('documentsWidget.drawer.title') }}
    </template>
    <template v-if="settings" #content>
      <v-stepper
        v-model="stepper"
        :class="expanded && 'flex-row' || 'flex-column'"
        class="ma-0 pa-4 d-flex"
        vertical
        flat>
        <div
          :class="{
            'col-6': expanded,
            'flex-grow-1': expanded || stepper === 1,
            'flex-grow-0': !expanded && stepper !== 1,
          }"
          class="flex-shrink-0">
          <v-stepper-step
            :step="1"
            :editable="!expanded"
            width="100%"
            class="ma-0 pa-0 position-relative">
            <div class="d-flex">
              <div class="d-flex align-center flex-grow-1 flex-shrink-1 text-truncate text-header">
                {{ $t('documentsWidget.label.addDocuments') }}
              </div>
              <v-btn
                v-if="(stepper === 1 || expanded) && hasDocuments"
                :title="$t('documentsWidget.label.addDocuments')"
                :class="{
                  'r-0': !$vuetify.rtl,

                  'l-0': $vuetify.rtl,
                }"
                class="position-absolute t-0 mt-n1"
                icon
                @click="$root.$emit('documents-widget-form-drawer')">
                <v-icon size="20">fa-plus</v-icon>
              </v-btn>
            </div>
          </v-stepper-step>
          <v-slide-y-transition>
            <div v-show="expanded || stepper === 1">
              <div v-if="hasDocuments" class="d-flex flex-column mt-8">
                <v-scroll-y-transition
                  v-for="(documentsWidget, index) in documentsWidget"
                  :key="`${documentsWidget.id}-${documentsWidget.url}-${index}`"
                  hide-on-leave>
                  <div
                    :key="`${documentsWidget.id}-${documentsWidget.url}-${index}`"
                    class="mb-2">
                    <documents-widget-input
                      :documents-widget="documentsWidget"
                      :first="index === 0"
                      :last="index === (documentsWidget.length - 1)"
                      @move-top="moveTop(index)"
                      @move-down="moveDown(index)"
                      @edit="edit(documentsWidget, index)"
                      @remove="remove(index)" />
                  </div>
                </v-scroll-y-transition>
              </div>
              <div v-else class="d-flex flex-grow-1 full-height full-width align-center justify-center">
                <v-btn
                  :title="$t('documentsWidget.label.addDocumentsButton')"
                  outlined
                  border
                  class="flex-grow-0 flex-shrink-0 mx-auto my-8 primary"
                  @click="$root.$emit('documents-widget-form-drawer')">
                  {{ $t('documentsWidget.label.add') }}
                </v-btn>
              </div>
            </div>
          </v-slide-y-transition>
        </div>
        <div
          :class="{
            'col-6': expanded,
            'mt-8': !expanded && stepper < 2,
            'mt-4': !expanded && stepper === 2,
          }"
          class="flex-grow-0 flex-shrink-0">
          <v-stepper-step
            :step="2"
            :editable="!expanded && !disabledSecondStep"
            class="ma-0 pa-0 position-relative">
            <div class="d-flex align-center flex-grow-1 flex-shrink-1 text-truncate text-header">
              {{ $t('documentsWidget.label.configureDisplay') }}
            </div>
          </v-stepper-step>
          <v-slide-y-transition>
            <div v-show="expanded || stepper > 1" class="mt-4">
              <div class="d-flex flex-column">
                <v-form
                  ref="form"
                  autocomplete="off">
                  <div class="d-flex flex-column">
                    <select
                      id="documentsWidgetSettingDisplayType"
                      v-model="settings.type"
                      class="flex-grow-1 ignore-vuetify-classes py-2 mb-4 height-auto width-auto text-truncate">
                      <option
                        v-for="item in displayTypes"
                        :key="item.value"
                        :value="item.value">
                        {{ item.label }}
                      </option>
                    </select>
                    <documents-widget-display-preview
                      :settings="settings"
                      :documents-Widget="documentsWidget"
                      class="pa-4 mb-4" />
                    <div class="mb-2">
                      <div class="d-flex mb-2">
                        <div class="d-flex align-center flex-grow-1 flex-shrink-1 text-truncate text-color">
                          {{ $t('documentsWidget.label.addHeader') }}
                        </div>
                        <v-switch
                          v-model="showHeader"
                          class="my-0 me-n2"
                          dense
                          hide-details />
                      </div>
                      <div v-if="showHeader && settings?.header" class="d-flex mb-2">
                        <translation-text-field
                          ref="documentsWidgetHeader"
                          id="documentsWidgetHeader"
                          v-model="settings.header"
                          :rules="rules.header"
                          :placeholder="$t('documentsWidget.label.headerPlaceHolder')"
                          :maxlength="maxHeaderLength"
                          drawer-title="documentsWidget.label.headerTranslation"
                          class="width-auto flex-grow-1"
                          no-expand-icon
                          autofocus
                          back-icon
                          required />
                      </div>
                    </div>
                    <div class="d-flex mb-4">
                      <div class="d-flex align-center flex-grow-1 flex-shrink-1 text-truncate text-color">
                        {{ $t('documentsWidget.label.largeIcons') }}
                      </div>
                      <v-switch
                        v-model="settings.largeIcon"
                        class="my-0 me-n2"
                        dense
                        hide-details />
                    </div>
                    <div class="d-flex mb-4">
                      <div class="d-flex align-center flex-grow-1 flex-shrink-1 text-truncate text-color">
                        {{ $t('documentsWidget.label.showName') }}
                      </div>
                      <v-switch
                        v-model="settings.showName"
                        class="my-0 me-n2"
                        dense
                        hide-details />
                    </div>
                    <div
                      v-if="settings?.type === 'COLUMN'"
                      class="d-flex mb-4">
                      <div class="d-flex align-center flex-grow-1 flex-shrink-1 text-truncate text-color">
                        {{ $t('documentsWidget.label.showDescription') }}
                      </div>
                      <v-switch
                        v-model="settings.showDescription"
                        class="my-0 me-n2"
                        dense
                        hide-details />
                    </div>
                    <div class="mb-2">
                      <div class="d-flex mb-2">
                        <div class="d-flex align-center flex-grow-1 flex-shrink-1 text-truncate text-color">
                          {{ $t('documentsWidget.label.addSeeMore') }}
                        </div>
                        <v-switch
                          v-model="seeMore"
                          class="my-0 me-n2"
                          dense
                          hide-details />
                      </div>
                      <div v-if="seeMore" class="mb-2">
                        <v-text-field
                          id="seeMoreInput"
                          name="seeMoreInput"
                          ref="seeMoreInput"
                          v-model="settings.seeMore"
                          :placeholder="$t('documentsWidget.label.enterUrl')"
                          :rules="rules.seeMore"
                          class="border-box-sizing width-auto pt-0"
                          type="text"
                          outlined
                          dense />
                      </div>
                    </div>
                  </div>
                </v-form>
              </div>
            </div>
          </v-slide-y-transition>
        </div>
      </v-stepper>

      <documents-widget-form-drawer
        @document-add="addDocument"
        @document-edit="editDocument"
        :is-open="isFormDrawerOpen"
        @close="isFormDrawerOpen = false"
        :document="currentDocument"
        :documents-widget="currentDocumentsWidget" />
    </template>
    <template v-slot:footer>
      <v-btn
        :disabled="saving"
        :loading="saving"
        :class="{'d-none': expanded}"
        @click="save"
        class="primary">
        {{ $t('documentsWidget.save') }}
      </v-btn>
      <v-btn
        :disabled="saving"
        :loading="saving"
        :class="{'d-none': !expanded}"
        @click="save"
        class="primary">
        {{ $t('documentsWidget.save') }}
      </v-btn>
      <v-btn
        :disabled="saving"
        :loading="saving"
        @click="closeDrawer"
        class="secondary">
        {{ $t('documentsWidget.close') }}
      </v-btn>
    </template>
  </exo-drawer>
</template>

<script>
