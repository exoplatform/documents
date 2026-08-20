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
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <gnu.org/licenses>.
 */
package org.exoplatform.document.cleanup.util;

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.model.Profile;

/**
 * Single definition of the owner display name of a cleanup item, shared by the
 * REST DTO builder and the CSV report so the exported name and the one shown in
 * the UI can never disagree. Works for a user AND a space identity alike: both
 * carry a profile full name, with the remote id as fallback.
 */
public class CleanupIdentityUtil {

  private CleanupIdentityUtil() {
    // static utility
  }

  /**
   * Display name of an owner identity: its profile full name, falling back to
   * its remote id when the profile is missing or unnamed.
   *
   * @param identity owner identity, nullable (unresolvable identity id)
   * @return display name, EMPTY (never null) for an unresolvable identity — a
   *         missing owner degrades a report row, it never breaks it
   */
  public static String displayName(Identity identity) {
    if (identity == null) {
      return "";
    }
    Profile profile = identity.getProfile();
    String name = profile == null || StringUtils.isBlank(profile.getFullName()) ? identity.getRemoteId() : profile.getFullName();
    // Never null: the CSV memoizes this per owner (a null would be re-resolved
    // on every row) and the escaping downstream expects a value
    return StringUtils.defaultString(name);
  }

}
