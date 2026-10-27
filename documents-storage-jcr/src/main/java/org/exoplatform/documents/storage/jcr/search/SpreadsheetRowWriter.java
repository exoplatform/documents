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
package org.exoplatform.documents.storage.jcr.search;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler.SheetContentsHandler;
import org.apache.poi.xssf.usermodel.XSSFComment;

/**
 * Writes a spreadsheet's rows as they are streamed, one line a row, its
 * cells' values separated by " | ", so a sheet is never held in memory.
 * The writer refusing more text is raised as an {@link UncheckedIOException},
 * the streaming handler declaring none, its cause being the writer's own.
 */
class SpreadsheetRowWriter implements SheetContentsHandler {

  private final CountingWriter out;

  private final List<String>   cells = new ArrayList<>();

  /**
   * Builds the row writer.
   *
   * @param out where the rows are written
   */
  SpreadsheetRowWriter(CountingWriter out) {
    this.out = out;
  }

  /**
   * Starts a row.
   *
   * @param rowNumber the row's number, from 0
   */
  @Override
  public void startRow(int rowNumber) {
    cells.clear();
  }

  /**
   * Writes the row's cells on one line, nothing for a row without values,
   * failing once the deadline is passed either way.
   *
   * @param rowNumber the row's number, from 0
   */
  @Override
  public void endRow(int rowNumber) {
    try {
      // checked on every row, a sheet of rows without values writing nothing that would check it
      out.checkDeadline();
      if (cells.isEmpty()) {
        return;
      }
      out.write(String.join(" | ", cells));
      out.write('\n');
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Keeps a cell's value as the spreadsheet shows it.
   *
   * @param cellReference the cell's reference, as A1
   * @param formattedValue its value, formatted
   * @param comment its comment, null for none
   */
  @Override
  public void cell(String cellReference, String formattedValue, XSSFComment comment) {
    String value = StringUtils.normalizeSpace(formattedValue);
    if (StringUtils.isNotEmpty(value)) {
      cells.add(value);
    }
  }
}
