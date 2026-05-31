/**
 * Copyright (C) 2025 eXo Platform SAS.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see<http://www.gnu.org/licenses/>.
*/
package org.exoplatform.documents.webdav.plugin.impl;

import static org.exoplatform.documents.webdav.model.constant.PropertyConstants.ALLOW_METHODS;
import static org.exoplatform.documents.webdav.model.constant.PropertyConstants.DISPLAYNAME;
import static org.exoplatform.documents.webdav.model.constant.PropertyConstants.REQUEST_INCLUDED_PROPS;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.ws.rs.core.HttpHeaders;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.Strings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.context.ServletContextAware;

import org.exoplatform.documents.webdav.model.Range;
import org.exoplatform.documents.webdav.model.RangedInputStream;
import org.exoplatform.documents.webdav.model.WebDavException;
import org.exoplatform.documents.webdav.model.WebDavFileDownload;
import org.exoplatform.documents.webdav.model.WebDavItem;
import org.exoplatform.documents.webdav.plugin.WebDavHttpMethodPlugin;
import org.exoplatform.services.rest.ExtHttpHeaders;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Setter;
import lombok.SneakyThrows;

@Component
public class GetWebDavHandler extends WebDavHttpMethodPlugin implements ServletContextAware {

  private static final String IMAGE_FILE_PATH     = "/documents-portlet/images/file.png";   // NOSONAR

  private static final String IMAGE_FOLDER_PATH   = "/documents-portlet/images/folder.png"; // NOSONAR

  private static final String HTML_LISTING_FILE   = "/html/get-content.html";

  private static final String ACCEPT_RANGES_BYTES = "bytes";

  public static final String  BOUNDARY            = "1234567890";

  @Setter
  private ServletContext      servletContext;

  @Value("${webdav.cacheControl:no-cache}")
  private String              cacheControl;

  private String              listingHtmlTemplate;

  public GetWebDavHandler() {
    super("GET");
  }

  @PostConstruct
  @SneakyThrows
  public void init() {
    URL resource = servletContext.getResource(HTML_LISTING_FILE);
    if (resource != null) {
      try (InputStream is = resource.openStream()) {
        this.listingHtmlTemplate = IOUtils.toString(is, StandardCharsets.UTF_8);
      }
    }
  }

  @Override
  @SneakyThrows
  public void handle(HttpServletRequest httpRequest, HttpServletResponse httpResponse) throws WebDavException {
    setAccessControlHeaders(httpResponse);

    String resourcePath = getResourcePath(httpRequest);
    String version = httpRequest.getParameter("version");
    List<Range> ranges = parseRanges(httpRequest, httpResponse);
    if (ranges == null) { // Ranges parsing error
      return;
    } else if (!checkModified(httpRequest, resourcePath, version)) {
      httpResponse.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
      return;
    }
    if (documentWebDavService.isFile(resourcePath)) {
      WebDavFileDownload fileDownload = documentWebDavService.download(resourcePath,
                                                                       version,
                                                                       getBaseUrl(httpRequest),
                                                                       httpRequest.getRemoteUser());
      // File content download
      long lastModifiedDate = fileDownload.getLastModifiedDate();
      long contentLength = fileDownload.getContentLength();
      String contentType = fileDownload.getMimeType();
      if (contentLength == 0 || ranges.isEmpty()) {
        // No ranges and no content length
        httpResponse.setHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(contentLength));
        setAcceptRangesHeader(httpResponse);
        setContentTypeHeader(httpResponse, contentType);
        setCacheHeaders(httpResponse, lastModifiedDate);
        httpResponse.setStatus(HttpServletResponse.SC_OK);
        writeResponseStream(httpResponse, fileDownload);
      } else if (ranges.size() == 1) {
        // Requested a single range
        Range range = ranges.get(0);
        if (!validateRange(range, contentLength)) {
          httpResponse.setHeader(ExtHttpHeaders.CONTENTRANGE, "bytes */" + contentLength);
          httpResponse.setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
        } else {
          httpResponse.setHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(range.getEnd() - range.getStart() + 1));
          httpResponse.setHeader(ExtHttpHeaders.CONTENTRANGE,
                                 "bytes " + range.getStart() + "-" + range.getEnd() + "/" + contentLength);
          setAcceptRangesHeader(httpResponse);
          setContentTypeHeader(httpResponse, contentType);
          setCacheHeaders(httpResponse, lastModifiedDate);
          httpResponse.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
          writeResponseRange(httpResponse, fileDownload, range);
        }
      } else {
        // Requested a set of ranges
        httpResponse.setHeader(HttpHeaders.CONTENT_TYPE, ExtHttpHeaders.MULTIPART_BYTERANGES + BOUNDARY);
        httpResponse.setDateHeader(HttpHeaders.LAST_MODIFIED, lastModifiedDate);
        setAcceptRangesHeader(httpResponse);
        writeResponseRanges(httpResponse, fileDownload, ranges);
      }
    } else {
      // Folder listing
      WebDavItem webDavItem = documentWebDavService.get(resourcePath,
                                                        REQUEST_INCLUDED_PROPS,
                                                        Collections.singleton(DISPLAYNAME),
                                                        false,
                                                        1,
                                                        getBaseUrl(httpRequest),
                                                        httpRequest.getRemoteUser());
      httpResponse.setHeader(HttpHeaders.CONTENT_TYPE, MimeTypeUtils.TEXT_HTML_VALUE);
      httpResponse.setStatus(HttpServletResponse.SC_OK);
      writeResponseHtml(httpResponse, webDavItem);
    }
  }

  private void writeResponseHtml(HttpServletResponse httpResponse, WebDavItem webDavItem) throws IOException {
    StringBuilder builder = new StringBuilder();
    builder.append(String.format("<a href=\"./..\"><img src=\"%s\" alt=\"\"> ..</a>", IMAGE_FOLDER_PATH));
    builder.append("<br>");
    if (CollectionUtils.isNotEmpty(webDavItem.getChildren())) {
      webDavItem.getChildren()
                .stream()
                .map(item -> String.format("<a href=\"%s\"><img src=\"%s\" alt=\"\"> %s</a><br>",
                                           item.getIdentifier(),
                                           item.isFile() ? IMAGE_FILE_PATH : IMAGE_FOLDER_PATH,
                                           item.getProperty(DISPLAYNAME).getValue()))
                .forEach(builder::append);
    }
    String content = this.listingHtmlTemplate.replace("@Content@", builder.toString());
    try (ServletOutputStream outputStream = httpResponse.getOutputStream()) {
      IOUtils.write(content, outputStream, StandardCharsets.UTF_8);
    }
  }

  private void writeResponseStream(HttpServletResponse httpResponse, WebDavFileDownload fileDownload) throws IOException {
    try (ServletOutputStream outputStream = httpResponse.getOutputStream();
        InputStream inputStream = fileDownload.getInputStream();) {
      IOUtils.copy(inputStream, outputStream);
    }
  }

  private void writeResponseRanges(HttpServletResponse httpResponse,
                                   WebDavFileDownload fileDownload,
                                   List<Range> ranges) throws IOException {
    String contentType = fileDownload.getMimeType();
    long contentLength = fileDownload.getContentLength();
    try (ServletOutputStream outputStream = httpResponse.getOutputStream();
        InputStream inputStream = fileDownload.getInputStream();) {
      // multipart byte ranges as byte:0-100,80-150,210-300
      if (ranges.stream().anyMatch(range -> !validateRange(range, contentLength))) {
        httpResponse.setHeader(ExtHttpHeaders.CONTENTRANGE, "bytes */" + contentLength);
        httpResponse.setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
      } else {
        httpResponse.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
        writeResponseRanges(inputStream, outputStream, contentLength, contentType, ranges);
      }
    }
  }

  private void writeResponseRange(HttpServletResponse httpResponse,
                                  WebDavFileDownload fileDownload,
                                  Range range) throws IOException {
    try (ServletOutputStream outputStream = httpResponse.getOutputStream();
        InputStream inputStream = fileDownload.getInputStream();) {
      IOUtils.copy(new RangedInputStream(inputStream, range.getStart(), range.getEnd()), outputStream);
    }
  }

  public void writeResponseRanges(InputStream inputStream,
                                  OutputStream outputStream,
                                  long contentLength,
                                  String contentType,
                                  List<Range> ranges) throws IOException {
    for (Range range : ranges) {
      println(outputStream);
      // boundary
      print("--" + BOUNDARY, outputStream);
      println(outputStream);
      // content-type
      print(HttpHeaders.CONTENT_TYPE + ": " + contentType, outputStream);
      println(outputStream);
      // current range
      print(ExtHttpHeaders.CONTENTRANGE + ": bytes " + range.getStart() + "-" + range.getEnd() + "/" + contentLength,
            outputStream);
      println(outputStream);
      println(outputStream);
      // range data
      RangedInputStream rangedInputStream = new RangedInputStream(inputStream, range.getStart(), range.getEnd());
      byte[] buff = new byte[0x1024];
      int rd = -1;
      while ((rd = rangedInputStream.read(buff)) != -1) {
        outputStream.write(buff, 0, rd);
      }
      rangedInputStream.close();
    }
    println(outputStream);
    print("--" + BOUNDARY + "--", outputStream);
    println(outputStream);
  }

  private void setAccessControlHeaders(HttpServletResponse httpResponse) {
    httpResponse.setHeader("Access-Control-Allow-Origin", "*");
    httpResponse.setHeader("Access-Control-Allow-Credentials", "true");
    httpResponse.setHeader("Access-Control-Allow-Methods", ALLOW_METHODS);
    httpResponse.setHeader("Access-Control-Allow-Headers", "*");
    httpResponse.setHeader("Access-Control-Expose-Header", "DAV, content-length, Allow");
    httpResponse.setHeader("Access-Control-Max-Age", "3600");
  }

  private void setAcceptRangesHeader(HttpServletResponse httpResponse) {
    httpResponse.setHeader(ExtHttpHeaders.ACCEPT_RANGES, ACCEPT_RANGES_BYTES);
  }

  private void setContentTypeHeader(HttpServletResponse httpResponse, String contentType) {
    httpResponse.setHeader(HttpHeaders.CONTENT_TYPE, contentType);
  }

  private void setCacheHeaders(HttpServletResponse httpResponse, long lastModifiedDate) {
    httpResponse.setHeader(HttpHeaders.ETAG, String.format("W/%s", lastModifiedDate));
    httpResponse.setHeader(HttpHeaders.CACHE_CONTROL, cacheControl);
    httpResponse.setDateHeader(HttpHeaders.LAST_MODIFIED, lastModifiedDate);
  }

  private void print(String s, OutputStream ostream) throws IOException {
    int length = s.length();
    for (int i = 0; i < length; i++) {
      char c = s.charAt(i);
      ostream.write(c);
    }
  }

  private void println(OutputStream ostream) throws IOException {
    ostream.write('\r');
    ostream.write('\n');
  }

  private List<Range> parseRanges(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
    String rangeHeader = httpRequest.getHeader(ExtHttpHeaders.RANGE);
    if (Strings.CI.startsWith(rangeHeader, "bytes=")) {
      List<Range> ranges = new ArrayList<>();
      String rangeString = rangeHeader.substring(rangeHeader.indexOf("=") + 1);

      String[] tokens = rangeString.split(",");
      for (String token : tokens) {
        Range range = new Range();
        token = token.trim();
        int dash = token.indexOf("-");
        if (dash == -1) {
          writeResponse(httpResponse,
                        HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE,
                        "Requested Range Not Satisfiable");
          return null; // NOSONAR
        } else if (dash == 0) {
          range.setStart(Long.parseLong(token));
          range.setEnd(-1L);
        } else if (dash > 0) {
          range.setStart(Long.parseLong(token.substring(0, dash)));
          if (dash < token.length() - 1)
            range.setEnd(Long.parseLong(token.substring(dash + 1, token.length())));
          else
            range.setEnd(-1L);
        }
        ranges.add(range);
      }
      return ranges;
    } else {
      return Collections.emptyList();
    }
  }

  private boolean validateRange(Range range, long contentLength) {
    long start = range.getStart();
    long end = range.getEnd();

    // range set as bytes:-100
    // take 100 bytes from end
    if (start < 0 && end == -1) {
      if ((-1 * start) >= contentLength) {
        start = 0;
        end = contentLength - 1;
      } else {
        start = contentLength + start;
        end = contentLength - 1;
      }
    }

    // range set as bytes:100-
    // take from 100 to the end
    if (start >= 0 && end == -1) {
      end = contentLength - 1;
    }

    // normal range set as bytes:100-200
    // end can be greater then content-length
    if (end >= contentLength) {
      end = contentLength - 1;
    }

    if (start >= 0 && end >= 0 && start <= end) {
      range.setStart(start);
      range.setEnd(end);
      return true;
    }
    return false;
  }

}
