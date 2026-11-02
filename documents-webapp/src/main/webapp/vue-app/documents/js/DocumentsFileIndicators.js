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

/**
 * Small markers other add-ons show after a file's or a folder's name, in the
 * file list and on the file's or the folder's tile. A provider registers with
 * extensionRegistry.registerExtension(EXTENSION_APP, PROVIDER_TYPE, {id, rank, load})
 * where load(fileIds) resolves to a Map, or a plain object, from a file id,
 * as listed, to whatever that provider's component shows for the file. Its
 * component registers with
 * extensionRegistry.registerComponent(EXTENSION_APP, COMPONENT_TYPE, {id, vueComponent, rank})
 * and receives the props {file, indicators, view}: file is the file or the
 * folder; indicators maps each provider id to its answer for it, and is
 * undefined where no provider was asked, such as a tile shown outside the
 * Documents application; view is 'list' in the file list and 'tile' on a
 * file's or a folder's tile, so markers can fit the room. The application
 * asks every provider once for each page of files it lists, never once per
 * file.
 */
export const EXTENSION_APP = 'DocumentsFileIndicators';

export const PROVIDER_TYPE = 'provider';

export const COMPONENT_TYPE = 'documents-file-indicator';

export const UPDATED_EVENT = `extension-${EXTENSION_APP}-${PROVIDER_TYPE}-updated`;

/**
 * @returns {Array<object>} the registered providers, in rank order
 */
export function getProviders() {
  return (extensionRegistry.loadExtensions(EXTENSION_APP, PROVIDER_TYPE) || []).slice()
    .sort((provider1, provider2) => (provider1.rank || 0) - (provider2.rank || 0));
}

/**
 * Asks every registered provider, once each, for the markers of some listed
 * files. A provider that fails is left out; the others still answer.
 *
 * @param {Array<string>} fileIds the ids of the files listed, each once
 * @returns {Promise<object>} file id to {providerId: answer}, holding an entry
 *          for every id asked, empty when no provider answered for it
 */
export function loadIndicators(fileIds) {
  const providers = getProviders();
  const byFile = {};
  (fileIds || []).forEach(fileId => byFile[fileId] = {});
  if (!providers.length || !fileIds?.length) {
    return Promise.resolve(byFile);
  }
  return Promise.all(providers.map(provider => Promise.resolve()
    .then(() => provider.load(fileIds))
    .then(answer => ({provider, answer}))
    .catch(e => {
      console.error(`Error reading the file markers of ${provider.id}`, e);
      return {provider, answer: null};
    })))
    .then(answers => {
      answers.forEach(({provider, answer}) => {
        const entries = answer instanceof Map ? [...answer.entries()] : Object.entries(answer || {});
        entries.forEach(([fileId, value]) => {
          if (byFile[fileId] && (value ?? null) !== null) {
            byFile[fileId][provider.id] = value;
          }
        });
      });
      return byFile;
    });
}
