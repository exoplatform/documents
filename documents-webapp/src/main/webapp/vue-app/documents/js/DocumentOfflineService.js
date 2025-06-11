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

const DB_NAME = 'favoriteDocuments';
const DB_VERSION = '1';
const DB_OBJECT_STORE = 'handles';
const DB_KEY = 'favorite';
let directoryHandle;

export async function removeFile(file) {
  try {
    const handle = await getDirectoryHandle();
    if (handle) {
      await handle.removeEntry(`${file.id}-${file.name}`);
    }
  } catch (e) {
    console.debug(`File '${file.name}' not synchronized locally`, e);
  }
}

export async function saveFile(file) {
  const handle = await openDirectoryHandle();

  const fileAttachment = await Vue.prototype.$attachmentService.getAttachmentById(file.id);
  if (fileAttachment?.downloadUrl?.length) {
    const downloadUrl = fileAttachment.downloadUrl.replaceAll('%', '%25').replaceAll('+', '%2B');
    const blob = await fetch(downloadUrl, {
      credentials: 'include',
    }).then(resp => {
      if (resp?.ok) {
        return resp.blob();
      } else {
        throw new Error(`Unable to download file with URL '${downloadUrl}', response status: ${resp.status}`);
      }
    });
    const fileHandle = await handle.getFileHandle(`${file.id}-${file.name}`, {
      create: true
    });
    const writable = await fileHandle.createWritable();
    await writable.write({
      type: 'write',
      data: blob,
      position: 0,
    });
    await writable.close();
  }
}

export async function openDirectoryHandle() {
  let handle = await getDirectoryHandle();
  if (!handle) {
    handle = await window.showDirectoryPicker({
      id: 'FavoriteDocuments',
      mode: 'readwrite',
      startIn: 'documents',
    });
    if (handle) {
      await setDirectoryHandle(handle);
    }
  }
  return handle;
}

export async function getDirectoryHandle() {
  if (!directoryHandle) {
    directoryHandle = await new Promise(resolve => {
      const transaction = localDatabase.transaction([DB_OBJECT_STORE], 'readonly');
      const request = transaction.objectStore(DB_OBJECT_STORE).get(DB_KEY);
      request.onsuccess = () => resolve(request.result);
    });
  }
  if (directoryHandle?.queryPermission) {
    const response = await directoryHandle.queryPermission({
      mode: 'readwrite',
    });
    if (response !== 'granted') {
      await removeDirectoryHandle();
      directoryHandle = null;
    }
  }
  return directoryHandle;
}

export function removeDirectoryHandle() {
  return new Promise(resolve => {
    const transaction = localDatabase.transaction([DB_OBJECT_STORE], 'readwrite');
    transaction.oncomplete = resolve;
    transaction.objectStore(DB_OBJECT_STORE).delete(DB_KEY);
  });
}

export function setDirectoryHandle(handle) {
  return new Promise(resolve => {
    const transaction = localDatabase.transaction([DB_OBJECT_STORE], 'readwrite');
    transaction.oncomplete = resolve;
    transaction.objectStore(DB_OBJECT_STORE).put(handle, DB_KEY);
  });
}

function getDatabase() {
  return new Promise((resolve, reject) => {
    const request = window.indexedDB.open(DB_NAME, DB_VERSION);
    request.onerror = e => reject(String(e));
    request.onsuccess = e => resolve(e.target.result);
    request.onupgradeneeded = e => e.target.result.createObjectStore(DB_OBJECT_STORE);
    return request;
  });
}

const localDatabase = await getDatabase();
