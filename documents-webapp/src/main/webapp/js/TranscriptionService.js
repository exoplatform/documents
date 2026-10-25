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
