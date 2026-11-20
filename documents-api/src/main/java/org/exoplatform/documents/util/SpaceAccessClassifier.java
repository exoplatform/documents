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
 *
*/
package org.exoplatform.documents.util;

import java.util.Collection;
import java.util.Set;
import java.util.function.Predicate;

import org.apache.commons.lang3.StringUtils;

/**
 * Decides whether an entry of a space document's stored access list reaches
 * outside the space. An entry is inside when it is a membership of the space's
 * own group (*, manager, member, redactor, publisher), the document's owner, a
 * member of the space, or an identity present on the access list of the
 * space's drive root; every other entry, everyone ({@code any}) included, is
 * outside. The caller decides where membership is read from: the cached
 * space for a listing, the committed rows for a refusal or a removal.
 */
public class SpaceAccessClassifier {

  /** The membership types of the space's own group an entry may hold inside */
  public static final Set<String> SPACE_MEMBERSHIP_TYPES = Set.of("*", "manager", "member", "redactor", "publisher");

  private final String            spaceGroupId;

  private final Predicate<String> isMember;

  private final Set<String>       driveRootIdentities;

  /**
   * @param spaceGroupId the space's group, such as {@code /spaces/team}
   * @param isMember tells whether a user is a member of the space
   * @param driveRootIdentities the identities of the access list of the
   *          space's drive root
   */
  public SpaceAccessClassifier(String spaceGroupId, Predicate<String> isMember, Set<String> driveRootIdentities) {
    this.spaceGroupId = spaceGroupId;
    this.isMember = isMember;
    this.driveRootIdentities = driveRootIdentities == null ? Set.of() : Set.copyOf(driveRootIdentities);
  }

  /**
   * @param identities the identities of a document's access list, one per
   *          entry, repeated when an identity holds several permissions
   * @param owner the document's owner, null when it has none
   * @return true when at least one entry reaches outside the space
   */
  public boolean hasOutsideEntry(Collection<String> identities, String owner) {
    return identities != null && identities.stream().anyMatch(identity -> isOutside(identity, owner));
  }

  /**
   * @param identity the identity of an access entry: a user name, a
   *          membership such as {@code manager:/spaces/team}, or {@code any}
   * @param owner the document's owner, null when it has none
   * @return true when the entry reaches outside the space
   */
  public boolean isOutside(String identity, String owner) {
    if (StringUtils.isBlank(identity) || driveRootIdentities.contains(identity)) {
      return false;
    }
    int membershipSeparator = identity.indexOf(':');
    if (membershipSeparator > 0) {
      String membershipType = identity.substring(0, membershipSeparator);
      String groupId = identity.substring(membershipSeparator + 1);
      return !(StringUtils.equals(spaceGroupId, groupId) && SPACE_MEMBERSHIP_TYPES.contains(membershipType));
    }
    return !StringUtils.equals(identity, owner) && !isMember.test(identity);
  }

}
