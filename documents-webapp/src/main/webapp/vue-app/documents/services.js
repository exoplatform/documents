/*
 * Copyright (C) 2025 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

import * as documentFileService from '../../js/DocumentFileService.js';
import * as documentsUtils from '../../js/DocumentsUtils.js';
import * as documentsWebSocket from './js/WebSocket.js';
import * as documentOfflineService from './js/DocumentOfflineService.js';
import * as transferRulesService from '../../js/transferRulesService.js';

if (!Vue.prototype.$documentFileService) {
  window.Object.defineProperty(Vue.prototype, '$documentFileService', {
    value: documentFileService,
  });
}

if (!Vue.prototype.$documentOfflineService) {
  window.Object.defineProperty(Vue.prototype, '$documentOfflineService', {
    value: documentOfflineService,
  });
}

if (!Vue.prototype.$documentsUtils) {
  window.Object.defineProperty(Vue.prototype, '$documentsUtils', {
    value: documentsUtils,
  });
}

if (!Vue.prototype.$documentsWebSocket) {
  window.Object.defineProperty(Vue.prototype, '$documentsWebSocket', {
    value: documentsWebSocket,
  });
}
if (!Vue.prototype.$transferRulesService) {
  window.Object.defineProperty(Vue.prototype, '$transferRulesService', {
    value: transferRulesService,
  });
}
