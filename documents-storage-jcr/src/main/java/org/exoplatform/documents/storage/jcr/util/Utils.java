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

import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import java.util.Comparator;

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
      return node.getProperty(propertyName).getString();
    }
    return "";
  }

  public static class NaturalComparator implements Comparator<String> {
    @Override
    public int compare(String s1, String s2) {
      /*
      Split string by matching a position where a non-digit (\\D) precedes and a digit (\\d) follows or vice versa
      (?<=) matches the position immediately following a non-digit (\\D) or a digit (\\d) character
      (?=) matches the position immediately preceding a non-digit (\\D) or a digit (\\d) character
       */
      String[] arr1 = s1.split("(?<=\\D)(?=\\d)|(?<=\\d)(?=\\D)");
      String[] arr2 = s2.split("(?<=\\D)(?=\\d)|(?<=\\d)(?=\\D)");
      int i = 0;
      while (i < arr1.length && i < arr2.length) {
        if (Character.isDigit(arr1[i].charAt(0)) && Character.isDigit(arr2[i].charAt(0))) {
          int num1 = Integer.parseInt(arr1[i]);
          int num2 = Integer.parseInt(arr2[i]);
          if (num1 != num2) {
            return Integer.compare(num1, num2);
          }
        } else {
          int result = arr1[i].compareTo(arr2[i]);
          if (result != 0) {
            return result;
          }
        }
        i++;
      }
      return s1.compareToIgnoreCase(s2);
    }
  }
}
