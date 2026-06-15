/*
 * Copyright (C) 2024 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

export function injectSortTooltip(tooltipName, markerClass) {
    const elements = document.getElementsByClassName(markerClass);
    elements?.forEach?.(element => {
        element.getElementsByTagName('i').item(0);
        element.title = tooltipName;
    });
}

export function getSize(size) {
    if (size === 0) {
        return {value: 0, unit: 'B'};
    }
    const m = size > 0 ? 1 : -1;
    const k = Math.floor((Math.log2(Math.abs(size)) / 10));
    let rank = `B`;
    if (k !== 0) {
        rank = `${'KMGT'[k - 1]}B`;
    }
    const count = (Math.abs(size) / Math.pow(1024, k)).toFixed(2);
    return {value: Math.round(count * m), unit: rank};
}


export function getThumbnailUrl(file, size, lastUpdated) {
    const formData = new FormData();
    formData.append('size', size);
    if (lastUpdated && lastUpdated > 0) {
        formData.append('lastModified', lastUpdated);
    }
    const params = new URLSearchParams(formData).toString();
    const fileId = file.sourceID || file.id;
    if (file?.readable) {
        return `${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/documents/officeThumbnail/${fileId}?${params}`;
    }
    if (file?.mimeType?.includes('image/') || file?.mimetype?.includes('image/')) {
        return `${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/documents/imageThumbnail/${fileId}?${params}`;
    }
    if (file?.mimeType?.includes('video/') || file?.mimetype?.includes('video/')) {
        return `${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/documents/videoThumbnail/${fileId}?${params}`;
    }
    return null;
}


export function getDownloadUrl(id, lastUpdated) {
    const formData = new FormData();
    if (lastUpdated && lastUpdated > 0) {
        formData.append('lastModified', lastUpdated);
    }
    const params = new URLSearchParams(formData).toString();
    return `/${eXo.env.portal.rest}/v1/documents/content/${id}?${params}`;

}

export function getParentFolderUrl(file) {
  if (file && file.id) {
    let folderPath = window.location.pathname;
    const pathParts = file.path.split('/');
    const spaceIndex = pathParts.indexOf('spaces');
    if (spaceIndex !== -1) {
      if (pathParts[spaceIndex + 2] === 'Documents') {
        pathParts[spaceIndex + 2] = 'documents';
      }
      folderPath = pathParts.slice(spaceIndex + 1, pathParts.length - 1).join('/');
      folderPath = `${eXo.env.portal.context}/g/:spaces:${folderPath}`;
    } else if (pathParts.indexOf('Users') !== -1) {
      const parentIndex = pathParts.indexOf('Private');
      if (parentIndex !== -1) {
        folderPath = pathParts.slice(parentIndex, pathParts.length - 1).join('/');
        folderPath = `${eXo.env.portal.defaultPath}/dashboard/drive/${folderPath}`;
      }
    }
    return folderPath;
  }
}

export function getEditorUrl(file, mode) {
  const fileId = file.sourceID? file.sourceID: file.id;
  let url = `${eXo.env.portal.context}/${eXo.env.portal.portalName}/oeditor?docId=${fileId}`;   
  if (mode) {
    url += `&mode=${mode}`;
  }
  url += `&backTo=${getParentFolderUrl(file)}`;
  return url;
}

export function getViewType(appId) {
    return localStorage.getItem(`documents-stored-view-type-${appId}`) || 'listView';
}

export function setViewType(viewType, appId) {
    localStorage.setItem(`documents-stored-view-type-${appId}`, viewType);
}

