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
export default {
  props: {
    file: {
      type: Object,
      default: null,
    }
  },
  methods: {
    openComposerDrawer() {
      const file = this.file;
      this.$nextTick().then(() => new Promise(resolve => {
        window.require(['SHARED/eXoVueI18n', 'SHARED/ActivityStream'], exoi18n => this.initActivityDrawer(exoi18n, resolve, file));
      }),);
    },
    async initActivityDrawer(exoi18n, callback, file) {
      const appId = 'activity-stream-quick-actions';
      if (!document.querySelector(`#${appId}`)) {
        const parent = document.createElement('div');
        parent.id = appId;
        document.querySelector('#vuetify-apps').appendChild(parent);
        await this.initActivityDrawerApp(appId, exoi18n, eXo.env.portal.maxFileSize);
      }
      const onComposerReady = () => {
        document.removeEventListener('activity-composer-ready', onComposerReady);
        if (!file) {
          return;
        }
        const attachment = {
          eXoDrive: true,
          id: file?.sourceID || file?.id,
          name: file.name,
          isCloudFile: file.cloudDriveFile,
          isSelectedFromDrives: true,
          mimetype: file.mimeType,
          size: file.size,
          path: file.path,
          title: file.title || file.name,
        };
        document.dispatchEvent(new CustomEvent('init-attachments', {detail: {
          attachment: attachment
        }}));
      };
      document.addEventListener('activity-composer-ready', onComposerReady);
      document.dispatchEvent(new CustomEvent('activity-composer-drawer-open'));
      callback();
    },

    initActivityDrawerApp(appId, exoi18n, maxFileSize) {
      const lang = eXo.env.portal.language;
      const urls = [
        `/social/i18n/locale.portlet.Portlets?lang=${lang}`,
        `/social/i18n/locale.commons.Commons?lang=${lang}`,
        `/social/i18n/locale.social.Webui?lang=${lang}`,
      ];
      return new Promise(resolveInit => exoi18n.loadLanguageAsync(lang, urls)
        .then(i18n => Vue.createApp({
          data: {
            maxFileSize,
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
            resolveInit();
          },
          template: `
        <extension-registry-components
          id="${appId}"
          :params="drawerParams"
          name="ActivityStream"
          type="activity-stream-drawers"
          parent-element="div"
          element="div"
          class="drawer-parent" />
      `,
          vuetify: Vue.prototype.vuetifyOptions,
          i18n,
        }, `#${appId}`, 'Activity Composer Quick Action'))
        .finally(() => Vue.prototype.$utils.includeExtensions('ActivityStreamExtension')));
    }
  },
};
</script>
