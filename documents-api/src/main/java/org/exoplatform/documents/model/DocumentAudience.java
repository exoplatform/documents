/*
 * Copyright (C) 2026 eXo Platform SAS
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
package org.exoplatform.documents.model;

import java.util.List;

/**
 * Who a file is for, read from where it is and whom it is shared with
 * (EXO-92126).
 *
 * @param owner the user name of the file's owner, possibly null
 * @param users the user names the file is shared with one by one
 * @param spaceGroupId the group id of the space whose documents hold the file,
 *          or null
 */
public record DocumentAudience(String owner, List<String> users, String spaceGroupId) {
}
