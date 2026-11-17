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
package org.exoplatform.documents.storage;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * A page given by the index of its first row and its size, as the REST
 * listings take them: a page request can only start at a multiple of its
 * size.
 */
record OffsetPageable(long offset, int limit, Sort sort) implements Pageable {

  OffsetPageable {
    if (offset < 0) {
      throw new IllegalArgumentException("The offset must not be negative");
    }
    if (limit < 1) {
      throw new IllegalArgumentException("The limit must be at least 1");
    }
  }

  @Override
  public int getPageNumber() {
    return (int) (offset / limit);
  }

  @Override
  public int getPageSize() {
    return limit;
  }

  @Override
  public long getOffset() {
    return offset;
  }

  @Override
  public Sort getSort() {
    return sort;
  }

  @Override
  public Pageable next() {
    return new OffsetPageable(offset + limit, limit, sort);
  }

  @Override
  public Pageable previousOrFirst() {
    return new OffsetPageable(Math.max(0, offset - limit), limit, sort);
  }

  @Override
  public Pageable first() {
    return new OffsetPageable(0, limit, sort);
  }

  @Override
  public Pageable withPage(int pageNumber) {
    return new OffsetPageable((long) pageNumber * limit, limit, sort);
  }

  @Override
  public boolean hasPrevious() {
    return offset > 0;
  }

}
