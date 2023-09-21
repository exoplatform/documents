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
package org.exoplatform.documents.notification.utils;

import org.exoplatform.commons.api.notification.model.ArgumentLiteral;

public class NotificationConstants {

  public static final ArgumentLiteral<String> FROM_USER     = new ArgumentLiteral<>(String.class, "FROM_USER");

  public static final ArgumentLiteral<String> DOCUMENT_URL  = new ArgumentLiteral<>(String.class, "DOCUMENT_URL");

  public static final ArgumentLiteral<String> DOCUMENT_NAME = new ArgumentLiteral<>(String.class, "DOCUMENT_NAME");

  public static final ArgumentLiteral<String> RECEIVERS     = new ArgumentLiteral<>(String.class, "RECEIVERS");

  public static final ArgumentLiteral<String> FOLDER_URL       = new ArgumentLiteral<>(String.class, "FOLDER_URL");

  public static final ArgumentLiteral<String> FOLDER_NAME      = new ArgumentLiteral<>(String.class, "FOLDER_NAME");

  public static final ArgumentLiteral<String> TOTAL_NUMBER     = new ArgumentLiteral<>(String.class, "TOTAL_NUMBER");

  public static final ArgumentLiteral<String> DURATION         = new ArgumentLiteral<>(String.class, "DURATION");

  public static final ArgumentLiteral<String> FILES_CREATED    = new ArgumentLiteral<>(String.class, "FILES_CREATED");

  public static final ArgumentLiteral<String> FILES_DUPLICATED = new ArgumentLiteral<>(String.class, "FILES_DUPLICATED");

  public static final ArgumentLiteral<String> FILES_UPDATED    = new ArgumentLiteral<>(String.class, "FILES_UPDATED");

  public static final ArgumentLiteral<String> FILES_IGNORED    = new ArgumentLiteral<>(String.class, "FILES_IGNORED");

  public static final ArgumentLiteral<String> FILES_FAILED     = new ArgumentLiteral<>(String.class, "FILES_FAILED");

}
