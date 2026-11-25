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
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.exoplatform.documents.portlet;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The settings a Document List gadget stores, each with the shape its value
 * must have to be stored.
 */
public final class DocumentGadgetSettings {

  private static final JsonMapper                   MAPPER                 = new JsonMapper();

  private static final Pattern                      IDENTIFIER             = Pattern.compile("[0-9]{1,19}");

  private static final Pattern                      FOLDER_ID              = Pattern.compile("[A-Za-z0-9-]{1,64}");

  private static final int                          MAX_URL_LENGTH         = 2000;

  private static final Pattern                      URL_SCHEME             = Pattern.compile("^([A-Za-z][A-Za-z0-9+.-]*):");

  private static final Pattern                      URL_IGNORED_CHARACTERS = Pattern.compile("[\\t\\n\\r]");

  private static final Pattern                      URL_LEADING_CHARACTERS = Pattern.compile("^[\\x00-\\x20]+");

  private static final int                          MAX_DOCUMENTS_TO_LIST  = 100;

  private static final Set<String>                  VIEW_OPTIONS           = Set.of("list", "cards");

  private static final Set<String>                  DOCUMENT_TYPES         = Set.of("recentDocument", "sharedWithMe", "favorites");

  private static final Map<String, Predicate<String>> VALIDATORS;

  static {
    Map<String, Predicate<String>> validators = new LinkedHashMap<>();
    validators.put("viewOptions", VIEW_OPTIONS::contains);
    validators.put("documentType", DOCUMENT_TYPES::contains);
    validators.put("maxDocumentsToList", DocumentGadgetSettings::isDocumentsCount);
    validators.put("customHeader", DocumentGadgetSettings::isBoolean);
    validators.put("displaySeeMore", DocumentGadgetSettings::isBoolean);
    validators.put("displayAccessDrive", DocumentGadgetSettings::isBoolean);
    validators.put("opensInSameTab", DocumentGadgetSettings::isBoolean);
    validators.put("categoryIds", DocumentGadgetSettings::isCategoryIdsJson);
    validators.put("excludeCategoryIds", DocumentGadgetSettings::isCategoryIdsJson);
    validators.put("selectedFoldersId", value -> value.isEmpty() || FOLDER_ID.matcher(value).matches());
    validators.put("spaceIdentityId", value -> value.isEmpty() || IDENTIFIER.matcher(value).matches());
    validators.put("driveUrl", DocumentGadgetSettings::isDriveUrl);
    VALIDATORS = Collections.unmodifiableMap(validators);
  }

  private DocumentGadgetSettings() {
  }

  /**
   * @param name a posted parameter name
   * @return true when the gadget stores a setting of this name
   */
  public static boolean isKnown(String name) {
    return VALIDATORS.containsKey(name);
  }

  /**
   * @param name a known setting name
   * @param value the posted value
   * @return true when the value has the shape of the setting
   */
  public static boolean isValid(String name, String value) {
    Predicate<String> validator = VALIDATORS.get(name);
    return validator != null && value != null && validator.test(value);
  }

  /**
   * @param name a known setting name
   * @param stored the stored value, possibly null
   * @param defaultValue the value to use when nothing valid is stored
   * @return the stored value when it has the shape of the setting, else the
   *         default value
   */
  public static String valueOrDefault(String name, String stored, String defaultValue) {
    return isValid(name, stored) ? stored : defaultValue;
  }

  private static boolean isBoolean(String value) {
    return "true".equals(value) || "false".equals(value);
  }

  private static boolean isDocumentsCount(String value) {
    if (!Pattern.matches("[0-9]{1,3}", value)) {
      return false;
    }
    return Integer.parseInt(value) <= MAX_DOCUMENTS_TO_LIST;
  }

  private static boolean isCategoryIdsJson(String value) {
    try {
      JsonNode node = MAPPER.readTree(value);
      if (node == null || !node.isArray()) {
        return false;
      }
      for (JsonNode item : node) {
        boolean validItem = item.isIntegralNumber() && item.canConvertToLong() && item.asLong() >= 0
                            || item.isString() && IDENTIFIER.matcher(item.asString()).matches();
        if (!validItem) {
          return false;
        }
      }
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  private static boolean isDriveUrl(String value) {
    if (value.isEmpty()) {
      return true;
    }
    if (value.length() > MAX_URL_LENGTH) {
      return false;
    }
    // read the scheme as a browser does: tabs and line breaks dropped, leading spaces and controls ignored
    String link = URL_LEADING_CHARACTERS.matcher(URL_IGNORED_CHARACTERS.matcher(value).replaceAll("")).replaceFirst("");
    Matcher scheme = URL_SCHEME.matcher(link);
    // a link with no scheme stays as typed; one that names a scheme must be a web link
    return !scheme.find() || "http".equalsIgnoreCase(scheme.group(1)) || "https".equalsIgnoreCase(scheme.group(1));
  }

}
