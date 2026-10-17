/**
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
package org.exoplatform.documents.mcp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.commons.utils.ListAccess;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;

/**
 * Pins how a document tool finds a space by its name: among the user's spaces
 * only, by display name first, then by pretty name or group id, and never by
 * a guess.
 */
public class SpaceNameResolverTest {

  private static final String USERNAME = "mary";

  private SpaceService        spaceService;

  private SpaceNameResolver   resolver;

  /**
   * A resolver over a mocked space service.
   */
  @Before
  public void setUp() {
    spaceService = mock(SpaceService.class);
    resolver = new SpaceNameResolver(spaceService);
  }

  /**
   * The display name matches whatever its case, the whitespace around it and
   * repeated whitespace inside it.
   * Mutants: case or whitespace no longer ignored.
   *
   * @throws Exception never
   */
  @Test
  public void aNameMatchesIgnoringCaseAndWhitespace() throws Exception {
    Space legal = space("6", "Legal Team", "legal_team");
    memberOf(space("5", "Marketing", "marketing"), legal);

    assertEquals("6", resolver.resolve("Legal Team", USERNAME).getId());
    assertEquals("6", resolver.resolve("  legal team ", USERNAME).getId());
    assertEquals("6", resolver.resolve("LEGAL   TEAM", USERNAME).getId());
  }

  /**
   * When no display name matches, the pretty name or the group id does, and
   * a display name match wins over another space's pretty name.
   * Mutants: the secondary rule removed, or tried before the display name.
   *
   * @throws Exception never
   */
  @Test
  public void thePrettyNameOrGroupIdMatchesWhenNoDisplayNameDoes() throws Exception {
    Space renamed = space("6", "Legal & Compliance", "legal");
    Space named = space("9", "Legal_old", "legal_old");
    memberOf(renamed, named);

    assertEquals("6", resolver.resolve("legal", USERNAME).getId());
    assertEquals("6", resolver.resolve("/spaces/legal", USERNAME).getId());

    Space displayLegal = space("7", "Legal", "legal_dept");
    memberOf(renamed, displayLegal);
    assertEquals("7", resolver.resolve("legal", USERNAME).getId());
  }

  /**
   * An unknown name is refused, naming at most five of the user's spaces,
   * the closest first: a name holding the asked one, then by edit distance.
   * Mutants: the suggestions not sorted, not limited, or missing.
   */
  @Test
  public void anUnknownNameIsRefusedWithTheClosestSpaces() {
    memberOf(space("1", "Zeta", "zeta"),
             space("2", "Legal Team", "legal_team"),
             space("3", "Legel", "legel"),
             space("4", "Marketing", "marketing"),
             space("5", "Sales", "sales"),
             space("8", "Engineering", "engineering"),
             space("10", "Support", "support"));

    ObjectNotFoundException e = assertThrows(ObjectNotFoundException.class, () -> resolver.resolve("Legal", USERNAME));

    String message = e.getMessage();
    assertTrue(message, message.startsWith("No space named 'Legal' among the spaces you're a member of. The closest are 'Legal Team' (space_id 2), 'Legel' (space_id 3)"));
    assertTrue(message, message.contains("get_my_spaces"));
    assertEquals(message, SpaceNameResolver.MAX_SUGGESTIONS, message.split("space_id \\d+\\)").length - 1);
  }

  /**
   * A user who is a member of no space is told so.
   */
  @Test
  public void anUnknownNameOfAUserWithoutSpacesSaysSo() {
    memberOf();

    ObjectNotFoundException e = assertThrows(ObjectNotFoundException.class, () -> resolver.resolve("Legal", USERNAME));

    assertTrue(e.getMessage(), e.getMessage().contains("you aren't a member of any space"));
  }

  /**
   * Two of the user's spaces with the same name are refused, listing both
   * with their ids, rather than one of them picked.
   * Mutants: the first match returned.
   */
  @Test
  public void twoSpacesWithTheNameAreRefusedWithTheirIds() {
    memberOf(space("6", "Legal", "legal"), space("9", "legal ", "legal_1"));

    IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> resolver.resolve("Legal", USERNAME));

    assertTrue(e.getMessage(), e.getMessage().contains("'Legal' (space_id 6)"));
    assertTrue(e.getMessage(), e.getMessage().contains("'legal ' (space_id 9)"));
    assertTrue(e.getMessage(), e.getMessage().contains("space_id of the one you mean"));
  }

  /**
   * A space the user isn't a member of is never found, by its display name or
   * by its pretty name, even though the space service knows it: only the
   * user's spaces are read.
   * Mutants: a lookup by pretty name or a search across every space.
   */
  @Test
  public void aSpaceTheUserIsNotAMemberOfIsNotFound() {
    Space secret = space("6", "Legal", "legal");
    when(spaceService.getSpaceByPrettyName("legal")).thenReturn(secret);
    memberOf(space("5", "Marketing", "marketing"));

    assertThrows(ObjectNotFoundException.class, () -> resolver.resolve("Legal", USERNAME));
    assertThrows(ObjectNotFoundException.class, () -> resolver.resolve("legal", USERNAME));
    verify(spaceService, never()).getSpaceByPrettyName(anyString());
  }

  /**
   * Every page of the user's spaces is read, so a space past the first page
   * is found.
   * Mutants: only the first page read.
   *
   * @throws Exception never
   */
  @Test
  public void aSpacePastTheFirstPageIsFound() throws Exception {
    List<Space> spaces = new ArrayList<>();
    for (int i = 1; i <= 250; i++) {
      spaces.add(space(String.valueOf(i), "Space " + i, "space_" + i));
    }
    memberOf(spaces.toArray(new Space[0]));

    assertEquals("250", resolver.resolve("Space 250", USERNAME).getId());
  }

  /**
   * A blank name is refused.
   */
  @Test
  public void aBlankNameIsRefused() {
    assertThrows(IllegalArgumentException.class, () -> resolver.resolve("  ", USERNAME));
    assertThrows(IllegalArgumentException.class, () -> resolver.resolve(null, USERNAME));
  }

  /**
   * @param id the space id
   * @param displayName its display name
   * @param prettyName its pretty name
   * @return the space
   */
  private static Space space(String id, String displayName, String prettyName) {
    Space space = new Space();
    space.setId(id);
    space.setDisplayName(displayName);
    space.setPrettyName(prettyName);
    space.setGroupId("/spaces/" + prettyName);
    return space;
  }

  /**
   * Makes the user a member of these spaces, and of no other.
   *
   * @param spaces the user's spaces
   */
  private void memberOf(Space... spaces) {
    when(spaceService.getMemberSpaces(USERNAME)).thenReturn(listAccessOf(spaces));
  }

  /**
   * @param spaces the spaces
   * @return a list access over them, read a page at a time
   */
  static ListAccess<Space> listAccessOf(Space... spaces) {
    List<Space> list = Arrays.asList(spaces);
    return new ListAccess<>() {
      /**
       * @param index the first space
       * @param length the most spaces
       * @return the page
       */
      @Override
      public Space[] load(int index, int length) {
        return list.subList(index, Math.min(list.size(), index + length)).toArray(new Space[0]);
      }

      /**
       * @return the number of spaces
       */
      @Override
      public int getSize() {
        return list.size();
      }
    };
  }

}
