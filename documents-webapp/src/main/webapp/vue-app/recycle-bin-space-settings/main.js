/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <gnu.org/licenses>.
 */

// The entry of the Recycle Bin in the settings of a space. The page requires
// this module and calls init().
//
// The entry has no isEnabled: an asynchronous one is lost when another
// extension with one registers at the same time, the page then keeping only
// the last of them. The entry renders nothing for who neither manages nor
// publishes in the space, as an administrator of the platform may reach its
// settings.
export function init() {
  return Vue.prototype.$recycleBinService.loadLabels().then(() => {
    extensionRegistry.registerComponent('SpaceSettings', 'space-settings-components', {
      id: 'documents-recycle-bin-space-settings',
      vueComponent: Vue.options.components['documents-recycle-bin-space-settings'],
      rank: 0,
    });
  });
}
