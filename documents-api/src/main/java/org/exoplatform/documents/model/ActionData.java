/*
 * Copyright (C) 2023 eXo Platform SAS.
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
 * along with this program. If not, see <gnu.org/licenses>.
 */
package org.exoplatform.documents.model;

import org.exoplatform.services.security.Identity;

import lombok.Data;

import java.util.List;

@Data
public class ActionData {

  private int          actionId;

  private String       status;

  private String       actionType;

  private String       message;

  private int          numberOfItems;

  private List<String> treatedItemsIds;

  private String       downloadZipPath;

  private Identity     identity;

}
