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

// Reads the security settings of a space, as the space's security REST
// endpoint answers them
export function getSpaceSecuritySettings(spaceId) {
  return fetch(`/social/rest/space/${spaceId}/security`, {credentials: 'include'})
    .then(resp => {
      if (!resp?.ok) {
        throw toError(resp);
      }
      return resp.json();
    });
}

// Changes some security settings of a space; a refusal is thrown with the
// code the endpoint answers, when it answers one, and its status
export function updateSpaceSecuritySettings(spaceId, changes) {
  return fetch(`/social/rest/space/${spaceId}/security`, {
    method: 'PATCH',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(changes),
  }).then(resp => {
    if (!resp?.ok) {
      return resp.json()
        .catch(() => null)
        .then(body => {
          throw toError(resp, body?.message);
        });
    }
    return resp.json();
  });
}

function toError(resp, code) {
  const error = new Error(code || `HTTP ${resp?.status}`);
  error.status = resp?.status;
  error.code = code;
  return error;
}
