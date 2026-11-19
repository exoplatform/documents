/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see<http://www.gnu.org/licenses/>.
 */

// Counts what a space's documents expose outside the space, for a manager of
// the space: {publicLinks, sharedOutside}
export function getExposure(spaceId) {
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/documents/space/${spaceId}/exposure`, {
    credentials: 'include',
  }).then(resp => {
    if (!resp?.ok) {
      throw toError(resp);
    }
    return resp.json();
  });
}

// Reads a page of a space's documents carrying a public link, sorted by name:
// {documents: [{id, name, fileType}], size, offset, limit}
export function getPublicLinkDocuments(spaceId, offset, limit) {
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/documents/space/${spaceId}/exposure/publicLinks?offset=${offset || 0}&limit=${limit || 20}`, {
    credentials: 'include',
  }).then(resp => {
    if (!resp?.ok) {
      throw toError(resp);
    }
    return resp.json();
  });
}

function toError(resp) {
  const error = new Error(`HTTP ${resp?.status}`);
  error.status = resp?.status;
  return error;
}
