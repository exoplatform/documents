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

export function duplicateDocument(fileId,ownerId) {
  const formData = new FormData();

  if (fileId) {
    formData.append('fileId', fileId);
  }
  if (ownerId) {
    formData.append('ownerId', ownerId);
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