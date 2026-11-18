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
import {openLink} from '../../../js/DocumentsUtils.js';

const CODE_PREFIX = 'recycleBin.';
const SIZE_UNITS = ['B', 'KB', 'MB', 'GB', 'TB'];
const PRECISE_SIZE_UNIT = SIZE_UNITS.indexOf('GB');

let labels = null;

function baseUrl() {
  return `${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/documents/recycle-bin`;
}

// The bin a request acts in, as the query parameters the REST entries read.
// bin: {scope: 'USER' | 'SPACE' | 'DRIVE', spaceId, ownerIdentityId}
function binParams(bin, params) {
  const query = new URLSearchParams();
  query.append('scope', bin.scope);
  if (bin.spaceId) {
    query.append('spaceId', bin.spaceId);
  }
  if (bin.ownerIdentityId) {
    query.append('ownerIdentityId', bin.ownerIdentityId);
  }
  Object.keys(params || {}).forEach(key => {
    const value = params[key];
    if (value || value === 0) {
      query.append(key, value);
    }
  });
  return query.toString();
}

// A refusal answers a message code of the Recycle Bin and nothing else: the
// error carries it as its code, which is also the key of its label.
function refused(resp) {
  return resp.text()
    .catch(() => '')
    .then(text => {
      const error = new Error(`Recycle Bin request refused with status ${resp.status}`);
      error.status = resp.status;
      error.code = text && text.startsWith(CODE_PREFIX) && text.trim() || null;
      throw error;
    });
}

function send(url, method, body) {
  const options = {
    method,
    credentials: 'include',
  };
  if (body) {
    options.headers = {'Content-Type': 'application/json'};
    options.body = JSON.stringify(body);
  }
  return fetch(url, options).then(resp => {
    if (resp?.ok) {
      return resp;
    }
    return refused(resp);
  });
}

export function loadLabels() {
  if (!labels) {
    const lang = eXo?.env?.portal?.language || 'en';
    labels = exoi18n.loadLanguageAsync(lang, `/documents-portlet/i18n/locale.portlet.RecycleBin?lang=${lang}`);
  }
  return labels;
}

export function getItems(bin, name, sortField, sortDirection, offset, limit) {
  return send(`${baseUrl()}/items?${binParams(bin, {name, sortField, sortDirection, offset, limit})}`, 'GET')
    .then(resp => resp.json());
}

export function restoreItem(bin, itemId) {
  return send(`${baseUrl()}/items/${encodeURIComponent(itemId)}/restore?${binParams(bin)}`, 'PUT');
}

export function deleteItem(bin, itemId) {
  return send(`${baseUrl()}/items/${encodeURIComponent(itemId)}?${binParams(bin)}`, 'DELETE');
}

export function restoreItems(bin, actionId, itemIds) {
  return send(`${baseUrl()}/bulk/restore/${actionId}?${binParams(bin)}`, 'PUT', itemIds);
}

export function deleteItems(bin, actionId, itemIds) {
  return send(`${baseUrl()}/bulk/delete/${actionId}?${binParams(bin)}`, 'DELETE', itemIds);
}

export function emptyBin(bin, actionId) {
  return send(`${baseUrl()}/${actionId}?${binParams(bin)}`, 'DELETE');
}

export function getContentUrl(itemId) {
  return `${baseUrl()}/items/${encodeURIComponent(itemId)}/content`;
}

// Opens a deleted document: in the office editor, read-only and in a new
// tab, when the editor handles its type, else in the preview.
export function viewItem(item) {
  const officeTypes = extensionRegistry.loadExtensions('documents', 'supported-document-types') || [];
  if (item.documentId && officeTypes.some(type => type.mimeType === item.mimeType)) {
    openLink(`${eXo.env.portal.context}/${eXo.env.portal.metaPortalName}/oeditor?docId=${item.documentId}&mode=view&backTo=${window.location.pathname}`, '_blank');
  } else {
    document.dispatchEvent(new CustomEvent('open-attachments-preview', {detail: {
      attachments: [{
        id: item.id,
        filename: item.name,
        mimetype: item.mimeType,
        source: 'documents-recycle-bin',
        downloadUrl: getContentUrl(item.id),
      }],
      id: item.id,
    }}));
  }
}

export function getDrives(offset, limit) {
  return send(`${baseUrl()}/drives?offset=${offset || 0}&limit=${limit || 0}`, 'GET')
    .then(resp => resp.json());
}

export function getSettings() {
  return send(`${baseUrl()}/settings`, 'GET')
    .then(resp => resp.json());
}

export function saveSettings(retentionDays) {
  return send(`${baseUrl()}/settings`, 'PUT', {retentionDays});
}

export function newActionId() {
  return String(crypto.getRandomValues(new Uint32Array(1))[0]);
}

// The label key of what a request ended on: its message code when it has
// one, else a label for its kind of failure.
export function getErrorLabel(error) {
  if (error?.code) {
    return error.code;
  }
  return error?.status === 403 && 'recycleBin.error.forbidden' || 'recycleBin.error.unexpected';
}

// Tells the user why a request ended on an error. translate gives the label
// of a key: a message code that has no label is told as an unexpected error.
export function displayError(error, translate) {
  const key = getErrorLabel(error);
  const label = translate(key);
  displayAlert(label && label !== key ? label : translate('recycleBin.error.unexpected'), 'error');
}

export function formatSize(size) {
  let value = Math.max(Number(size) || 0, 0);
  let unit = 0;
  // Whole numbers up to megabytes, two decimals from gigabytes on.
  const rounded = () => unit >= PRECISE_SIZE_UNIT && Math.round(value * 100) / 100 || Math.round(value);
  while (rounded() >= 1024 && unit < SIZE_UNITS.length - 1) {
    value /= 1024;
    unit++;
  }
  return {
    value: rounded(),
    unit: SIZE_UNITS[unit],
  };
}

export function displayAlert(alertMessage, alertType) {
  document.dispatchEvent(new CustomEvent('alert-message', {detail: {
    alertMessage,
    alertType: alertType || 'success',
  }}));
}
