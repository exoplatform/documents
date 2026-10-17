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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.commons.utils.ListAccess;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;

/**
 * Finds the space a document tool call names by its name, among the spaces
 * the calling user is a member of, and only those: a space the user isn't a
 * member of is never found, whatever its visibility.
 * <p>
 * The name is matched against the space's display name, ignoring case,
 * leading and trailing whitespace and repeated inner whitespace. Only when no
 * display name matches, it is matched against the space's pretty name (its
 * URL name) or its group id ({@code /spaces/<pretty name>}), ignoring case.
 * Nothing is ever guessed: no match is refused with the closest names the
 * user's spaces carry, and several spaces with that name are refused with
 * their ids, so the caller picks one by its id.
 */
public class SpaceNameResolver {

  /** The most spaces a refusal names. */
  public static final int     MAX_SUGGESTIONS = 5;

  /** The refusal of a name several of the user's spaces carry. */
  private static final String MSG_SEVERAL     =
                                          "Several of your spaces are named '%s': %s. Call again with the space_id of the one you mean.";

  /** The refusal of a name none of the user's spaces carries. */
  private static final String MSG_NOT_FOUND   =
                                            "No space named '%s' among the spaces you're a member of. The closest are %s. Call again with the exact space_name or the space_id of one of them, or find the space with get_my_spaces.";

  /** How many of the user's spaces are read at a time. */
  private static final int    PAGE_SIZE       = 100;

  /** The group id prefix of a space. */
  private static final String SPACE_GROUP     = "/spaces/";

  /** Reads the spaces the user is a member of. */
  private final SpaceService  spaceService;

  /**
   * @param spaceService reads the spaces the user is a member of
   */
  public SpaceNameResolver(SpaceService spaceService) {
    this.spaceService = spaceService;
  }

  /**
   * Finds the one space with that name among the spaces the user is a member
   * of.
   *
   * @param spaceName the space's name as the caller gave it
   * @param username the calling user
   * @return the space, never null
   * @throws IllegalArgumentException when the name is blank, or when several
   *           of the user's spaces carry it: the message lists them with their
   *           ids
   * @throws ObjectNotFoundException when none of the user's spaces carries
   *           it: the message names the closest ones
   */
  public Space resolve(String spaceName, String username) throws ObjectNotFoundException {
    String wanted = normalize(spaceName);
    if (wanted.isEmpty()) {
      throw new IllegalArgumentException("The space_name is blank. Pass the space's name, or its space_id; get_my_spaces lists your spaces.");
    }
    List<Space> spaces = memberSpaces(username);
    List<Space> byDisplayName = spaces.stream().filter(space -> wanted.equals(normalize(space.getDisplayName()))).toList();
    if (byDisplayName.size() == 1) {
      return byDisplayName.get(0);
    } else if (byDisplayName.size() > 1) {
      throw new IllegalArgumentException(MSG_SEVERAL.formatted(spaceName.trim(), describe(byDisplayName)));
    }
    List<Space> byUrlName = spaces.stream().filter(space -> matchesUrlName(space, wanted)).toList();
    if (byUrlName.size() == 1) {
      return byUrlName.get(0);
    } else if (byUrlName.size() > 1) {
      throw new IllegalArgumentException(MSG_SEVERAL.formatted(spaceName.trim(), describe(byUrlName)));
    }
    throw new ObjectNotFoundException(notFoundMessage(spaceName.trim(), wanted, spaces));
  }

  /**
   * @param spaceName the name as the caller gave it, trimmed
   * @param wanted the name, normalized
   * @param spaces the spaces the user is a member of
   * @return the refusal of a name none of them carries, naming the closest
   *         ones
   */
  private String notFoundMessage(String spaceName, String wanted, List<Space> spaces) {
    if (spaces.isEmpty()) {
      return "No space named '%s' among the spaces you're a member of: you aren't a member of any space.".formatted(spaceName);
    }
    List<Space> closest = spaces.stream()
                                .sorted(Comparator.comparingInt((Space space) -> distance(wanted, normalize(space.getDisplayName())))
                                                  .thenComparing(space -> normalize(space.getDisplayName())))
                                .limit(MAX_SUGGESTIONS)
                                .toList();
    return MSG_NOT_FOUND.formatted(spaceName, describe(closest));
  }

  /**
   * @param space a space of the user
   * @param wanted the name, normalized
   * @return true when the name is the space's pretty name or its group id,
   *         ignoring case
   */
  private boolean matchesUrlName(Space space, String wanted) {
    return StringUtils.isNotBlank(space.getPrettyName())
           && (wanted.equals(space.getPrettyName().toLowerCase(Locale.ROOT))
               || wanted.equals((SPACE_GROUP + space.getPrettyName()).toLowerCase(Locale.ROOT)))
           || StringUtils.isNotBlank(space.getGroupId()) && wanted.equals(space.getGroupId().toLowerCase(Locale.ROOT));
  }

  /**
   * Reads every space the user is a member of, a page at a time.
   *
   * @param username the calling user
   * @return the user's spaces, empty when they can't be read
   */
  private List<Space> memberSpaces(String username) {
    List<Space> spaces = new ArrayList<>();
    ListAccess<Space> memberSpaces = spaceService.getMemberSpaces(username);
    if (memberSpaces == null) {
      return spaces;
    }
    try {
      int size = memberSpaces.getSize();
      for (int offset = 0; offset < size; offset += PAGE_SIZE) {
        Space[] page = memberSpaces.load(offset, Math.min(PAGE_SIZE, size - offset));
        if (page == null || page.length == 0) {
          break;
        }
        for (Space space : page) {
          if (space != null) {
            spaces.add(space);
          }
        }
      }
    } catch (Exception e) {
      throw new IllegalStateException("Your spaces can't be read right now, so the space can't be found by its name. Call again with its space_id.",
                                      e);
    }
    return spaces;
  }

  /**
   * @param spaces spaces to name
   * @return each space's display name and id, for the caller to pick from
   */
  private static String describe(List<Space> spaces) {
    return spaces.stream()
                 .map(space -> "'%s' (space_id %s)".formatted(space.getDisplayName(), space.getId()))
                 .collect(Collectors.joining(", "));
  }

  /**
   * @param name a space name
   * @return the name in lower case, trimmed, with each run of inner
   *         whitespace made a single space; empty for null
   */
  private static String normalize(String name) {
    return StringUtils.normalizeSpace(StringUtils.defaultString(name)).toLowerCase(Locale.ROOT);
  }

  /**
   * How far a space's name is from the wanted one: 0 when one holds the
   * other, else their edit distance plus one, so that a name holding the
   * wanted one ranks first.
   *
   * @param wanted the wanted name, normalized
   * @param candidate a space's name, normalized
   * @return the distance, the lower the closer
   */
  private static int distance(String wanted, String candidate) {
    if (!candidate.isEmpty() && (candidate.contains(wanted) || wanted.contains(candidate))) {
      return 0;
    }
    return levenshtein(wanted, candidate) + 1;
  }

  /**
   * @param left a string
   * @param right another string
   * @return the number of single-character insertions, deletions and
   *         substitutions turning one into the other
   */
  private static int levenshtein(String left, String right) {
    int[] previous = new int[right.length() + 1];
    int[] current = new int[right.length() + 1];
    for (int j = 0; j <= right.length(); j++) {
      previous[j] = j;
    }
    for (int i = 1; i <= left.length(); i++) {
      current[0] = i;
      for (int j = 1; j <= right.length(); j++) {
        int substitution = previous[j - 1] + (left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1);
        current[j] = Math.min(substitution, Math.min(previous[j], current[j - 1]) + 1);
      }
      int[] swap = previous;
      previous = current;
      current = swap;
    }
    return previous[right.length()];
  }

}
