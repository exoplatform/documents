<!--
 Copyright (C) 2025 eXo Platform SAS.

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU Affero General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU Affero General Public License for more details.

 You should have received a copy of the GNU Affero General Public License
 along with this program. If not, see <http://www.gnu.org/licenses/>.
-->

<template>
  <document-action-item
    icon="fa fas fa-stream"
    :label="$t('documents.label.post')"
    @click="openComposerDrawer" />
</template>

<script>
const ACTIVITY_APP_ID = 'activity-stream-quick-actions';

const ACTIVITY_I18N_URLS = lang => [
  `/social/i18n/locale.portlet.Portlets?lang=${lang}`,
  `/social/i18n/locale.commons.Commons?lang=${lang}`,
  `/social/i18n/locale.social.Webui?lang=${lang}`,
];

function buildAttachment(file) {
  return {
    eXoDrive: true,
    id: file.sourceID || file.id,
    name: file.name,
    isCloudFile: file.cloudDriveFile,
    isSelectedFromDrives: true,
    mimetype: file.mimeType,
    size: file.size,
    path: file.path,
    title: file.title || file.name,
  };
}

export default {
  props: {
    file: {
      type: Object,
      default: null,
    },
  },
  methods: {
    openComposerDrawer() {
      // Capture file immediately — the component may be destroyed before
      // activity-composer-ready fires (e.g. context menu closes on the same click).
      const file = this.file;
      this.$nextTick().then(() => new Promise(resolve => {
        window.require(['SHARED/eXoVueI18n', 'SHARED/ActivityStream'],
          exoi18n => this.openDrawer(exoi18n, resolve, file));
      }));
    },

    async openDrawer(exoi18n, callback, file) {
      if (!document.querySelector(`#${ACTIVITY_APP_ID}`)) {
        const container = document.createElement('div');
        container.id = ACTIVITY_APP_ID;
        document.querySelector('#vuetify-apps').appendChild(container);
        await this.createActivityDrawerApp(exoi18n);
      }

      // Register before opening so activity-composer-ready is never missed.
      document.addEventListener('activity-composer-ready', function onReady() {
        document.removeEventListener('activity-composer-ready', onReady);
        if (file) {
          document.dispatchEvent(new CustomEvent('init-attachments', {
            detail: { attachment: buildAttachment(file) },
          }));
        }
      });

      document.dispatchEvent(new CustomEvent('activity-composer-drawer-open'));
      callback();
    },

    createActivityDrawerApp(exoi18n) {
      const lang = eXo.env.portal.language;
      return new Promise(resolve =>
        exoi18n.loadLanguageAsync(lang, ACTIVITY_I18N_URLS(lang))
          .then(i18n => Vue.createApp({
            data: {
              maxFileSize: eXo.env.portal.maxFileSize,
              activityTypes: {},
              activityActions: {},
              commentActions: {},
              extensionApp: 'activity',
              activityTypeExtension: 'type',
              activityActionExtension: 'action',
              commentActionExtension: 'comment-action',
            },
            computed: {
              isMobile() {
                return this.$vuetify?.breakpoint?.mobile;
              },
              drawerParams() {
                return {
                  activityTypes: this.activityTypes,
                  activityActions: this.activityActions,
                  commentTypes: this.activityTypes,
                  commentActions: this.commentActions,
                };
              },
            },
            created() {
              this.activityTypes = extensionRegistry.loadExtensions(this.extensionApp, this.activityTypeExtension);
              this.activityActions = extensionRegistry.loadExtensions(this.extensionApp, this.activityActionExtension);
              this.commentActions = extensionRegistry.loadExtensions(this.extensionApp, this.commentActionExtension);
            },
            mounted() {
              resolve();
            },
            template: `
              <extension-registry-components
                id="${ACTIVITY_APP_ID}"
                :params="drawerParams"
                name="ActivityStream"
                type="activity-stream-drawers"
                parent-element="div"
                element="div"
                class="drawer-parent" />
            `,
            vuetify: Vue.prototype.vuetifyOptions,
            i18n,
          }, `#${ACTIVITY_APP_ID}`, 'Activity Composer Quick Action'))
          .finally(() => Vue.prototype.$utils.includeExtensions('ActivityStreamExtension'))
      );
    },
  },
};
</script>
