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
  const formData = new FormData();
  Object.entries(filters || {}).forEach(([name, value]) => {
    if (value != null && value !== '') {
      formData.append(name, value);
    }
  });
  const params = new URLSearchParams(formData).toString();
  return fetch(`${BASE_URL}/campaigns/${campaignId}/items?${params}`, {
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

export function getMyItems(page, size) {
  return fetch(`${BASE_URL}/campaigns/published/my-items?page=${page || 0}&size=${size || 20}`, {
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

function handleJsonResponse(resp) {
  if (!resp || !resp.ok) {
    return resp.text().then(text => {
      throw new Error(text || resp.status);
    });
  }
  return resp.json();
}

function handleVoidResponse(resp) {
  if (!resp || !resp.ok) {
    return resp.text().then(text => {
      throw new Error(text || resp.status);
    });
  }
  return resp;
}
