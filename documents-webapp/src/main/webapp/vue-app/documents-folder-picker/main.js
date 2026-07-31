/*
 * Copyright (C) 2025 eXo Platform SAS.
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

// Site-wide bootstrap of the reusable Documents folder picker (EXO-88830).
//
// This module is deliberately TINY and is the only piece loaded on every page (via
// the quick-actions discovery: the module name contains "QuickActionExtension", so
// the top bar's includeExtensions('QuickActionExtension') requires it everywhere).
// It registers ONE always-on listener for the public 'open-documents-folder-picker'
// DOM event and, on first use only, lazily requires the real picker app
// (SHARED/DocumentsFolderPickerApp) which mounts the ONE existing folders-only
// drive explorer (attachments-drive-explorer-drawer) on its own small root.
//
// Public contract (DOM CustomEvents on `document`):
//  - 'open-documents-folder-picker'   detail: {defaultDrive?, defaultFolder?, spaceId?}
//  - 'documents-folder-picker-selected'  detail: {folderId, driveName, drive, ownerId,
//        spaceId, relativePath, path, schemaFolder, folderName}
//  - 'documents-folder-picker-cancelled' detail: {}

const PICKER_EVENT = 'open-documents-folder-picker';

// The AMD id of the lazily-loaded picker app (a plain named gatein module of this
// webapp, requireable from any page like the unified-search connector modules).
const PICKER_APP_MODULE = 'SHARED/DocumentsFolderPickerApp';

// Whether the single always-on listener has been registered, so registering it again
// (init() called by the extension discovery after the factory already ran) is a no-op.
let registered = false;

/**
 * Serves a folder-picker request: lazily loads the picker app bundle (first use
 * only — RequireJS caches it afterwards) and hands it the request detail.
 *
 * @param {CustomEvent} event the open-documents-folder-picker event
 * @returns {void}
 */
function onOpenFolderPicker(event) {
  const detail = event && event.detail || {};
  window.require([PICKER_APP_MODULE], app => app.open(detail), error => {
    // surface the failure instead of swallowing the user's click silently
    // eslint-disable-next-line no-console
    console.error('Documents folder picker could not be loaded', error);
  });
}

/**
 * Registers the single, always-on folder-picker listener. Idempotent: safe to call
 * from the module factory AND from init().
 *
 * @returns {void}
 */
function registerFolderPickerListener() {
  if (registered) {
    return;
  }
  registered = true;
  document.addEventListener(PICKER_EVENT, onOpenFolderPicker);
}

/**
 * Entry point for the platform's extension discovery (includeExtensions requires
 * this module by name, then calls init()). Registers the always-on listener.
 *
 * @returns {void}
 */
export function init() {
  registerFolderPickerListener();
}

// Also register as soon as the module factory runs, so the listener is present
// whether the module is executed by name-based discovery or as a plain dependency.
registerFolderPickerListener();
