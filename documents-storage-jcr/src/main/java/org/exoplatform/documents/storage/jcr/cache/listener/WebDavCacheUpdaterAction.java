/**
 * Copyright (C) 2025 eXo Platform SAS
 *
 *  This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <gnu.org/licenses>.
 */
package org.exoplatform.documents.storage.jcr.cache.listener;

import static javax.jcr.observation.Event.NODE_ADDED;
import static javax.jcr.observation.Event.NODE_REMOVED;
import static javax.jcr.observation.Event.PROPERTY_ADDED;
import static javax.jcr.observation.Event.PROPERTY_CHANGED;
import static javax.jcr.observation.Event.PROPERTY_REMOVED;
import static org.exoplatform.documents.storage.jcr.util.NodeTypeConstants.NT_RESOURCE;
import static org.exoplatform.services.jcr.observation.ExtendedEvent.ADD_MIXIN;
import static org.exoplatform.services.jcr.observation.ExtendedEvent.CHECKIN;
import static org.exoplatform.services.jcr.observation.ExtendedEvent.CHECKOUT;
import static org.exoplatform.services.jcr.observation.ExtendedEvent.LOCK;
import static org.exoplatform.services.jcr.observation.ExtendedEvent.NODE_MOVED;
import static org.exoplatform.services.jcr.observation.ExtendedEvent.PERMISSION_CHANGED;
import static org.exoplatform.services.jcr.observation.ExtendedEvent.REMOVE_MIXIN;
import static org.exoplatform.services.jcr.observation.ExtendedEvent.UNLOCK;

import org.apache.commons.chain.Context;

import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.documents.storage.jcr.cache.CachedJcrWebDavService;
import org.exoplatform.services.command.action.Action;
import org.exoplatform.services.ext.action.InvocationContext;
import org.exoplatform.services.jcr.impl.core.NodeImpl;
import org.exoplatform.services.jcr.impl.core.PropertyImpl;

public class WebDavCacheUpdaterAction implements Action {

  private CachedJcrWebDavService cachedJcrWebDavService;

  @Override
  public boolean execute(Context context) throws Exception {
    int eventType = (Integer) context.get(InvocationContext.EVENT);

    NodeImpl node = null;
    switch (eventType) { // NOSONAR
    case NODE_ADDED, NODE_REMOVED, PERMISSION_CHANGED, NODE_MOVED, ADD_MIXIN, REMOVE_MIXIN, LOCK, UNLOCK, CHECKIN, CHECKOUT:
      node = (NodeImpl) context.get(InvocationContext.CURRENT_ITEM);
      break;
    case PROPERTY_ADDED, PROPERTY_CHANGED, PROPERTY_REMOVED:
      PropertyImpl property = (PropertyImpl) context.get(InvocationContext.CURRENT_ITEM);
      if (property != null) {
        node = property.getParent();
        if (node.isNodeType(NT_RESOURCE)) {
          node = node.getParent();
        }
      }
      break;
    }
    if (node != null) {
      boolean dropEntry = eventType == NODE_ADDED
                          || eventType == NODE_MOVED
                          || eventType == NODE_REMOVED
                          || eventType == PERMISSION_CHANGED;
      getCachedJcrWebDavService().clearCache(node, dropEntry);
    }
    return true;
  }

  public CachedJcrWebDavService getCachedJcrWebDavService() {
    if (cachedJcrWebDavService == null) {
      cachedJcrWebDavService = ExoContainerContext.getService(CachedJcrWebDavService.class);
    }
    return cachedJcrWebDavService;
  }
}
