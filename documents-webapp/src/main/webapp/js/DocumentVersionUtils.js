/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <gnu.org/licenses>.
 */
import {getDocumentDetails} from './attachmentService.js';
import {getEditorUrl, openLink} from './DocumentsUtils.js';

/**
 * Opens a past version of a file read only: in the online viewer when it can
 * show the file's type, else in the attachments preview, from the version's
 * own content.
 *
 * @param {Object} version the version, with its originId, frozenId and
 *          versionNumber
 * @param {Array} supportedDocuments the types the online editor handles
 * @param {Function} getIcon gives the icon of a file, for the preview
 * @returns {Promise} resolved with the file once opened in the preview,
 *          or with nothing once opened in the viewer
 */
export function openFileVersion(version, supportedDocuments, getIcon) {
  return getDocumentDetails(version.originId)
    .then(attachment => getDocumentDetails(version.frozenId)
      .then(file => {
        file.downloadUrl = `/rest/jcr/repository/collaboration${attachment.path}?version=${version.versionNumber}`;
        file.path = attachment.path;
        file.mimeType = attachment.mimeType;
        file.filename = attachment.name;
        file.source = 'documents';
        if (isReadable(supportedDocuments, attachment)) {
          if (file?.id) {
            openLink(getEditorUrl(file, 'view'), '_blank');
          }
        } else {
          const versionFile = {'id': file.id, 'filename': attachment.name, 'mimetype': attachment.mimeType, 'source': 'documents', 'downloadUrl': file.downloadUrl, 'icon': getIcon(attachment)};
          document.dispatchEvent(new CustomEvent('open-attachments-preview', {detail: {'attachments': [versionFile], 'id': file.id}}));
          return attachment;
        }
      }));
}

/**
 * Tells whether the online editor can show a file's type.
 *
 * @param {Array} supportedDocuments the types the online editor handles
 * @param {Object} file the file
 * @returns {boolean} true when one of the types is the file's
 */
function isReadable(supportedDocuments, file) {
  return !!supportedDocuments && supportedDocuments.filter(doc => doc.mimeType === file.mimeType).length > 0;
}
