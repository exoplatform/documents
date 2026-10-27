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
import java.util.List;

import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;

/**
 * Strips a PDF's text in one pass, noting where each page starts in the text
 * written: PDFBox walks every page each time it strips, so stripping the
 * pages one call each would walk the document once per page.
 */
class PageStartRecordingStripper extends PDFTextStripper {

  private final CountingWriter out;

  private final List<Long>     pageStarts;

  /**
   * Builds the stripper.
   *
   * @param out the writer the text is written to, whose count gives where a
   *          page starts
   * @param pageStarts where each page's start is added, the first page first
   * @throws IOException never, PDFBox declares it
   */
  PageStartRecordingStripper(CountingWriter out, List<Long> pageStarts) throws IOException {
    this.out = out;
    this.pageStarts = pageStarts;
  }

  /**
   * Notes where a page starts, and the pages before it PDFBox skipped for
   * having no content, which start where it does.
   *
   * @param page the page
   * @throws IOException when the page start can't be written
   */
  @Override
  protected void startPage(PDPage page) throws IOException {
    fillUpTo(getCurrentPageNo());
    super.startPage(page);
  }

  /**
   * Notes the start of the pages up to a page number not noted yet, at the
   * text written so far.
   *
   * @param pageNumber the page number, from 1
   */
  void fillUpTo(int pageNumber) {
    while (pageStarts.size() < pageNumber) {
      pageStarts.add(out.count());
    }
  }
}
