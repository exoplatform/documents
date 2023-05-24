/*
 * This file is part of the Meeds project (https://meeds.io/).
 *
 * Copyright (C) 2020 - 2023 Meeds Association contact@meeds.io
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

export function getDocumentItems(itemsFilter, offset, limit, expand) {
  const formData = new FormData();
  if (itemsFilter) {
    Object.keys(itemsFilter).forEach(key => {
      const value = itemsFilter[key];
      if (value) {
        formData.append(key, value);
      }
    });
  }
  if (expand) {
    formData.append('expand', expand);
  }
  if (offset) {
    formData.append('offset', offset);
  }
  if (limit) {
    formData.append('limit', limit);
  }
  const params = new URLSearchParams(formData).toString();
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/documents?${params}`, {
    method: 'GET',
    credentials: 'include',
  }).then(resp => {
    if (!resp || !resp.ok) {
      throw new Error('Response code indicates a server error', resp);
    } else {
      return resp.json();
    }
  });

}

export function canAddDocument(spaceId) {
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/documents/canAddDocument?spaceId=${spaceId}`, {
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
export function getUserSettings() {
  const ownerId = eXo.env.portal.spaceIdentityId || eXo.env.portal.userIdentityId;
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/documents/settings/${ownerId}`, {
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
export function setUserDefaultView(view) {
  const ownerId = eXo.env.portal.spaceIdentityId || eXo.env.portal.userIdentityId;
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/documents/settings/${ownerId}/${view}`, {
    method: 'POST'
  }).then((resp) => {
    if (resp && resp.ok) {
      return resp.json();
    } else {
      throw new Error('Server indicates an error while sending request');
    }
  });   
}

export function getBreadCrumbs(folderId,ownerId,folderPath) {
  const formData = new FormData();

  if (folderId) {
    formData.append('folderId', folderId);
  }
  if (folderPath) {
    formData.append('folderPath', folderPath);
  }
  if (ownerId) {
    formData.append('ownerId', ownerId);
  }
  const params = new URLSearchParams(formData).toString();
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/documents/breadcrumb?${params}`, {
    method: 'GET',
    credentials: 'include',
  }).then(resp => {
    if (!resp || !resp.ok) {
      throw new Error('Response code indicates a server error', resp);
    } else {
      return resp.json();
    }
  });

}

export function getFullTreeData(ownerId, folderId) {
  const formData = new FormData();
  if (folderId) {
    formData.append('folderId', folderId);
  }
  if (ownerId) {
    formData.append('ownerId', ownerId);
  }
  const params = new URLSearchParams(formData).toString();
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/documents/fullTree?${params}`, {
    method: 'GET',
    credentials: 'include',
  }).then(resp => {
    if (!resp || !resp.ok) {
      throw new Error('Response code indicates a server error', resp);
    } else {
      return resp.json();
    }
  });
}

export function duplicateDocument(fileId,ownerId,prefixClone) {
  const formData = new FormData();

  if (fileId) {
    formData.append('fileId', fileId);
  }
  if (ownerId) {
    formData.append('ownerId', ownerId);
  }
  if (prefixClone) {
    formData.append('prefixClone', prefixClone);
  }
  const params = new URLSearchParams(formData).toString();
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/documents/duplicate?${params}`, {
    method: 'post',
    credentials: 'include',
  }).then(resp => {
    if (!resp || !resp.ok) {
      throw new Error('Response code indicates a server error', resp);
    } else {
      return resp.json();
    }
  });
}
export function saveVisibility(file) {
  const abFile ={
    'id': file.id,
    'path': file.path,
    'ownerId': file.ownerId,
    'creatorId': file.creatorId,
    'acl': file.acl,
  };
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/documents/permissions`, {
    method: 'post',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(abFile),
  }).then(resp => {
    if (!resp || !resp.ok) {
      throw new Error('Response code indicates a server error', resp);
    } else {
      return resp.ok;
    }
  });

}

export function renameDocument(ownerId,documentID,newName) {
  const formData = new FormData();
  if (ownerId) {
    formData.append('ownerId', ownerId);
  }
  if (documentID) {
    formData.append('documentID', documentID);
  }
  if (newName) {
    formData.append('newName', newName);
  }
  const params = new URLSearchParams(formData).toString();
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/documents/rename?${params}`, {
    credentials: 'include',
    method: 'PUT',
  }).then((resp) => {
    if (resp && resp.ok) {
      return resp.ok;
    } else {
      throw resp;
    }
  });
}

export function moveDocument(ownerId,documentID,destPath, conflictAction) {
  const formData = new FormData();
  if (ownerId) {
    formData.append('ownerId', ownerId);
  }
  if (documentID) {
    formData.append('documentID', documentID);
  }
  if (destPath) {
    formData.append('destPath', destPath);
  }
  if (conflictAction) {
    formData.append('conflictAction', conflictAction);
  }
  const params = new URLSearchParams(formData).toString();
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/documents/move?${params}`, {
    credentials: 'include',
    method: 'PUT',
  }).then((resp) => {
    if (resp && resp.ok) {
      return resp.ok;
    } else {
      throw resp;
    }
  });
}

export function createFolder(ownerId,parentid,folderPath,name) {
  const formData = new FormData();
  if (ownerId) {
    formData.append('ownerId', ownerId);
  }
  if (parentid) {
    formData.append('parentid', parentid);
  }
  if (folderPath) {
    formData.append('folderPath', folderPath);
  }
  if (name) {
    formData.append('name', name);
  }
  const params = new URLSearchParams(formData).toString();
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/documents/folder?${params}`, {
    credentials: 'include',
    method: 'POST',
  }).then((resp) => {
    if (resp && resp.ok) {
      return resp.json();
    } else {
      throw resp;
    }
  });
}
export function getNewName(ownerId,parentid,folderPath,name) {
  const formData = new FormData();
  if (ownerId) {
    formData.append('ownerId', ownerId);
  }
  if (parentid) {
    formData.append('parentid', parentid);
  }
  if (folderPath) {
    formData.append('folderPath', folderPath);
  }
  if (name) {
    formData.append('name', name);
  }
  const params = new URLSearchParams(formData).toString();
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/documents/newname?${params}`, {
    credentials: 'include',
    method: 'GET',
  }).then(resp => {
    if (!resp || !resp.ok) {
      throw new Error('Response code indicates a server error', resp);
    } else {
      return resp.text();
    }
  });
}

export function deleteDocument(documentPath, documentId, favorite, delay) {
  if (delay > 0) {
    localStorage.setItem('deletedDocument', documentId);
  }
  const formData = new FormData();
  if (delay) {
    formData.append('delay', delay);
  }
  if (documentPath) {
    formData.append('documentPath', documentPath.replaceAll('/', ':'));
  }
  if (favorite) {
    formData.append('favorite', favorite);
  }
  if (documentId) {
    formData.append('documentId', documentId);
  }

  const params = new URLSearchParams(formData).toString();
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/documents/${documentId}?${params}`, {
    credentials: 'include',
    method: 'DELETE'
  }).then((resp) => {
    if (resp && !resp.ok) {
      throw new Error('Error when deleting document');
    }
  });
}

export function undoDeleteDocument(documentId) {
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/documents/${documentId}/undoDelete`, {
    method: 'POST',
    credentials: 'include',
  }).then((resp) => {
    if (resp && resp.ok) {
      localStorage.removeItem('deletedDocument');
    } else {
      throw new Error('Error when undoing deleting document');
    }
  });
}

export function bulkDeleteDocuments(actionId,documents) {
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/documents/bulk/${actionId}`, {
    method: 'DELETE',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(documents),
  }).then((resp) => {
    if (resp && !resp.ok) {
      throw new Error('Error when deleting document');
    }
  });
}

export function bulkDownloadDocument(actionId,documents) {
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/documents/bulk/download/${actionId}`, {
    method: 'POST',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(documents),
  }).then((resp) => {
    if (resp && !resp.ok) {
      throw new Error('Error when deleting document');
    }
  });
}

export function updateDescription(ownerId,document) {
  const formData = new FormData();
  if (ownerId) {
    formData.append('ownerId', ownerId);
  }
  if (document.id) {
    formData.append('documentId', document.id);
  }
  if (document.description) {
    formData.append('description', document.description);
  }
  const params = new URLSearchParams(formData).toString();
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/documents/description?${params}`, {
    credentials: 'include',
    method: 'PUT',
  }).then((resp) => {
    if (resp && resp.ok) {
      return resp.ok;
    } else {
      throw resp;
    }
  }).catch(e => {
    throw new Error(`Error when trying to update document description ${e}`);
  });
}

export function createShortcut(documentID,destPath, conflictAction) {
  const formData = new FormData();
  if (documentID) {
    formData.append('documentID', documentID);
  }
  if (destPath) {
    formData.append('destPath', destPath);
  }
  if (conflictAction) {
    formData.append('conflictAction', conflictAction);
  }
  const params = new URLSearchParams(formData).toString();
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/documents/shortcut?${params}`, {
    credentials: 'include',
    method: 'POST',
  }).then((resp) => {
    if (resp && resp.ok) {
      return resp.ok;
    } else {
      throw resp;
    }
  });
}

export function getFileVersions(fileId) {
  const formData = new FormData();
  formData.append('fileId', fileId);
  const params = new URLSearchParams(formData).toString();
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/documents/versions?${params}`, {
    method: 'GET',
    credentials: 'include',
  }).then(resp => {
    if (!resp || !resp.ok) {
      throw new Error('Response code indicates a server error');
    } else {
      return resp.json();
    }
  });
}

export function updateVersionSummary(originFileId, versionId, summary) {
  const formData = new FormData();
  formData.append('versionId', versionId);
  formData.append('originFileId', originFileId);
  const params = new URLSearchParams(formData).toString();
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/documents/versions?${params}`, {
    method: 'PATCH',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      value: summary,
    }),
  }).then(resp => {
    if (!resp || !resp.ok) {
      throw new Error('Error while updating version summary');
    } else {
      return resp.json();
    }
  });
}

export function restoreVersion(versionId) {
  const formData = new FormData();
  formData.append('versionId', versionId);
  const params = new URLSearchParams(formData).toString();
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/documents/versions?${params}`, {
    method: 'PUT',
    credentials: 'include',
  }).then(resp => {
    if (!resp || !resp.ok) {
      throw new Error('Error while restoring version');
    } else {
      return resp.json();
    }
  });
  
}

export function getDownloadZip(actionId) {

  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/documents/bulk/download/${actionId}`, {
    credentials: 'include',
    method: 'GET',
  }).then((resp) => {
    if (resp && resp.ok) {
      return resp;
    } else { 
      throw resp;
    }
  });
}
export function cancelBulkAction(actionId) {

  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/documents/bulk/cancel/${actionId}`, {
    credentials: 'include',
    method: 'GET',
  }).then((resp) => {
    if (resp && resp.ok) {
      return resp;
    } else { 
      throw new Error('Error when cancelling action');
    }
  });
}

export function uploadNewFileVersion(nodeId, newContent) {
  const formData = new FormData();
  formData.append('nodeId', nodeId);
  const params = new URLSearchParams(formData).toString();
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/documents/createNewVersion?${params}`, {
    credentials: 'include',
    method: 'PUT',
    body: newContent,
  }).then((resp) => {
    if (resp && resp.ok) {
      return resp;
    } else {
      throw new Error('Error when creating new version');
    }
  });
}

export function bulkMoveDocuments(actionId, documents, ownerId, destPath) {
  const formData = new FormData();
  formData.append('actionId', actionId);
  formData.append('ownerId', ownerId);
  formData.append('destPath', destPath);
  const params = new URLSearchParams(formData).toString();
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/documents/bulk/move/${actionId}?${params}`, {
    method: 'PUT',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(documents),
  }).then((resp) => {
    if (resp && resp.ok) {
      return resp;
    } else {
      throw new Error('Error moving files');
    }
  });
}

export function getDocumentPublicAccess(nodeId, isFolder, password, expirationDate, isNew) {
  const formData = new FormData();
  if (nodeId) {
    formData.append('nodeId', nodeId);
  }
  if (isFolder) {
    formData.append('isFolder', isFolder);
  }
  if (password) {
    formData.append('password', password);
  }
  if (expirationDate) {
    formData.append('expirationDate', expirationDate);
  }
  if (isNew) {
    formData.append('isNew', isNew);
  }
  const params = new URLSearchParams(formData).toString();
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/documents/publicAccessLink?${params}`, {
    method: 'POST',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      isFolder: isFolder,
      password: password,
      expirationDate: expirationDate
    }),
  }).then(resp => {
    if (!resp?.ok) {
      throw resp;
    } else {
      return resp.text();
    }
  });
}

export function downloadPublicDocument(nodeId, password) {
  const formData = new FormData();
  if (nodeId) {
    formData.append('nodeId', nodeId);
  }
  if (password) {
    formData.append('password', password);
  }
  const params = new URLSearchParams(formData).toString();
  const url = `${window.location.origin}${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/documents/download?${params}`;
  return fetch(url, {
    headers: {
      'Content-Type': 'text/plain'
    },
    method: 'GET'
  }).then(response => {
    if (response?.ok) {
      return response;
    } else {
      throw response;
    }
  });
}