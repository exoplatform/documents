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

const BASE_URL = '/documents-portlet/rest/cleanup';

export function getCampaigns() {
  return fetch(`${BASE_URL}/campaigns`, {
    method: 'GET',
    credentials: 'include',
  }).then(handleJsonResponse);
}

export function getCampaign(campaignId) {
  return fetch(`${BASE_URL}/campaigns/${campaignId}`, {
    method: 'GET',
    credentials: 'include',
  }).then(handleJsonResponse);
}

export function getDefaults() {
  return fetch(`${BASE_URL}/campaigns/defaults`, {
    method: 'GET',
    credentials: 'include',
  }).then(handleJsonResponse);
}

export function createCampaign(campaign) {
  return fetch(`${BASE_URL}/campaigns`, {
    method: 'POST',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(campaign),
  }).then(handleJsonResponse);
}

export function cancelCampaign(campaignId) {
  return fetch(`${BASE_URL}/campaigns/${campaignId}`, {
    method: 'DELETE',
    credentials: 'include',
  }).then(handleVoidResponse);
}

export function publishCampaign(campaignId) {
  return fetch(`${BASE_URL}/campaigns/${campaignId}/publish`, {
    method: 'POST',
    credentials: 'include',
  }).then(handleVoidResponse);
}

export function executeCampaign(campaignId) {
  return fetch(`${BASE_URL}/campaigns/${campaignId}/execute`, {
    method: 'POST',
    credentials: 'include',
  }).then(handleVoidResponse);
}

export function getCampaignItems(campaignId, filters) {
  return fetch(`${BASE_URL}/campaigns/${campaignId}/items?${toQueryParams(filters)}`, {
    method: 'GET',
    credentials: 'include',
  }).then(handleJsonResponse);
}

export function compareCampaigns(campaignId, otherCampaignId) {
  return fetch(`${BASE_URL}/campaigns/${campaignId}/compare/${otherCampaignId}`, {
    method: 'GET',
    credentials: 'include',
  }).then(handleJsonResponse);
}

export function getArchiveUrl(campaignId) {
  return `${BASE_URL}/campaigns/${campaignId}/archive`;
}

// Takes the same {search, page, size, sort} shape as getCampaignItems: the
// review table is paged, sorted AND searched server-side, exactly like the admin
// one.
export function getMyItems(filters) {
  return fetch(`${BASE_URL}/campaigns/published/my-items?${toQueryParams(filters)}`, {
    method: 'GET',
    credentials: 'include',
  }).then(handleJsonResponse);
}

export function getMySummary() {
  return fetch(`${BASE_URL}/campaigns/published/my-items/summary`, {
    method: 'GET',
    credentials: 'include',
  }).then(handleJsonResponse);
}

export function keepItem(itemId) {
  return fetch(`${BASE_URL}/items/${itemId}/keep`, {
    method: 'POST',
    credentials: 'include',
  }).then(handleVoidResponse);
}

// Answers 200 with {succeeded, failures: [{itemId, reason}]}: a bulk keep
// continues past individual failures, so the caller must inspect the outcomes
// instead of assuming everything was kept.
export function keepItems(itemIds) {
  return fetch(`${BASE_URL}/items/keep`, {
    method: 'POST',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({itemIds}),
  }).then(handleJsonResponse);
}

export function unkeepItem(itemId) {
  return fetch(`${BASE_URL}/items/${itemId}/unkeep`, {
    method: 'POST',
    credentials: 'include',
  }).then(handleVoidResponse);
}

// Same outcome contract as keepItems.
export function unkeepItems(itemIds) {
  return fetch(`${BASE_URL}/items/unkeep`, {
    method: 'POST',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({itemIds}),
  }).then(handleJsonResponse);
}

// Null, undefined and empty values are DROPPED rather than sent empty: every
// filter of both items endpoints is optional server-side, and a blank 'search'
// or 'state' must mean 'no filter', not 'match nothing'.
function toQueryParams(filters) {
  const formData = new FormData();
  Object.entries(filters || {}).forEach(([name, value]) => {
    if (value != null && value !== '') {
      formData.append(name, value);
    }
  });
  return new URLSearchParams(formData).toString();
}

// The module defines NO @ControllerAdvice, so a
// ResponseStatusException(BAD_REQUEST, 'cleanup.graceNotElapsed') reaches the
// browser wrapped in Spring's default error envelope
// ({"timestamp":...,"status":400,"message":"cleanup.graceNotElapsed",...}), not
// as a bare code. Every consumer of these errors goes through the shared
// $cleanupErrorLabel (see ../services.js), which localizes error.message as a
// BARE message code and falls back to a generic 'unexpected error' when it isn't
// one — so the code is unwrapped HERE, once, instead of in each caller. A
// non-JSON body is already the message; a body with no message falls back to the
// status.
function errorMessage(text, status) {
  if (text) {
    try {
      const body = JSON.parse(text);
      if (body?.message) {
        return body.message;
      }
    } catch (e) {
      // Not a JSON envelope: the raw body IS the message code
      return text;
    }
  }
  return text || `${status}`;
}

function rejectResponse(resp) {
  if (!resp) {
    return Promise.reject(new Error(''));
  }
  return resp.text().then(text => {
    throw new Error(errorMessage(text, resp.status));
  });
}

function handleJsonResponse(resp) {
  if (!resp || !resp.ok) {
    return rejectResponse(resp);
  }
  return resp.json();
}

function handleVoidResponse(resp) {
  if (!resp || !resp.ok) {
    return rejectResponse(resp);
  }
  return resp;
}
