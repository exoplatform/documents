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

const TRANSCRIPTIONS_URL = '/documents-portlet/rest/transcriptions';

/**
 * Reads the transcription state of a file.
 *
 * @param {string} documentId the file's id
 * @returns {Promise<Object>} the state: available, status, progress, error,
 *          language, durationSeconds, updated, hasSegments, hasChapters
 */
export function getState(documentId) {
  return fetch(`${TRANSCRIPTIONS_URL}/${encodeURIComponent(documentId)}`, {
    method: 'GET',
    credentials: 'include',
  }).then(resp => {
    if (!resp?.ok) {
      throw new Error(`Error ${resp?.status} when reading the transcription state of '${documentId}'`);
    }
    return resp.json();
  });
}

/**
 * Reads the transcript of a transcribed file, as plain text.
 *
 * @param {string} documentId the file's id
 * @returns {Promise<string>} the transcript
 */
export function getText(documentId) {
  return fetch(`${TRANSCRIPTIONS_URL}/${encodeURIComponent(documentId)}/text`, {
    method: 'GET',
    credentials: 'include',
  }).then(resp => {
    if (!resp?.ok) {
      throw new Error(`Error ${resp?.status} when reading the transcript of '${documentId}'`);
    }
    return resp.text();
  });
}

/**
 * Reads the timed segments of a transcribed file.
 *
 * @param {string} documentId the file's id
 * @returns {Promise<Array>} the segments, each with start and end in seconds
 *          and text
 */
export function getSegments(documentId) {
  return fetch(`${TRANSCRIPTIONS_URL}/${encodeURIComponent(documentId)}/segments`, {
    method: 'GET',
    credentials: 'include',
  }).then(resp => {
    if (!resp?.ok) {
      throw new Error(`Error ${resp?.status} when reading the transcript segments of '${documentId}'`);
    }
    return resp.json();
  });
}

/**
 * Reads the chapters a transcribed file's transcript is grouped into.
 *
 * @param {string} documentId the file's id
 * @returns {Promise<Array>} the chapters, each with start in seconds, the
 *          index of its first segment, title and summary
 */
export function getChapters(documentId) {
  return fetch(`${TRANSCRIPTIONS_URL}/${encodeURIComponent(documentId)}/chapters`, {
    method: 'GET',
    credentials: 'include',
  }).then(resp => {
    if (!resp?.ok) {
      throw new Error(`Error ${resp?.status} when reading the transcript chapters of '${documentId}'`);
    }
    return resp.json();
  });
}

/**
 * Asks for the chapters of a transcribed file being read, which a transcript
 * done before they were switched on gets the first time it is opened.
 *
 * @param {string} documentId the file's id
 * @returns {Promise<boolean>} resolved with true when the chapters are coming
 *          (the server answered 202), false when none are to be made (204)
 */
export function requestChapters(documentId) {
  return fetch(`${TRANSCRIPTIONS_URL}/${encodeURIComponent(documentId)}/chapters`, {
    method: 'POST',
    credentials: 'include',
  }).then(resp => {
    if (!resp?.ok) {
      throw new Error(`Error ${resp?.status} when asking for the transcript chapters of '${documentId}'`);
    }
    return resp.status === 202;
  });
}

/**
 * Asks to transcribe a file again.
 *
 * @param {string} documentId the file's id
 * @returns {Promise} resolved once the request is accepted
 */
export function retry(documentId) {
  return fetch(`${TRANSCRIPTIONS_URL}/${encodeURIComponent(documentId)}/retry`, {
    method: 'POST',
    credentials: 'include',
  }).then(resp => {
    if (!resp?.ok) {
      throw new Error(`Error ${resp?.status} when asking to transcribe '${documentId}' again`);
    }
  });
}

/**
 * Reads the speakers of a file's transcript whom someone named, and whether
 * the current user may name them (EXO-92123).
 *
 * @param {string} documentId the file's UUID
 * @returns {Promise<{canEdit: boolean, speakers: Array<object>}>} the named speakers
 */
export function getSpeakers(documentId) {
  return fetch(`${TRANSCRIPTIONS_URL}/${encodeURIComponent(documentId)}/speakers`, {
    method: 'GET',
    credentials: 'include',
  }).then(resp => {
    if (!resp?.ok) {
      throw new Error(`Error ${resp?.status} when reading the speakers of '${documentId}'`);
    }
    return resp.json();
  });
}

/**
 * Says which person a speaker of a file's transcript is (EXO-92123).
 *
 * @param {string} documentId the file's UUID
 * @param {string} speaker the speaker's number
 * @param {string|number} identityId the person's identity id
 * @returns {Promise<void>} resolved once named; rejected with the refusal's
 *          message code
 */
export function setSpeaker(documentId, speaker, identityId) {
  return updateSpeaker(documentId, speaker, 'PUT', `?identityId=${encodeURIComponent(identityId)}`);
}

/**
 * Says that a speaker of a file's transcript is a guest with no account,
 * known by a plain name (EXO-92123).
 *
 * @param {string} documentId the file's UUID
 * @param {string} speaker the speaker's number
 * @param {string} name the guest's name
 * @returns {Promise<void>} resolved once named; rejected with the refusal's
 *          message code
 */
export function setGuestSpeaker(documentId, speaker, name) {
  return updateSpeaker(documentId, speaker, 'PUT', `?name=${encodeURIComponent(name)}`);
}

/**
 * Says that a speaker of a file's transcript is nobody named any more
 * (EXO-92123).
 *
 * @param {string} documentId the file's UUID
 * @param {string} speaker the speaker's number
 * @returns {Promise<void>} resolved once unnamed
 */
export function unsetSpeaker(documentId, speaker) {
  return updateSpeaker(documentId, speaker, 'DELETE', '');
}

/**
 * Sends a change of a speaker, rejecting with the message code the server
 * answers with when it refuses it.
 *
 * @param {string} documentId the file's UUID
 * @param {string} speaker the speaker's number
 * @param {string} method PUT or DELETE
 * @param {string} query the query string, empty for none
 * @returns {Promise<void>} resolved once done
 */
function updateSpeaker(documentId, speaker, method, query) {
  return fetch(`${TRANSCRIPTIONS_URL}/${encodeURIComponent(documentId)}/speakers/${encodeURIComponent(speaker)}${query}`, {
    method,
    credentials: 'include',
  }).then(resp => {
    if (!resp?.ok) {
      return resp.json()
        .catch(() => ({}))
        .then(body => {
          throw new Error(body?.message || `Error ${resp?.status} when changing a speaker of '${documentId}'`);
        });
    }
  });
}
