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

const CHANNEL = '/eXo/Application/CleanupCampaign';

// Subscribes to the cleanup CometD channel. The shared $socialWebSocket
// helper re-dispatches every received message as a DOM CustomEvent named
// by the payload's wsEventName ('campaign.progress' / 'campaign.stateChanged'),
// with the full payload in event.detail.
export function init() {
  Vue.prototype.$socialWebSocket.initCometd(CHANNEL);
}
