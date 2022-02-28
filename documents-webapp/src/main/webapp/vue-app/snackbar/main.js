import ExoDocumentNotificationAlert from './ExoDocumentNotificationAlert.vue';
import ExoDocumentNotificationAlerts from './ExoDocumentNotificationAlerts.vue';

const components = {
  'exo-document-notification-alert': ExoDocumentNotificationAlert,
  'exo-document-notification-alerts': ExoDocumentNotificationAlerts,
};

for (const key in components) {
  Vue.component(key, components[key]);
}
