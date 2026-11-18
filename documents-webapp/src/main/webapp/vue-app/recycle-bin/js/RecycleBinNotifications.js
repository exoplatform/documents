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
import {initCometd} from '../../documents/js/WebSocket.js';

/** The action id under which a purge tells its requester that it is over. */
export const PURGE_ACTION_ID = 'recycle-bin-purge';

const END_STATUSES = ['done_successfully', 'done_with_errors', 'failed', 'canceled'];
const listeners = [];
let subscribed = false;

function subscribe() {
  if (!subscribed) {
    subscribed = true;
    initCometd(eXo.env.portal.cometdContext, eXo.env.portal.cometdToken, actionData => {
      const status = actionData?.status?.toLowerCase?.();
      if (END_STATUSES.includes(status)) {
        const action = {
          actionId: String(actionData.actionId),
          succeeded: status === 'done_successfully',
          // A bulk action names the items it treated, a purge counts what it removed.
          numberOfItems: Array.isArray(actionData.treatedItemsIds)
            ? actionData.treatedItemsIds.length
            : actionData.numberOfItems || 0,
        };
        listeners.slice().forEach(listener => listener(action));
      }
    });
  }
}

// Tells the listener each bulk action of the user that is over, on the
// channel the bulk actions of the documents already use: the page subscribes
// once, whatever the number of listeners.
export function addActionEndListener(listener) {
  subscribe();
  listeners.push(listener);
}

export function removeActionEndListener(listener) {
  const index = listeners.indexOf(listener);
  if (index >= 0) {
    listeners.splice(index, 1);
  }
}
