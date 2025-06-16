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

export const DB_NAME = 'favoriteDocuments';
export const DB_VERSION = '1';
export const DB_OBJECT_STORE = 'handles';
export const DB_FILES_OBJECT_STORE = 'files';
export const DB_KEY = 'favorite';

/* File Item Operations */
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
    await addFileToDB(file, fileHandle);
  }
}

export async function removeFile(file) {
  try {
    const handle = await getDirectoryHandle();
    if (handle) {
      await handle.removeEntry(`${file.id}-${file.name}`);
      await removeFileFromDB(file.id);
    }
  } catch (e) {
    console.debug(`File '${file.name}' not synchronized locally`, e);
  }
}

export async function getFiles() {
  const database = await getDatabase();
  return new Promise(resolve => {
    const files = [];
    const transaction = database.transaction([DB_FILES_OBJECT_STORE],'readonly');
    transaction.oncomplete = () => resolve(files);
    transaction.objectStore(DB_FILES_OBJECT_STORE).openCursor().onsuccess = e => {
      const cursor = e.target.result;
      if (cursor) {
        files.push(cursor.value);
        cursor.continue();
      }
    };
  });
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

async function removeFileFromDB(fileId) {
  const database = await getDatabase();
  return new Promise(resolve => {
    const transaction = database.transaction([DB_FILES_OBJECT_STORE], 'readwrite');
    transaction.oncomplete = resolve;
    transaction.objectStore(DB_FILES_OBJECT_STORE).delete(fileId);
  });
}

async function addFileToDB(file, fileHandle) {
  const database = await getDatabase();
  return new Promise(resolve => {
    const transaction = database.transaction([DB_FILES_OBJECT_STORE], 'readwrite');
    transaction.oncomplete = resolve;
    transaction.objectStore(DB_FILES_OBJECT_STORE).put({
      id: file.id,
      name: file.name,
      path: file.path,
      mimeType: file.mimeType,
      datasource: file.datasource,
      canEdit: file.acl?.canEdit,
      size: file.size,
      versionNumber: file.versionNumber,
      handle: fileHandle,
    }, file.id);
  });
}

/* Directory Handle Operations */
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

let directoryHandle;
export async function removeDirectoryHandle() {
  const database = await getDatabase();
  return new Promise(resolve => {
    const transaction = database.transaction([DB_OBJECT_STORE], 'readwrite');
    transaction.oncomplete =  () => {
      directoryHandle = null;
      resolve();
    };
    transaction.objectStore(DB_OBJECT_STORE).delete(DB_KEY);
  });
}

export async function setDirectoryHandle(handle) {
  const database = await getDatabase();
  return new Promise(resolve => {
    const transaction = database.transaction([DB_OBJECT_STORE], 'readwrite');
    transaction.oncomplete = () => {
      directoryHandle = handle;
      resolve();
    };
    transaction.objectStore(DB_OBJECT_STORE).put(handle, DB_KEY);
  });
}

export async function getDirectoryHandle() {
  if (!directoryHandle) {
    const database = await getDatabase();
    directoryHandle = await new Promise(resolve => {
      const transaction = database.transaction([DB_OBJECT_STORE], 'readonly');
      const request = transaction.objectStore(DB_OBJECT_STORE).get(DB_KEY);
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => resolve(null);
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

/* Database Operations */
let localDatabase;
async function getDatabase() {
  if (!localDatabase) {
    localDatabase = await new Promise((resolve, reject) => {
      const request = window.indexedDB.open(DB_NAME, DB_VERSION);
      request.onerror = e => reject(String(e));
      request.onsuccess = e => resolve(e.target.result);
      request.onupgradeneeded = e => {
        e.target.result.createObjectStore(DB_OBJECT_STORE);
        e.target.result.createObjectStore(DB_FILES_OBJECT_STORE);
      };
    });
  }
  return localDatabase;
}
