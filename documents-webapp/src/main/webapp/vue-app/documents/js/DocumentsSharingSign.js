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
 * The sharing signs a listed file or folder shows after its name: its
 * visibility, with the icon and the tooltip the former Access column used,
 * when its access is its own, then a link sign when it has a public link.
 *
 * Inside a folder, a row's access is its own when it differs from the opened
 * folder's: the listing gives each row its own access, and the opened
 * folder's access is read once per folder (`FOLDER_MODE`). In the recent view
 * and in search results, rows come from many folders, so only a public access
 * counts as its own (`EXPOSURE_MODE`).
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
  publicLink: {
    id: 'publicLink',
    icon: 'fas fa-link',
    labelKey: 'documents.label.sharing.publicLink',
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
 * Tells whether a node's access names the space or the user browsing it: the
 * rule the Access column used to show a read-only space or drive share.
 *
 * @param {object} acl the node's access
 * @returns {boolean} true when the current space, or the current user outside a
 *          space, is among the node's collaborators
 */
function isSharedWithCurrentSpaceOrDrive(acl) {
  const spaceIdentityId = eXo.env.portal.spaceIdentityId;
  const spaceName = eXo.env.portal.spaceName;
  const collaborators = acl?.collaborators || [];
  if (spaceIdentityId && spaceName && collaborators.length > 0) {
    return collaborators.some(collaborator => collaborator.identity.id === spaceIdentityId
      && collaborator.identity.remoteId === spaceName);
  }
  return collaborators.some(collaborator => collaborator.identity.remoteId === eXo.env.portal.userName);
}

/**
 * Gives the icon and the tooltip of a node's visibility, as the list's former
 * Access column showed them.
 *
 * @param {object} file the listed file or folder
 * @returns {object} the sign: `{id: 'visibility', icon, labelKey}`
 */
export function getVisibilitySign(file) {
  const sign = (icon, labelKey) => ({id: 'visibility', icon, labelKey});
  const inSpace = !!eXo.env.portal.spaceGroup;
  if (file.drive) {
    return sign('fas fa-lock', 'documents.label.visibility.system');
  }
  if (file.folder && file.creatorUserName === '__system' && eXo.env.portal.spaceIdentityId && !eXo.env.portal.isAdministrator) {
    return sign('fas fa-lock', 'documents.label.visibility.system');
  }
  if (file.folder && file.id < 0) {
    return sign('fas fa-layer-group', 'documents.label.visibility.all');
  }
  const acl = file.acl;
  if (isSharedWithCurrentSpaceOrDrive(acl) && !acl.canEdit) {
    const collaborators = acl.collaborators.filter(e => e.identity.id === eXo.env.portal.spaceIdentityId || eXo.env.portal.userName);
    return collaborators[0].permission === 'read' ?
      sign('fas fa-eye', 'documents.label.visibility.specific.manger')
      : sign('fas fa-layer-group', 'documents.label.visibility.all');
  }
  switch (acl.visibilityChoice) {
  case 'SPECIFIC_COLLABORATOR':
    return sign('fas fa-user-lock', inSpace ? 'documents.label.visibility.specific.collaborator'
      : 'documents.myDrive.label.visibility.specific.collaborator');
  case 'ALL_MEMBERS':
    return acl.allMembersCanEdit ?
      sign('fas fa-layer-group', 'documents.label.visibility.all')
      : sign('fas fa-eye', 'documents.label.visibility.specific.manger');
  case 'COLLABORATORS_AND_PUBLIC_ACCESS':
  case 'ANYONE':
    return sign('fas fa-globe', inSpace ? 'documents.label.visibility.anyone' : 'documents.myDrive.label.visibility.anyone');
  default:
    return sign('fas fa-layer-group', 'documents.label.visibility.all');
  }
}

/**
 * Tells whether a row's access differs from the opened folder's, a public
 * access with or without a link counting as one visibility.
 *
 * A row names, besides the people it was shared with, grants the storage made
 * by itself: its own creator, whose read entry the listing returns once the
 * row's access was saved, and the folder's creator, whose grant on a saved
 * folder the rows created inside inherit. Neither is a sharing, so only
 * someone neither its creator, the folder's creator nor already on the folder
 * makes the row's access differ.
 *
 * @param {object} file the listed row, with its `acl` and `creatorUserName`
 * @param {object} folder the opened folder, with its `acl` and `creatorUserName`
 * @returns {boolean} true when the row's access is its own
 */
function differsFromFolder(file, folder) {
  const acl = file.acl;
  const folderAcl = folder.acl;
  const visibility = nodeAcl => (isPublic(nodeAcl) ? 'PUBLIC' : nodeAcl.visibilityChoice);
  if (visibility(acl) !== visibility(folderAcl)) {
    return true;
  }
  if (acl.visibilityChoice === 'ALL_MEMBERS' && !!acl.allMembersCanEdit !== !!folderAcl.allMembersCanEdit) {
    return true;
  }
  const known = new Set(collaboratorKeys(folder));
  [folder.creatorUserName, file.creatorUserName]
    .filter(Boolean)
    .forEach(userName => known.add(identityKey('organization', userName)));
  return collaboratorKeys(file).some(key => !known.has(key));
}

/**
 * Computes the sharing signs of a listed row, at most two: its visibility, as
 * the former Access column showed it, when its access is its own (inside a
 * folder, when it differs from the folder's; in the recent view and in search
 * results, when it is public), then a link sign when it has a public link.
 *
 * @param {object} file the listed file or folder
 * @param {object} reference `{mode, folder}`: the mode, and in `FOLDER_MODE`
 *          the opened folder with its `acl`, null while it is not read yet
 * @returns {Array<object>} the signs, `{id, icon, labelKey}`, empty for none
 */
export function getSharingSigns(file, reference) {
  if (!file?.acl || file.drive || file.sourceID || file.cloudDriveFolder || !reference?.mode) {
    return [];
  }
  let ownAccess;
  if (reference.mode === FOLDER_MODE) {
    if (!reference.folder?.acl) {
      return [];
    }
    ownAccess = differsFromFolder(file, reference.folder);
  } else {
    ownAccess = isPublic(file.acl);
  }
  const signs = ownAccess ? [getVisibilitySign(file)] : [];
  if (hasPublicLink(file.acl)) {
    signs.push(SIGNS.publicLink);
  }
  return signs;
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
