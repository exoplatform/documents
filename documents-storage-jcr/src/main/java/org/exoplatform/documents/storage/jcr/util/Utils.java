/*
 * Copyright (C) 2022 eXo Platform SAS.
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
package org.exoplatform.documents.storage.jcr.util;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.RepositoryException;

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

public class Utils {

  private static final Log LOG = ExoLogger.getLogger(Utils.class);

  public static <S, D> void broadcast(ListenerService listenerService, String eventName, S source, D data) {
    try {
      listenerService.broadcast(eventName, source, data);
    } catch (Exception e) {
      LOG.error("Error while broadcasting event: {}", eventName, e);
    }
  }

  public static String getStringProperty(Node node, String propertyName) throws RepositoryException {
    if (node.hasProperty(propertyName)) {
      Property property = node.getProperty(propertyName);
      if (property.getDefinition().isMultiple()) {
        if (property.getValues().length >= 1) {
          return property.getValues()[0].getString();
        }
      } else {
        return property.getString();
      }
    }
    return "";
  }

  public static String decodeString(String value) {
    String currentValue;
    do {
      currentValue = value;
      try {
        value = URLDecoder.decode(value, StandardCharsets.UTF_8);
      } catch (IllegalArgumentException e) {
        if (LOG.isDebugEnabled()) {
          LOG.warn("Unable to decode value: {}. Return original value",
                   value,
                   e);
        } else {
          LOG.warn("Unable to decode value: {}, error: {}. Return original value.",
                   value,
                   e.getMessage());
        }
        return value;
      }
    } while (!StringUtils.equals(currentValue, value));
    return value;
  }

  public static String encodeNodeName(String name) {
    return name.replace("|", "%7C")
               .replace("[", "%5b")
               .replace("]", "%5d")
               .replace("*", "%2a");
  }

  /**
   * Decodes a URL-encoded value while preserving literal "+" characters.
   * URLDecoder.decode() follows application/x-www-form-urlencoded rules and
   * turns "+" into a space, which is wrong for URL path segments (and file or
   * folder names) where "+" is a literal character - e.g. "test + test.pdf".
   * Escaping "+" to "%2B" before decoding keeps it intact while still decoding
   * genuine percent-encoded sequences (e.g. "%5b" -> "["). The operation is
   * idempotent, so it stays correct even when a value is decoded more than once
   * (the WebDAV path pipeline decodes segments at several layers).
   *
   * @param value URL-encoded value (may be null)
   * @return decoded value with literal "+" preserved, or null if the input was null
   */
  public static String decodeUrlPreservingPlus(String value) {
    if (value == null) {
      return null;
    }
    return URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8);
  }

}
