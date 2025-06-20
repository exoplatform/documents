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
export const DB_DIRECTORY_KEY = 'favorite';
export const DB_LINK_TYPE_KEY = 'linkType';
export const DB_LOCAL_FOLDER_KEY = 'localFolderPath';
export const DB_USER_LOCAL_FOLDER_KEY = 'userLocalFolderPath';
export const DB_SPACE_LOCAL_FOLDER_KEY = 'spaceLocalFolderPath';
export const DB_LOCAL_VALUES = {};

/* Link Type */

export async function getLinkType() {
  return getValue(DB_LINK_TYPE_KEY);
}

export async function setLinkType(link) {
  setValue(DB_LINK_TYPE_KEY, link);
}

/* Local Folder Path */

export async function getLocalFolderPath() {
  return getValue(DB_LOCAL_FOLDER_KEY);
}

export async function setLocalFolderPath(path) {
  setValue(DB_LOCAL_FOLDER_KEY, path);
}

export async function removeLocalFolderPath() {
  removeValue(DB_LOCAL_FOLDER_KEY);
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

export async function removeDirectoryHandle() {
  removeValue(DB_DIRECTORY_KEY);
}

export async function setDirectoryHandle(handle) {
  setValue(DB_DIRECTORY_KEY, handle);
}

export async function getDirectoryHandle() {
  const directoryHandle = await getValue(DB_DIRECTORY_KEY);
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

export async function isDirectoryHandleExists() {
  if (await isDatabaseExists()) {
    return !!(await getValue(DB_DIRECTORY_KEY));
  } else {
    return false;
  }
}

/* File Item Operations */

export async function saveFile(file) {
  const handle = await openDirectoryHandle();

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
    const fileHandle = await createFileHandle(file);
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
    const storedFile = await getFile(file.id);
    if (storedFile) {
      const directoryHandle = await getParentDirectoryHandle(storedFile);
      if (directoryHandle) {
        if (storedFile?.handle?.remove) {
          await storedFile.handle.remove();
        } else {
          await directoryHandle.removeEntry(storedFile.name);
        }
        await removeFileFromDB(storedFile.id);
      }
    }
  } catch (e) {
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

export async function getFiles() {
  const database = await getDatabase();
  return new Promise(resolve => {
    const files = [];
    const transaction = database.transaction([DB_FILES_OBJECT_STORE],'readonly');
    transaction.oncomplete = () => resolve(files);
    transaction.objectStore(DB_FILES_OBJECT_STORE).openCursor().onsuccess = e => {
      const cursor = e.target.result;
      if (cursor) {
        const file = cursor.value;
        file.handle?.getFile?.()
          ?.then?.(() => files.push(file))
          ?.catch?.(() => removeFileFromDB(file.id));
        cursor.continue();
      }
    };
  });
}

/* Utils */

export async function getValue(paramName) {
  if (!DB_LOCAL_VALUES[paramName]) {
    const database = await getDatabase();
    DB_LOCAL_VALUES[paramName] = await new Promise(resolve => {
      const transaction = database.transaction([DB_OBJECT_STORE], 'readonly');
      const request = transaction.objectStore(DB_OBJECT_STORE).get(paramName);
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => resolve(null);
    });
  }
  return DB_LOCAL_VALUES[paramName];
}

export async function setValue(paramName, paramValue) {
  const database = await getDatabase();
  return new Promise(resolve => {
    const transaction = database.transaction([DB_OBJECT_STORE], 'readwrite');
    transaction.oncomplete = () => {
      DB_LOCAL_VALUES[paramName] = paramValue;
      resolve();
    };
    transaction.objectStore(DB_OBJECT_STORE).put(paramValue, paramName);
  });
  return paramValue;
}

export async function removeValue(paramName) {
  const database = await getDatabase();
  return new Promise(resolve => {
    const transaction = database.transaction([DB_OBJECT_STORE], 'readwrite');
    transaction.oncomplete =  () => {
      DB_LOCAL_VALUES[paramName] = null;
      resolve();
    };
    transaction.objectStore(DB_OBJECT_STORE).delete(paramName);
  });
}

/* Files DB Operations */

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

async function createFileHandle(file) {
  let parentDirectoryHandle = await getParentDirectoryHandle(file)
  return parentDirectoryHandle.getFileHandle(file.name, {
    create: true
  });
}

async function getParentDirectoryHandle(file) {
  let parentDirectoryHandle = await getDirectoryHandle()
  if (!parentDirectoryHandle) {
    return null;
  }
  if (file.path?.startsWith?.('/Groups/spaces') && file.path?.includes?.('/Documents/')) {
    const path = file.path.substring(file.path.indexOf('/Documents/'), file.path.length);
    parentDirectoryHandle = await createFolderHandle(parentDirectoryHandle, [await getSpaceFolderName()]);

    const paths = path.split('/').filter(p => !!p?.length);
    paths.splice(paths.length - 1, 1);
    if (paths.length) {
      parentDirectoryHandle = await createFolderHandle(parentDirectoryHandle, paths);
    }
  } else if (file.path?.startsWith?.('/Users') && file.path?.includes?.('/Private/')) {
    const path = file.path.substring(file.path.indexOf('/Private/') + '/Private/'.length, file.path.length);
    parentDirectoryHandle = await createFolderHandle(parentDirectoryHandle, [await getUserFolderName()]);

    const paths = path.split('/').filter(p => !!p?.length);
    paths.splice(paths.length - 1, 1);
    if (paths.length) {
      parentDirectoryHandle = await createFolderHandle(parentDirectoryHandle, paths);
    }
  }
  return parentDirectoryHandle;
}

async function createFolderHandle(handle, paths) {
  if (paths.length) {
    const folderPath = paths.pop();
    if (folderPath?.length) {
      handle = await handle.getDirectoryHandle(folderPath, {
        create: true
      });
    }
    return await createFolderHandle(handle, paths);
  }
  return handle;
}

async function getUserFolderName() {
  const key = `${DB_USER_LOCAL_FOLDER_KEY}-${eXo.env.portal.userIdentityId}`;
  const folderName = await getValue(key);
  if (folderName) {
    return folderName;
  } else {
    return await setValue(key, Vue.prototype?.$currentUserIdentity?.profile?.fullname || eXo.env.portal.userName);
  }
}

async function getSpaceFolderName() {
  const key = `${DB_USER_LOCAL_FOLDER_KEY}-${eXo.env.portal.spaceId}`;
  const folderName = await getValue(key);
  if (folderName) {
    return folderName;
  } else {
    return await setValue(key, eXo.env.portal.spaceDisplayName);
  }
}

/* Database Operations */
let localDatabase;
async function isDatabaseExists() {
  const dbs = await window.indexedDB.databases()
  return !!dbs?.find?.(db => db.name === DB_NAME);
}

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
