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
import './initComponents.js';
import '../cleanup-common/services.js';
import {initCleanupReviewHeaderExtension} from './extensions.js';

//getting language of the PLF
const lang = eXo?.env?.portal?.language || 'en';

//should expose the locale resources as REST API
const url = `/documents-portlet/i18n/locale.portlet.DocumentCleanupUserReview?lang=${lang}`;

/**
 * Documents-header extension entry point, called by the Documents app's
 * includeExtensions('DocumentsExtension') discovery: loads the review i18n
 * bundle then registers the header button (with its review drawer) into the
 * 'documents-header-right' extension point.
 *
 * @returns {Promise} resolved once the header extension is registered
 */
export function init() {
  return exoi18n.loadLanguageAsync(lang, url)
    .then(() => initCleanupReviewHeaderExtension());
}
