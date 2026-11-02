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
 * (`EXPOSURE_MODE`). The listing leaves out of the collaborators the drive's
 * owner and the creator's own grant, so any collaborator left was added.
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
 * Tells whether an access is open to the public.
 *
 * @param {object} acl the access of a node, as the documents REST answers it
 * @returns {boolean} true for a public access, with or without a public link
 */
function isPublic(acl) {
  return PUBLIC_CHOICES.includes(acl?.visibilityChoice);
}

/**
 * Lists the ids of the identities an access names as collaborators.
 *
 * @param {object} acl the access of a node
 * @returns {Array<string>} the identity ids, empty when there is none
 */
function collaboratorIds(acl) {
  return (acl?.collaborators || []).map(collaborator => collaborator?.identity?.id).filter(Boolean);
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
 * @param {object} acl the row's access
 * @param {object} folderAcl the opened folder's access
 * @returns {string|null} the sign id, or null when the row shares as its folder
 */
function signAgainstFolder(acl, folderAcl) {
  if (isPublic(acl) && !isPublic(folderAcl)) {
    return publicSign(acl);
  }
  if (acl.visibilityChoice === 'SPECIFIC_COLLABORATOR' && folderAcl.visibilityChoice === 'ALL_MEMBERS') {
    return 'restricted';
  }
  const folderCollaborators = collaboratorIds(folderAcl);
  if (collaboratorIds(acl).some(id => !folderCollaborators.includes(id))) {
    return 'people';
  }
  return null;
}

/**
 * Computes the sign of a row that is not compared with a folder.
 *
 * @param {object} acl the row's access
 * @returns {string|null} the sign id, or null when the row is not exposed
 */
function signOfExposure(acl) {
  if (isPublic(acl)) {
    return publicSign(acl);
  }
  return collaboratorIds(acl).length ? 'people' : null;
}

/**
 * Computes the sharing sign of a listed row.
 *
 * @param {object} file the listed file or folder
 * @param {object} reference `{mode, acl}`: the mode, and in `FOLDER_MODE` the
 *          opened folder's access, null while it is not read yet
 * @returns {string|null} a key of `SIGNS`, or null for no sign
 */
export function getSharingSign(file, reference) {
  const acl = file?.acl;
  if (!acl || file.drive || file.sourceID || file.cloudDriveFolder || !reference?.mode) {
    return null;
  }
  if (reference.mode === FOLDER_MODE) {
    return reference.acl ? signAgainstFolder(acl, reference.acl) : null;
  }
  return signOfExposure(acl);
}
