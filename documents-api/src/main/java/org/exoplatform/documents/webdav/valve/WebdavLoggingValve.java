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
package org.exoplatform.documents.webdav.valve;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import org.apache.catalina.Context;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ValveBase;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.coyote.ContinueResponseTiming;

import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

public class WebdavLoggingValve extends ValveBase {

  private static final Log LOG = ExoLogger.getLogger(WebdavLoggingValve.class);

  @Override
  public void invoke(Request request, Response response) throws IOException, ServletException {
    if (LOG.isDebugEnabled()) {
      UUID reqUuid = UUID.randomUUID();
      try { // NOSONAR
        LOG.debug("[{}] URI: {} - Method {}", reqUuid, request.getRequestURI(), request.getMethod());

        ByteArrayInputStream arrayInputStream = null;
        try (InputStream inputStream = request.getInputStream()) {
          arrayInputStream = new ByteArrayInputStream(IOUtils.toByteArray(inputStream));
        }

        if (arrayInputStream.available() > 0
            && (StringUtils.contains(request.getContentType(), "text/")
                || StringUtils.equals(request.getContentType(), "application/xml")
                || StringUtils.equals(request.getContentType(), "application.json/"))) {
          byte[] bytes = arrayInputStream.readAllBytes();
          arrayInputStream.reset();
          LOG.trace("[{}] + Request Body: {}", reqUuid, new String(bytes));
        }

        // Create a custom response wrapper
        ResponseWrapper wrappedResponse = new ResponseWrapper(response);

        // Continue the processing chain, allowing content to be written to the
        // wrapped response
        getNext().invoke(request, wrappedResponse);

        byte[] responseBytes = wrappedResponse.getBufferedContent();
        if (LOG.isTraceEnabled()
            && StringUtils.contains(response.getContentType(), "text/")
            && responseBytes.length > 0) {
          LOG.trace("[{}] + Response Body: {}", reqUuid, new String(responseBytes));
        }
        if (responseBytes.length > 0) {
          try (ServletOutputStream responseOutputStream = response.getOutputStream()) {
            responseOutputStream.write(responseBytes);
          }
        }
      } finally {
        LOG.debug("[{}] - Response Status: {}", reqUuid, response.getStatus());
        Collection<String> headerNames = response.getHeaderNames();
        for (String h : headerNames) {
          LOG.debug("[{}] - Response Header: {}: {}", reqUuid, h, response.getHeader(h));
        }
      }
    } else {
      getNext().invoke(request, response);
    }
  }

  private class ResponseWrapper extends Response {

    private final ByteArrayOutputStream buffer             = new ByteArrayOutputStream();

    private final ServletOutputStream   bufferOutputStream = newServletOutputStreamWrapper();

    private PrintWriter                 bufferWriter;

    private HttpServletResponseWrapper  bufferResponse;

    private Response                    response;

    public ResponseWrapper(Response response) {
      this.response = response;
    }

    @Override
    public ServletOutputStream getOutputStream() throws IOException {
      return bufferOutputStream;
    }

    @Override
    public PrintWriter getWriter() throws IOException {
      if (bufferWriter == null) {
        bufferWriter = new PrintWriter(buffer);
      }
      return bufferWriter;
    }

    @Override
    public void setCoyoteResponse(org.apache.coyote.Response coyoteResponse) {
      response.setCoyoteResponse(coyoteResponse);
    }

    @Override
    public org.apache.coyote.Response getCoyoteResponse() {
      return response.getCoyoteResponse();
    }

    @Override
    public Context getContext() {
      return response.getContext();
    }

    @Override
    public void recycle() {
      response.recycle();
    }

    @Override
    public List<Cookie> getCookies() {
      return response.getCookies();
    }

    @Override
    public long getContentWritten() {
      return response.getContentWritten();
    }

    @Override
    public long getBytesWritten(boolean flush) {
      return response.getBytesWritten(flush);
    }

    @Override
    public void setAppCommitted(boolean appCommitted) {
      response.setAppCommitted(appCommitted);
    }

    @Override
    public boolean isAppCommitted() {
      return response.isAppCommitted();
    }

    @Override
    public Request getRequest() {
      return response.getRequest();
    }

    @Override
    public void setRequest(Request request) {
      response.setRequest(request);
    }

    @Override
    public HttpServletResponse getResponse() {
      if (bufferResponse == null) {
        bufferResponse = newHttpServletResponseWrapper(response.getResponse());
      }
      return bufferResponse;
    }

    @Override
    public void setResponse(HttpServletResponse applicationResponse) {
      response.setResponse(applicationResponse);
    }

    @Override
    public void setSuspended(boolean suspended) {
      response.setSuspended(suspended);
    }

    @Override
    public boolean isSuspended() {
      return response.isSuspended();
    }

    @Override
    public boolean isClosed() {
      return response.isClosed();
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean setError() {
      return response.setError();
    }

    @Override
    public String toString() {
      return response.toString();
    }

    @Override
    public boolean isError() {
      return response.isError();
    }

    @Override
    public boolean isErrorReportRequired() {
      return response.isErrorReportRequired();
    }

    @Override
    public boolean setErrorReported() {
      return response.setErrorReported();
    }

    @Override
    public void resetError() {
      response.resetError();
    }

    @Override
    public void finishResponse() throws IOException {
      response.finishResponse();
    }

    @Override
    public int getContentLength() {
      return response.getContentLength();
    }

    @Override
    public String getContentType() {
      return response.getContentType();
    }

    @Override
    public PrintWriter getReporter() throws IOException {
      return response.getReporter();
    }

    @Override
    public void flushBuffer() throws IOException {
      response.flushBuffer();
    }

    @Override
    public int getBufferSize() {
      return response.getBufferSize();
    }

    @Override
    public String getCharacterEncoding() {
      return response.getCharacterEncoding();
    }

    @Override
    public Locale getLocale() {
      return response.getLocale();
    }

    @Override
    public boolean isCommitted() {
      return response.isCommitted();
    }

    @Override
    public void reset() {
      response.reset();
    }

    @Override
    public void resetBuffer() {
      response.resetBuffer();
    }

    @Override
    public void resetBuffer(boolean resetWriterStreamFlags) {
      response.resetBuffer(resetWriterStreamFlags);
    }

    @Override
    public void setBufferSize(int size) {
      response.setBufferSize(size);
    }

    @Override
    public void setContentLength(int length) {
      response.setContentLength(length);
    }

    @Override
    public void setContentLengthLong(long length) {
      response.setContentLengthLong(length);
    }

    @Override
    public void setContentType(String type) {
      response.setContentType(type);
    }

    @Override
    public void setCharacterEncoding(String encoding) {
      response.setCharacterEncoding(encoding);
    }

    @Override
    public void setLocale(Locale locale) {
      response.setLocale(locale);
    }

    @Override
    public String getHeader(String name) {
      return response.getHeader(name);
    }

    @Override
    public Collection<String> getHeaderNames() {
      return response.getHeaderNames();
    }

    @Override
    public Collection<String> getHeaders(String name) {
      return response.getHeaders(name);
    }

    @Override
    public String getMessage() {
      return response.getMessage();
    }

    @Override
    public int getStatus() {
      return response.getStatus();
    }

    @Override
    public void addCookie(Cookie cookie) {
      response.addCookie(cookie);
    }

    @Override
    public void addSessionCookieInternal(Cookie cookie) {
      response.addSessionCookieInternal(cookie);
    }

    @Override
    public String generateCookieString(Cookie cookie) {
      return response.generateCookieString(cookie);
    }

    @Override
    public void addDateHeader(String name, long value) {
      response.addDateHeader(name, value);
    }

    @Override
    public void addHeader(String name, String value) {
      response.addHeader(name, value);
    }

    @Override
    public void addIntHeader(String name, int value) {
      response.addIntHeader(name, value);
    }

    @Override
    public boolean containsHeader(String name) {
      return response.containsHeader(name);
    }

    @Override
    public void setTrailerFields(Supplier<Map<String, String>> supplier) {
      response.setTrailerFields(supplier);
    }

    @Override
    public Supplier<Map<String, String>> getTrailerFields() {
      return response.getTrailerFields();
    }

    @Override
    public String encodeRedirectURL(String url) {
      return response.encodeRedirectURL(url);
    }

    @Override
    public String encodeURL(String url) {
      return response.encodeURL(url);
    }

    @Override
    public void sendAcknowledgement(ContinueResponseTiming continueResponseTiming) throws IOException {
      response.sendAcknowledgement(continueResponseTiming);
    }

    @Override
    public void sendEarlyHints() {
      response.sendEarlyHints();
    }

    @Override
    public void sendError(int status) throws IOException {
      response.sendError(status);
    }

    @Override
    public void sendError(int status, String message) throws IOException {
      response.sendError(status, message);
    }

    @Override
    public void sendRedirect(String location) throws IOException {
      response.sendRedirect(location);
    }

    @Override
    public void sendRedirect(String location, int status) throws IOException {
      response.sendRedirect(location, status);
    }

    @Override
    public void setDateHeader(String name, long value) {
      response.setDateHeader(name, value);
    }

    @Override
    public void setHeader(String name, String value) {
      response.setHeader(name, value);
    }

    @Override
    public void setIntHeader(String name, int value) {
      response.setIntHeader(name, value);
    }

    @Override
    public void setStatus(int status) {
      response.setStatus(status);
    }

    public byte[] getBufferedContent() {
      return buffer.toByteArray();
    }

    private HttpServletResponseWrapper newHttpServletResponseWrapper(HttpServletResponse httpResponse) {
      return new HttpServletResponseWrapper(httpResponse) {
        @Override
        public ServletOutputStream getOutputStream() throws IOException {
          return ResponseWrapper.this.getOutputStream();
        }

        @Override
        public PrintWriter getWriter() throws IOException {
          return ResponseWrapper.this.getWriter();
        }
      };
    }

    private ServletOutputStream newServletOutputStreamWrapper() {
      return new ServletOutputStream() {

        @Override
        public void write(int b) throws IOException {
          buffer.write(b);
        }

        @Override
        public boolean isReady() {
          return true;
        }

        @Override
        public void setWriteListener(WriteListener writeListener) {
          // Noop
        }
      };
    }

  }

}
