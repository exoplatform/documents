/*
 * Copyright (C) 2003-2021 eXo Platform SAS.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.exoplatform.documents.rest.util;

import org.exoplatform.services.security.ConversationState;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;

public class RestUtils {

  private RestUtils() {
  }

  public static final String getCurrentUser() {
    return ConversationState.getCurrent().getIdentity().getUserId();
  }

  public static final Identity getCurrentUserIdentity(IdentityManager identityManager) {
    String currentUser = getCurrentUser();
    return identityManager.getOrCreateUserIdentity(currentUser);
  }

  public static final long getCurrentUserIdentityId(IdentityManager identityManager) {
    String currentUser = getCurrentUser();
    Identity identity = identityManager.getOrCreateUserIdentity(currentUser);
    return identity == null ? 0 : Long.parseLong(identity.getId());
  }

}
