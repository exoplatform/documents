/*
 * Copyright (C) 2026 eXo Platform SAS.
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

/**
 * The sharing sign a listed file or folder shows after its name.
 *
 * Inside a folder, a row shows a sign only when its sharing differs from the
 * opened folder's: the listing gives each row its own access, and the opened
 * folder's access is read once per folder (`FOLDER_MODE`). In the recent view
 * and in search results, rows come from many folders, so a row shows a sign
 * only for a public link or a public access (`EXPOSURE_MODE`).
 *
 * The collaborators the listing returns are not all sharings: it leaves the
 * drive's owner out, but returns the administrators group to its members, a
 * creator's read entry once the node's access was saved, and the grant a
 * saved folder gives its creator, which the rows created inside inherit.
 * The rules below set those grants aside on the client. The server-side fix
 * of the two creator grants, and a « shared beyond default » flag computed by
 * the server, are tracked by the Maintenance task EXO-92881.
 */

export const FOLDER_MODE = 'folder';
export const EXPOSURE_MODE = 'exposure';

export const SIGNS = {
  anyone: {
    icon: 'fas fa-globe',
    labelKey: 'documents.label.sharing.anyone',
  },
  publicLink: {
    icon: 'fas fa-link',
    labelKey: 'documents.label.sharing.publicLink',
  },
  restricted: {
    icon: 'fas fa-user-lock',
    labelKey: 'documents.label.sharing.restricted',
  },
  people: {
    icon: 'fas fa-user-friends',
    labelKey: 'documents.label.sharing.people',
  },
};

const PUBLIC_CHOICES = ['ANYONE', 'COLLABORATORS_AND_PUBLIC_ACCESS'];

/**
 * The group the storage grants on every node of a space drive whose access
 * is saved (`JCRDocumentFileStorage#updatePermissions`), and which the
 * listing returns as a collaborator to its members only: never a sharing.
 */
const ADMINISTRATORS_GROUP = '/platform/administrators';

/**
 * Tells whether an access is open to the public.
 *
 * @param {object} acl the access of a node, as the documents REST answers it
 * @returns {boolean} true for a public access, with or without a public link
 */
function isPublic(acl) {
  return PUBLIC_CHOICES.includes(acl?.visibilityChoice);
}

/**
 * Gives the key of an identity, the same for every node that names it.
 *
 * @param {string} providerId the identity's provider: 'organization' for a
 *          user, 'space' or 'group'
 * @param {string} remoteId the user name, space pretty name or group id
 * @returns {string} the key
 */
function identityKey(providerId, remoteId) {
  return `${providerId}:${remoteId}`;
}

/**
 * Lists the keys of the identities a node's access names as collaborators,
 * except the administrators group the storage grants by itself.
 *
 * @param {object} node a listed row or a folder, with its `acl`
 * @returns {Array<string>} the identity keys, empty when there is none
 */
function collaboratorKeys(node) {
  return (node?.acl?.collaborators || [])
    .map(collaborator => collaborator?.identity)
    .filter(identity => identity?.remoteId && !(identity.providerId === 'group' && identity.remoteId === ADMINISTRATORS_GROUP))
    .map(identity => identityKey(identity.providerId, identity.remoteId));
}

/**
 * Tells whether a node has a public link. The listing flags it on its own,
 * since a link can exist on a node that is not open to anyone.
 *
 * @param {object} acl the access of a node, as the documents REST answers it
 * @returns {boolean} true when the node has a public link
 */
function hasPublicLink(acl) {
  return !!acl?.hasPublicLink || acl?.visibilityChoice === 'COLLABORATORS_AND_PUBLIC_ACCESS';
}

/**
 * Gives the public signs of a row: open to anyone, then a public link, each
 * told only when the reference does not have it already.
 *
 * @param {object} acl the row's access
 * @param {object} referenceAcl the opened folder's access, or null when the
 *          row is not compared with a folder
 * @returns {Array<string>} the public sign ids, at most two, in that order
 */
function publicSigns(acl, referenceAcl) {
  const signs = [];
  if (isPublic(acl) && !isPublic(referenceAcl)) {
    signs.push('anyone');
  }
  if (hasPublicLink(acl) && !hasPublicLink(referenceAcl)) {
    signs.push('publicLink');
  }
  return signs;
}

/**
 * Computes the signs of a row inside a folder, against the opened folder's
 * access.
 *
 * A row names, besides the people it was shared with, grants the storage made
 * by itself: its own creator, whose read entry the listing returns once the
 * row's access was saved, and the folder's creator, whose grant on a saved
 * folder the rows created inside inherit. Neither is a sharing, so a row
 * shows « people » only for someone neither its creator, the folder's
 * creator nor already on the folder.
 *
 * @param {object} file the listed row, with its `acl` and `creatorUserName`
 * @param {object} folder the opened folder, with its `acl` and `creatorUserName`
 * @returns {Array<string>} the sign ids, empty when the row shares as its folder
 */
function signsAgainstFolder(file, folder) {
  const acl = file.acl;
  const folderAcl = folder.acl;
  const signs = publicSigns(acl, folderAcl);
  if (signs.length) {
    return signs;
  }
  if (acl.visibilityChoice === 'SPECIFIC_COLLABORATOR' && folderAcl.visibilityChoice !== 'SPECIFIC_COLLABORATOR') {
    return ['restricted'];
  }
  const known = new Set(collaboratorKeys(folder));
  [folder.creatorUserName, file.creatorUserName]
    .filter(Boolean)
    .forEach(userName => known.add(identityKey('organization', userName)));
  return collaboratorKeys(file).some(key => !known.has(key)) ? ['people'] : [];
}

/**
 * Computes the sharing signs of a listed row: at most two, open to anyone
 * then a public link; or one of restricted and shared with people, inside a
 * folder only.
 *
 * @param {object} file the listed file or folder
 * @param {object} reference `{mode, folder}`: the mode, and in `FOLDER_MODE`
 *          the opened folder with its `acl`, null while it is not read yet
 * @returns {Array<string>} keys of `SIGNS`, empty for no sign
 */
export function getSharingSigns(file, reference) {
  if (!file?.acl || file.drive || file.sourceID || file.cloudDriveFolder || !reference?.mode) {
    return [];
  }
  if (reference.mode === FOLDER_MODE) {
    return reference.folder?.acl ? signsAgainstFolder(file, reference.folder) : [];
  }
  return publicSigns(file.acl, null);
}

/**
 * Tells whether the viewer may change a node's access from the list, by the
 * same rule as the action menu's Manage access entry: never on a shortcut, a
 * cloud drive folder, a news attachment or a locked system node, nor while
 * sharing is suspended. Sharing is known to be suspended only once the
 * transfer rules are loaded, so a row may show the action before then; the
 * rule is checked again when the action is used. A row with no access yet,
 * such as a folder being created, offers none.
 *
 * @param {object} file the listed file or folder
 * @returns {boolean} true when the viewer may open Manage access for it
 */
export function canManageAccess(file) {
  const entry = extensionRegistry.loadExtensions('DocumentMenu', 'menuActionMenu')
    .find(extension => extension.id === 'visibility');
  return !!(file?.acl && entry?.enabled?.(file, false));
}

/**
 * Opens Manage access for a node, when the viewer may still change its
 * access, and tells the analytics which view it was opened from.
 *
 * @param {object} root the application's root component
 * @param {object} file the listed file or folder
 * @param {string} view 'folderView' or 'recentView'
 * @returns {void}
 */
export function openManageAccess(root, file, view) {
  if (!canManageAccess(file)) {
    return;
  }
  root.$emit('open-visibility-drawer', file);
  document.dispatchEvent(new CustomEvent('manage-access', {
    detail: {
      'category': file.folder ? 'Folder' : 'Document',
      'spaceId': eXo.env.portal.spaceId,
      'view': view,
    }
  }));
}
