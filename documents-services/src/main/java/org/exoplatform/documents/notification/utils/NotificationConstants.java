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
}
