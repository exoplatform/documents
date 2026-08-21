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

// PATCH on the campaign resource, not an /update or /rename sub-resource: it
// updates ATTRIBUTES, it triggers no action. Answers the FULL updated campaign,
// which the caller applies instead of refetching.
//
// PARTIAL update: 'updates' carries ONLY the attributes to change ({name},
// {graceDays}, or both). An attribute that is absent (or null) is left
// UNTOUCHED server-side, which is why the caller must not send the whole
// campaign back: that would overwrite fields nobody edited. Sending neither is
// refused with 'cleanup.nothingToUpdate', so the caller checks there is
// something to send before calling.
//
// Careful with graceDays: 0 is a MEANINGFUL value (a zero grace period elapses
// at publication), never 'empty' — a caller building 'updates' must test
// against null/undefined, not falsiness, or a deliberate 0 is silently dropped.
export function updateCampaign(campaignId, updates) {
  return fetch(`${BASE_URL}/campaigns/${campaignId}`, {
    method: 'PATCH',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(updates || {}),
  }).then(handleJsonResponse);
}

// CANCEL keeps the campaign, with everything its run had already found; DELETE
// removes it. They shared the DELETE verb until a real delete existed, which made
// one of them a trap.
export function cancelCampaign(campaignId) {
  return fetch(`${BASE_URL}/campaigns/${campaignId}/cancel`, {
    method: 'POST',
    credentials: 'include',
  }).then(handleVoidResponse);
}

// Drops a DRAFT, SIMULATED or CANCELLED campaign with its report, its scan units
// and its archive. Anything else answers 400: a COMPLETED campaign records an
// irreversible purge, and a running or published one must be cancelled first. The
// users' keep decisions live in JCR and are NOT removed with it.
export function deleteCampaign(campaignId) {
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

// Requeues the items a previous execution could not process, and answers the
// full updated campaign (back to EXECUTING) — applied by the caller instead of
// refetching, exactly like updateCampaign. NO body: WHICH failures are
// retryable is decided server-side, the client never selects them.
export function retryCampaign(campaignId) {
  return fetch(`${BASE_URL}/campaigns/${campaignId}/retry`, {
    method: 'POST',
    credentials: 'include',
  }).then(handleJsonResponse);
}

// Answers the execution failures of a finished campaign, folded server-side into
// [{reason, count, retryable}] — one entry per distinct message code, with the
// server's OWN retryability verdict. An empty array is not proof the run was
// clean: it is also what a campaign whose item rows the retention job already
// purged answers.
export function getCampaignFailures(campaignId) {
  return fetch(`${BASE_URL}/campaigns/${campaignId}/failures`, {
    method: 'GET',
    credentials: 'include',
  }).then(handleJsonResponse);
}

// Answers the SCAN failures of a campaign — the subtrees its dry run could never
// walk — folded server-side into the same [{reason, count, retryable}] shape as
// getCampaignFailures, so one console block renders both. An empty array is the
// normal answer: only a scan the server RECORDED as incomplete reports anything,
// and there is deliberately NO retry for these (the watchdog re-walked them while
// attempts remained, and a settled subtree would fail identically).
export function getCampaignScanFailures(campaignId) {
  return fetch(`${BASE_URL}/campaigns/${campaignId}/scan-failures`, {
    method: 'GET',
    credentials: 'include',
  }).then(handleJsonResponse);
}

// Answers the PER-UNIT progress of a campaign's dry run: the subtree state counts,
// the deepest walk attempt spent, and the subtrees in flight. Unlike the scan
// FAILURES above this is readable WHILE the scan runs, and that is the point — the
// node percentage comes from per-unit counts already persisted, so an interrupted
// run whose nodes were all counted reads 100% while a unit is still being
// re-walked from its checkpoint. 'scanComplete' is the honest completion signal.
export function getCampaignScanUnits(campaignId) {
  return fetch(`${BASE_URL}/campaigns/${campaignId}/scan-units`, {
    method: 'GET',
    credentials: 'include',
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
      // A parsed envelope without a message carries no code: fall back to the
      // status rather than handing the whole envelope over as one
      return JSON.parse(text)?.message || `${status}`;
    } catch (e) {
      // Not a JSON envelope: the raw body IS the message code
      return text;
    }
  }
  return `${status}`;
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
