<!--

 Copyright (C) 2026 eXo Platform SAS.

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU Affero General Public License
 as published by the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 GNU Affero General Public License for more details.

 You should have received a copy of the GNU Affero General Public License
 along with this program. If not, see <gnu.org/licenses>.

-->
<template>
  <v-card
    v-if="holdsBin"
    id="documentsSpaceSettings"
    class="recycle-bin-space-settings card-border-radius"
    flat>
    <div class="text-title">
      {{ $t('recycleBin.spaceSettings.title') }}
    </div>
    <v-list class="pa-0">
      <v-list-item class="pa-0 mt-n2">
        <v-list-item-content>
          <v-list-item-title>
            {{ $t('recycleBin.title') }}
          </v-list-item-title>
        </v-list-item-content>
        <v-list-item-action>
          <v-btn
            id="spaceSettingsRecycleBin"
            :title="$t('recycleBin.title')"
            :aria-label="$t('recycleBin.title')"
            small
            icon
            @click="open">
            <v-icon size="18" class="icon-default-color">
              {{ $vuetify.rtl && 'fa-caret-left' || 'fa-caret-right' }}
            </v-icon>
          </v-btn>
        </v-list-item-action>
      </v-list-item>
    </v-list>
  </v-card>
</template>
<script>
export default {
  props: {
    spaceId: {
      type: [String, Number],
      default: () => eXo.env.portal.spaceId,
    },
  },
  data: () => ({
    section: 'documents-recycle-bin',
    space: null,
  }),
  computed: {
    // The bin of a space is opened by its managers and publishers: an
    // administrator of the platform reaches the settings of any space, and
    // sees no entry in one they neither manage nor publish in.
    holdsBin() {
      return !!this.space && (this.space.isManager || this.space.isPublisher);
    },
  },
  created() {
    this.$spaceService.getSpaceById(this.spaceId)
      .then(space => this.space = space)
      .catch(() => this.space = null);
  },
  methods: {
    // The bin is displayed as a section of the page: the page hides its other
    // blocks, this one included, so the section lives beside them until its
    // back arrow, or another section, gives the page back.
    open() {
      const page = this.$root;
      const section = this.section;
      const spaceId = String(this.spaceId);
      const container = document.createElement('div');
      (page.$el.querySelector('.v-main__wrap') || page.$el).appendChild(container);
      document.dispatchEvent(new CustomEvent('hideSettingsApps', {detail: section}));
      new Vue({
        parent: page,
        created() {
          document.addEventListener('showSettingsApps', this.close);
          document.addEventListener('hideSettingsApps', this.sectionChanged);
        },
        beforeDestroy() {
          document.removeEventListener('showSettingsApps', this.close);
          document.removeEventListener('hideSettingsApps', this.sectionChanged);
        },
        methods: {
          back() {
            document.dispatchEvent(new CustomEvent('showSettingsApps'));
          },
          sectionChanged(event) {
            if (event?.detail !== section) {
              this.close();
            }
          },
          close() {
            this.$destroy();
            this.$el.remove();
          },
        },
        render(createElement) {
          return createElement('v-card', {
            attrs: {id: 'spaceSettingsRecycleBinSection'},
            class: 'recycle-bin-space-section py-2',
            props: {flat: true},
          }, [createElement('documents-recycle-bin', {
            props: {scope: 'SPACE', spaceId},
            on: {back: this.back},
          })]);
        },
      }).$mount(container);
    },
  },
};
</script>
