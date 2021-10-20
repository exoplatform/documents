export function getFiles(ownerId, parentFolderId, listingType, query, expand, offset, limit) {
  const formData = new FormData();
  if (ownerId) {
    formData.append('ownerId', ownerId);
  }
  if (parentFolderId) {
    formData.append('parentFolderId', parentFolderId);
  }
  if (listingType) {
    formData.append('listingType', listingType);
  }
  if (query) {
    formData.append('query', query);
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