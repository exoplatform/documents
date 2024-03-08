/*
 * Copyright (C) 2024 eXo Platform SAS.
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


export function getData() {
  const ownerId = eXo.env.portal.spaceIdentityId || eXo.env.portal.userIdentityId;
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/documents/size/${ownerId}`, {
    headers: {
      'Content-Type': 'text/plain'
    },
    method: 'GET'
  }).then((resp) => {
    if (resp && resp.ok) {
      return resp.json();
    } else {
      throw new Error('Server indicates an error while sending request');
    }
  });   
}
export function updateSize() {
  const ownerId = eXo.env.portal.spaceIdentityId || eXo.env.portal.userIdentityId;
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/documents/size/${ownerId}`, {
    method: 'POST'
  }).then((resp) => {
    if (resp && resp.ok) {
      return resp.json();
    } else {
      throw new Error('Server indicates an error while sending request');
    }
  });   
}
export function getBiggestDocuments(offset,limit) {
  const ownerId = eXo.env.portal.spaceIdentityId || eXo.env.portal.userIdentityId;
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/documents/biggest/${ownerId}?offset=${offset}&limit=${limit}`, {
    headers: {
      'Content-Type': 'text/plain'
    },
    method: 'GET'
  }).then((resp) => {
    if (resp && resp.ok) {
      return resp.json();
    } else {
      throw new Error('Server indicates an error while sending request');
    }
  });
}
