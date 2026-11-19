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
package org.exoplatform.documents.storage.jcr.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SpaceAccessClassifierTest {

  private static final String         SPACE_GROUP = "/spaces/team";

  private final SpaceAccessClassifier classifier  = new SpaceAccessClassifier(SPACE_GROUP,
                                                                              Set.of("mary", "john")::contains,
                                                                              Set.of("*:/platform/administrators",
                                                                                     "*:/spaces/team"));

  @ParameterizedTest
  @ValueSource(strings = { "*:/spaces/team", "manager:/spaces/team", "member:/spaces/team", "redactor:/spaces/team",
      "publisher:/spaces/team" })
  void aMembershipOfTheSpaceGroupIsInside(String identity) {
    assertFalse(classifier.isOutside(identity, "owner"));
  }

  @ParameterizedTest
  @ValueSource(strings = { "validator:/spaces/team", "*:/spaces/other", "manager:/spaces/team2", "*:/platform/users",
      "manager:/spaces" })
  void anotherMembershipIsOutside(String identity) {
    assertTrue(classifier.isOutside(identity, "owner"));
  }

  @Test
  void everyoneIsOutside() {
    assertTrue(classifier.isOutside("any", "owner"));
  }

  @Test
  void theOwnerAndTheMembersAreInsideAndAnotherUserIsOutside() {
    assertFalse(classifier.isOutside("owner", "owner"));
    assertFalse(classifier.isOutside("mary", "owner"));
    assertTrue(classifier.isOutside("paul", "owner"));
    assertTrue(classifier.isOutside("paul", null));
  }

  @Test
  void anIdentityOfTheDriveRootAccessListIsInside() {
    assertFalse(classifier.isOutside("*:/platform/administrators", "owner"));

    SpaceAccessClassifier withoutDriveRoot = new SpaceAccessClassifier(SPACE_GROUP, Set.of("mary")::contains, null);
    assertTrue(withoutDriveRoot.isOutside("*:/platform/administrators", "owner"));
  }

  @Test
  void anAccessListWithOneOutsideEntryIsSharedOutside() {
    assertFalse(classifier.hasOutsideEntry(List.of("manager:/spaces/team", "owner", "mary", "mary"), "owner"));
    assertTrue(classifier.hasOutsideEntry(List.of("manager:/spaces/team", "owner", "paul"), "owner"));
    assertTrue(classifier.hasOutsideEntry(List.of("any", "owner"), "owner"));
    assertFalse(classifier.hasOutsideEntry(List.of(), "owner"));
    assertFalse(classifier.hasOutsideEntry(null, "owner"));
  }

}
