/*
 * Copyright (C) 2025 eXo Platform SAS.
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

export const DB_NAME = 'FavoriteDocuments';
export const DB_VERSION = '2';
export const DB_FILES_OBJECT_STORE = 'files';
export const DB_FILE_BLOBS_OBJECT_STORE = 'file-blobs';

/* File Item Operations */

export async function downloadFavorites(offset, limit, size) {
  const favoriteData = await Vue.prototype.$favoriteService.getFavorites(offset || 0, limit || 10, !offset || !size, 'file');
  const items = favoriteData?.favoritesItem || [];
  size = favoriteData?.size || size || 0;
  await Promise.all(items.map(item => saveFile(item.objectId)));
  if (size && size > favoriteData.offset + favoriteData.limit) {
    await downloadFavorites(favoriteData.offset + favoriteData.limit, favoriteData.limit, size);
  }
}

export async function saveFile(fileId) {
  const file = await retrieveFileById(fileId);
  const fileAttachment = await Vue.prototype.$attachmentService.getAttachmentById(file.id);
  if (fileAttachment?.downloadUrl?.length) {
    const downloadUrl = fileAttachment.downloadUrl.replaceAll('%', '%25').replaceAll('[', '%5b').replaceAll(']', '%5d').replaceAll('+', '%2B');
    const blob = await fetch(downloadUrl, {
      credentials: 'include',
    }).then(resp => {
      if (resp?.ok) {
        return resp.blob();
      } else {
        throw new Error(`Unable to download file with URL '${downloadUrl}', response status: ${resp.status}`);
      }
    });
    await addFileToDB(file, blob);
  }
}

export function retrieveFileById(fileId) {
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/documents/${fileId}`)
    .then(resp => {
      if (resp?.ok) {
        return resp.json();
      } else {
        throw new Error('Server indicates an error while sending request');
      }
    });   
}

export async function removeFile(file) {
  try {
    await removeFileFromDB(file.id);
  } catch (e) {
    // eslint-disable-next-line no-console
    console.debug(`File '${file.name}' not synchronized locally`, e);
  }
}

export async function getFile(fileId) {
  const database = await getDatabase();
  return new Promise(resolve => {
    const transaction = database.transaction([DB_FILES_OBJECT_STORE], 'readonly');
    const request = transaction.objectStore(DB_FILES_OBJECT_STORE).get(fileId);
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => resolve(null);
  });
}

export async function getFileBlob(fileId) {
  const database = await getDatabase();
  return new Promise(resolve => {
    const transaction = database.transaction([DB_FILE_BLOBS_OBJECT_STORE], 'readonly');
    const request = transaction.objectStore(DB_FILE_BLOBS_OBJECT_STORE).get(fileId);
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => resolve(null);
  });
}

export async function getFiles() {
  const database = await getDatabase();
  return new Promise(resolve => {
    const files = [];
    const transaction = database.transaction([DB_FILES_OBJECT_STORE], 'readonly');
    transaction.oncomplete = () => resolve(files);
    transaction.objectStore(DB_FILES_OBJECT_STORE).openCursor().onsuccess = e => {
      const cursor = e.target.result;
      if (cursor?.value) {
        files.push(cursor.value);
      }
      cursor?.continue?.();
    };
  });
}

/* Files DB Operations */

async function removeFileFromDB(fileId) {
  const database = await getDatabase();
  return new Promise(resolve => {
    const transaction = database.transaction([DB_FILES_OBJECT_STORE, DB_FILE_BLOBS_OBJECT_STORE], 'readwrite');
    transaction.oncomplete = resolve;
    transaction.objectStore(DB_FILES_OBJECT_STORE).delete(fileId);
    transaction.objectStore(DB_FILE_BLOBS_OBJECT_STORE).delete(fileId);
  });
}

async function addFileToDB(file, blob) {
  const database = await getDatabase();
  return new Promise(resolve => {
    const transaction = database.transaction([DB_FILES_OBJECT_STORE, DB_FILE_BLOBS_OBJECT_STORE], 'readwrite');
    transaction.oncomplete = resolve;
    const fileToStore = {
      ...file,
      downloadTime: Date.now(),
    };
    transaction.objectStore(DB_FILES_OBJECT_STORE).put(fileToStore, file.id);
    transaction.objectStore(DB_FILE_BLOBS_OBJECT_STORE).put(blob, file.id);
  });
}

/* Database Operations */
let localDatabase;
export async function isDatabaseExists() {
  const dbs = await window.indexedDB.databases();
  return !!dbs?.find?.(db => db.name === DB_NAME);
}

export async function deleteDatabase() {
  if (await isDatabaseExists()) {
    const db = retrieveDatabase('1');
    localDatabase = null;
    if (db) {
      return new Promise((resolve, reject) => {
        const request = window.indexedDB.deleteDatabase(DB_NAME);
        request.onerror = e => reject(String(e));
        request.onsuccess = resolve;
      });
    }
  }
}

export function createDatabase() {
  return retrieveDatabase();
}

async function getDatabase() {
  if (!(await isDatabaseExists())) {
    return null;
  } else if (!localDatabase) {
    localDatabase = await retrieveDatabase();
  }
  return localDatabase;
}

async function retrieveDatabase(version) {
  const request = await window.indexedDB.open(DB_NAME, version || DB_VERSION);
  return new Promise((resolve, reject) => {
    request.onerror = e => reject(String(e));
    request.onsuccess = e => resolve(e.target.result);
    request.onupgradeneeded = e => {
      try {
        if (version === '1') {
          e.target.result.deleteObjectStore(DB_FILE_BLOBS_OBJECT_STORE);
          e.target.result.deleteObjectStore(DB_FILES_OBJECT_STORE);
        } else {
          e.target.result.createObjectStore(DB_FILE_BLOBS_OBJECT_STORE);
          e.target.result.createObjectStore(DB_FILES_OBJECT_STORE);
        }
      } catch (e) {
        console.debug('Error upgrading database version', e);
      }
    };
  });
}
