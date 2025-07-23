/**
 * Copyright (C) 2025 eXo Platform SAS
 *
 *  This program is free software: you can redistribute it and/or modify
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
package org.exoplatform.documents.webdav.model;

import lombok.Getter;

@Getter
public class WebDavException extends Exception {

  private static final long serialVersionUID = -8695436948939218084L;

  private final int         httpError;

  public WebDavException(int httpError, String message) {
    super(message);
    this.httpError = httpError;
  }

  public WebDavException(int httpError, String message, Throwable cause) {
    super(message, cause);
    this.httpError = httpError;
  }

  public WebDavException(int httpError, Throwable cause) {
    this(httpError, cause.getMessage(), cause);
  }

}
