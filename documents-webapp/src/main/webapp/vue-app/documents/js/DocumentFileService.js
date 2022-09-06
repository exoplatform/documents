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
      return resp.json();
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
    }
  }).catch(e => {
    throw new Error(`Error renaming document ${e}`);
  });
}

export function moveDocument(ownerId,documentID,destPath) {
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
  const params = new URLSearchParams(formData).toString();
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/documents/move?${params}`, {
    credentials: 'include',
    method: 'PUT',
  }).then((resp) => {
    if (resp && resp.ok) {
      return resp.ok;
    }
  }).catch(e => {
    throw new Error(`Error renaming document ${e}`);
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
      return resp.ok;
    }
  }).catch(e => {
    throw new Error(`Error creating folder ${e}`);
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
    }
  }).catch(e => {
    throw new Error(`Error renaming document ${e}`);
  });
}
