import './initComponents.js';
import {initDocumentsExtensions} from './extensions.js';

import * as documentFileService from '../documents/js/DocumentFileService.js';
if (!Vue.prototype.$documentFileService) {
  window.Object.defineProperty(Vue.prototype, '$documentFileService', {
    value: documentFileService,
  });
}


export function init() {
  initDocumentsExtensions();
}