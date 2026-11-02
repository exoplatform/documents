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
 * only for a real exposure: a public access, or collaborators added to it
 * (`EXPOSURE_MODE`). The listing leaves the drive's owner out of the
 * collaborators; the collaborators the storage adds by itself (the
 * administrators group, the creator's read entry) are left out here, so any
 * collaborator left was added by someone.
 */

export const FOLDER_MODE = 'folder';
export const EXPOSURE_MODE = 'exposure';

export const SIGNS = {
  anyone: {
    icon: 'fas fa-globe',
    labelKey: 'documents.label.sharing.anyone',
  },
  publicLink: {
    icon: 'fas fa-globe',
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
 * Tells whether a collaborator is one the storage adds by itself rather than
 * a sharing: the administrators group, or the node's own creator. Saving a
 * node's access grants its creator every permission, which the content
 * repository keeps as one entry per permission; the listing hides the
 * creator's edit entries but returns its read entry as a collaborator.
 *
 * @param {object} identity the collaborator's identity
 * @param {string} creatorUserName the user name of the node's creator
 * @returns {boolean} true when the collaborator is not a sharing
 */
function isImplicitCollaborator(identity, creatorUserName) {
  if (identity.providerId === 'group') {
    return identity.remoteId === ADMINISTRATORS_GROUP;
  }
  return identity.providerId === 'organization' && !!creatorUserName && identity.remoteId === creatorUserName;
}

/**
 * Lists the ids of the identities a node's access names as collaborators,
 * leaving out those the storage adds by itself.
 *
 * @param {object} node a listed row or a folder, with its `acl` and its
 *          `creatorUserName`
 * @returns {Array<string>} the identity ids, empty when there is none
 */
function collaboratorIds(node) {
  return (node?.acl?.collaborators || [])
    .map(collaborator => collaborator?.identity)
    .filter(identity => identity?.id && !isImplicitCollaborator(identity, node.creatorUserName))
    .map(identity => identity.id);
}

/**
 * Gives the public sign of an access.
 *
 * @param {object} acl the access of a node
 * @returns {string} 'publicLink' when a public link exists, 'anyone' otherwise
 */
function publicSign(acl) {
  return acl.visibilityChoice === 'COLLABORATORS_AND_PUBLIC_ACCESS' ? 'publicLink' : 'anyone';
}

/**
 * Computes the sign of a row inside a folder, against the opened folder's access.
 *
 * @param {object} file the listed row, with its `acl` and `creatorUserName`
 * @param {object} folder the opened folder, with its `acl` and `creatorUserName`
 * @returns {string|null} the sign id, or null when the row shares as its folder
 */
function signAgainstFolder(file, folder) {
  const acl = file.acl;
  const folderAcl = folder.acl;
  if (isPublic(acl) && !isPublic(folderAcl)) {
    return publicSign(acl);
  }
  if (acl.visibilityChoice === 'SPECIFIC_COLLABORATOR' && folderAcl.visibilityChoice === 'ALL_MEMBERS') {
    return 'restricted';
  }
  const folderCollaborators = collaboratorIds(folder);
  if (collaboratorIds(file).some(id => !folderCollaborators.includes(id))) {
    return 'people';
  }
  return null;
}

/**
 * Computes the sign of a row that is not compared with a folder.
 *
 * @param {object} file the listed row, with its `acl` and `creatorUserName`
 * @returns {string|null} the sign id, or null when the row is not exposed
 */
function signOfExposure(file) {
  if (isPublic(file.acl)) {
    return publicSign(file.acl);
  }
  return collaboratorIds(file).length ? 'people' : null;
}

/**
 * Computes the sharing sign of a listed row.
 *
 * @param {object} file the listed file or folder
 * @param {object} reference `{mode, folder}`: the mode, and in `FOLDER_MODE`
 *          the opened folder with its `acl`, null while it is not read yet
 * @returns {string|null} a key of `SIGNS`, or null for no sign
 */
export function getSharingSign(file, reference) {
  if (!file?.acl || file.drive || file.sourceID || file.cloudDriveFolder || !reference?.mode) {
    return null;
  }
  if (reference.mode === FOLDER_MODE) {
    return reference.folder?.acl ? signAgainstFolder(file, reference.folder) : null;
  }
  return signOfExposure(file);
}
