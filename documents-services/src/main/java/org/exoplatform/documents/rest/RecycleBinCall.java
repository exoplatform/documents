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
 */
package org.exoplatform.documents.rest;

import javax.ws.rs.core.Response;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.documents.service.RecycleBinService;

/** What an entry of the Recycle Bin asks the service, for the user of the request. */
@FunctionalInterface
interface RecycleBinCall {

  Response.ResponseBuilder apply(RecycleBinService service, String username) throws ObjectNotFoundException, IllegalAccessException;

}
