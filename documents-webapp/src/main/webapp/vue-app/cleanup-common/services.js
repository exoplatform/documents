/*
 * Copyright (C) 2026 eXo Platform SAS.
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

import * as cleanupService from './js/CleanupService.js';
import * as cleanupUtils from './js/CleanupUtils.js';
import * as documentsUtils from '../../js/DocumentsUtils.js';

if (!Vue.prototype.$cleanupService) {
  window.Object.defineProperty(Vue.prototype, '$cleanupService', {
    value: cleanupService,
  });
}
if (!Vue.prototype.$cleanupUtils) {
  window.Object.defineProperty(Vue.prototype, '$cleanupUtils', {
    value: cleanupUtils,
  });
}
/**
 * Byte sizes are rendered through the Documents-side DocumentsUtils.getSize
 * helper (the shared value/unit split) and the shared
 * 'document.size.label.unit.*' i18n keys — the cleanup apps carry no byte
 * formatter and no hardcoded English suffix of their own.
 *
 * Defined on Vue.prototype rather than exported from CleanupUtils because the
 * unit label needs $t, which resolves against the calling component's i18n
 * instance: called from a template as $cleanupSize(bytes), 'this' is the
 * component, so $t works. A plain module function could not translate anything.
 */
if (!Vue.prototype.$cleanupSize) {
  window.Object.defineProperty(Vue.prototype, '$cleanupSize', {
    value: function(bytes) {
      if (bytes == null || isNaN(bytes)) {
        return '';
      }
      const size = documentsUtils.getSize(Number(bytes));
      return `${size.value} ${this.$t(`document.size.label.unit.${size.unit}`)}`;
    },
  });
}
/**
 * Localizes ONE cleanup message code — the single implementation shared by every
 * consumer (the campaign actions, the create drawer, the per-item review
 * failures), all of which used to carry their own copy of these three lines.
 *
 * The cleanup REST endpoints answer a message code as the error body
 * (cleanup.campaignAlreadyActive, cleanup.notOwner, cleanup.nameMandatory...),
 * unwrapped from Spring's error envelope by CleanupService (see errorMessage
 * there). Accepts either that bare code or the rejected Error carrying it, so a
 * caller never has to dig into error.message itself.
 *
 * An UNKNOWN code is never shown raw: it falls back to the caller's own generic
 * sentence, whose key differs per portlet bundle and is therefore a parameter.
 *
 * On Vue.prototype rather than in CleanupUtils, for the same reason as
 * $cleanupSize above: it needs $t, which resolves against the calling
 * component's i18n instance.
 *
 * @param {String|Error} codeOrError message code, or the Error whose message is
 *          that code
 * @param {String} fallbackKey i18n key of the generic sentence shown when the
 *          code carries no bundle entry
 */
if (!Vue.prototype.$cleanupErrorLabel) {
  window.Object.defineProperty(Vue.prototype, '$cleanupErrorLabel', {
    value: function(codeOrError, fallbackKey) {
      const code = (typeof codeOrError === 'string' ? codeOrError : codeOrError?.message)?.trim();
      const label = code && this.$t(code);
      return !label || label === code ? this.$t(fallbackKey) : label;
    },
  });
}
