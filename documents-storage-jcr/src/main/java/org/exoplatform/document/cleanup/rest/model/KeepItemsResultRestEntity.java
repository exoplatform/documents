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
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <gnu.org/licenses>.
 */
package org.exoplatform.document.cleanup.rest.model;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/**
 * REST representation of a bulk keep / un-keep outcome: the UI needs the
 * per-item failures to warn instead of reporting a success when nothing was
 * actually kept.
 */
@Data
public class KeepItemsResultRestEntity {

  private int                             succeeded;

  private List<KeepItemFailureRestEntity> failures = new ArrayList<>();

  @Data
  public static class KeepItemFailureRestEntity {

    private long   itemId;

    private String reason;

  }

}
