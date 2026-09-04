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

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.security.Principal;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import org.apache.catalina.Context;
import org.apache.catalina.Host;
import org.apache.catalina.Session;
import org.apache.catalina.Wrapper;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.core.AsyncContextImpl;
import org.apache.catalina.mapper.MappingData;
import org.apache.catalina.valves.ValveBase;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.coyote.ContinueResponseTiming;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.http.ServerCookies;

import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletConnection;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletMapping;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpUpgradeHandler;
import jakarta.servlet.http.Part;
import jakarta.servlet.http.PushBuilder;

public class WebdavLoggingValve extends ValveBase {

  private static final Log LOG = ExoLogger.getLogger(WebdavLoggingValve.class);

  @Override
  public void invoke(Request request, Response response) throws IOException, ServletException { // NOSONAR
    if (LOG.isDebugEnabled()) {
      UUID reqUuid = UUID.randomUUID();
      try { // NOSONAR
        LOG.debug("[{}] URI: {} - Method {}", reqUuid, request.getRequestURI(), request.getMethod());
        Enumeration<String> paramNames = request.getParameterNames();
        while (paramNames.hasMoreElements()) {
          String p = paramNames.nextElement();
          LOG.debug("[{}] - Request Param: {}: {}", reqUuid, p, StringUtils.join(request.getParameterValues(p), ", "));
        }
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
          String h = headerNames.nextElement();
          LOG.debug("[{}] - Request Header: {}: {}", reqUuid, h, request.getHeader(h));
        }

        ByteArrayInputStream arrayInputStream = null;
        try (InputStream inputStream = request.getInputStream()) {
          arrayInputStream = new ByteArrayInputStream(IOUtils.toByteArray(inputStream));
        }

        if (arrayInputStream.available() > 0
            && (arrayInputStream.available() < 2048
                || Strings.CS.contains(request.getContentType(), "text/")
                || Strings.CS.equals(request.getContentType(), "application/xml")
                || Strings.CS.equals(request.getContentType(), "application.json/"))) {
          byte[] bytes = arrayInputStream.readAllBytes();
          arrayInputStream.reset();
          LOG.debug("[{}] + Request Body: {}", reqUuid, new String(bytes));
        }

        // Create a custom response and request wrappers
        RequestWrapper wrappedRequest = new RequestWrapper(request, arrayInputStream);
        ResponseWrapper wrappedResponse = new ResponseWrapper(response);

        // Continue the processing chain, allowing content to be written to the
        // wrapped response
        getNext().invoke(wrappedRequest, wrappedResponse);

        byte[] responseBytes = wrappedResponse.getBufferedContent();
        if (LOG.isTraceEnabled()
            && Strings.CS.contains(response.getContentType(), "text/")
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
      // Continue the processing chain, allowing content to be written to the
      // wrapped response
      getNext().invoke(request, response);
    }
  }

  private class RequestWrapper extends Request {

    private Request              request;

    private ByteArrayInputStream arrayInputStream;

    public RequestWrapper(Request request, ByteArrayInputStream arrayInputStream) {
      super(request.getConnector(), request.getCoyoteRequest());
      this.request = request;
      this.arrayInputStream = arrayInputStream;
    }

    @Override
    public String toString() {
      return request.toString();
    }

    @Override
    public void recycleSessionInfo() {
      request.recycleSessionInfo();
    }

    @Override
    public void setMaxParameterCount(int maxParameterCount) {
      request.setMaxParameterCount(maxParameterCount);
    }

    @Override
    public void setMaxPartCount(int maxPartCount) {
      request.setMaxPartCount(maxPartCount);
    }

    @Override
    public void setMaxPartHeaderSize(int maxPartHeaderSize) {
      request.setMaxPartHeaderSize(maxPartHeaderSize);
    }

    @Override
    public void setCharacterEncoding(Charset charset) {
      request.setCharacterEncoding(charset);
    }

    @Override
    public org.apache.coyote.Request getCoyoteRequest() {
      return request.getCoyoteRequest();
    }

    @Override
    public void addPathParameter(String name, String value) {
      request.addPathParameter(name, value);
    }

    @Override
    public String getPathParameter(String name) {
      return request.getPathParameter(name);
    }

    @Override
    public void setAsyncSupported(boolean asyncSupported) {
      request.setAsyncSupported(asyncSupported);
    }

    @Override
    public void recycle() {
      request.recycle();
    }

    @Override
    public Connector getConnector() {
      return request.getConnector();
    }

    @Override
    public Context getContext() {
      return request.getContext();
    }

    @Override
    public boolean getDiscardFacades() {
      return request.getDiscardFacades();
    }

    @Override
    public FilterChain getFilterChain() {
      return request.getFilterChain();
    }

    @Override
    public void setFilterChain(FilterChain filterChain) {
      request.setFilterChain(filterChain);
    }

    @Override
    public Host getHost() {
      return request.getHost();
    }

    @Override
    public MappingData getMappingData() {
      return request.getMappingData();
    }

    @Override
    public HttpServletRequest getRequest() {
      return newHttpServletRequestWrapper(request.getRequest(), arrayInputStream);
    }

    @Override
    public void setRequest(HttpServletRequest applicationRequest) {
      request.setRequest(applicationRequest);
    }

    @Override
    public Response getResponse() {
      return request.getResponse();
    }

    @Override
    public void setResponse(Response response) {
      request.setResponse(response);
    }

    @Override
    public InputStream getStream() {
      return request.getStream();
    }

    @Override
    public Wrapper getWrapper() {
      return request.getWrapper();
    }

    @Override
    public ServletInputStream createInputStream() throws IOException {
      return request.createInputStream();
    }

    @Override
    public void finishRequest() throws IOException {
      request.finishRequest();
    }

    @Override
    public Object getNote(String name) {
      return request.getNote(name);
    }

    @Override
    public void removeNote(String name) {
      request.removeNote(name);
    }

    @Override
    public void setLocalPort(int port) {
      request.setLocalPort(port);
    }

    @Override
    public void setNote(String name, Object value) {
      request.setNote(name, value);
    }

    @Override
    public void setRemoteAddr(String remoteAddr) {
      request.setRemoteAddr(remoteAddr);
    }

    @Override
    public void setRemoteHost(String remoteHost) {
      request.setRemoteHost(remoteHost);
    }

    @Override
    public void setSecure(boolean secure) {
      request.setSecure(secure);
    }

    @Override
    public void setServerPort(int port) {
      request.setServerPort(port);
    }

    @Override
    public Object getAttribute(String name) {
      return request.getAttribute(name);
    }

    @Override
    public long getContentLengthLong() {
      return request.getContentLengthLong();
    }

    @Override
    public Enumeration<String> getAttributeNames() {
      return request.getAttributeNames();
    }

    @Override
    public String getCharacterEncoding() {
      return request.getCharacterEncoding();
    }

    @Override
    public int getContentLength() {
      return request.getContentLength();
    }

    @Override
    public String getContentType() {
      return request.getContentType();
    }

    @Override
    public void setContentType(String contentType) {
      request.setContentType(contentType);
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
      return request.getInputStream();
    }

    @Override
    public Locale getLocale() {
      return request.getLocale();
    }

    @Override
    public Enumeration<Locale> getLocales() {
      return request.getLocales();
    }

    @Override
    public String getParameter(String name) {
      return request.getParameter(name);
    }

    @Override
    public Map<String, String[]> getParameterMap() {
      return request.getParameterMap();
    }

    @Override
    public Enumeration<String> getParameterNames() {
      return request.getParameterNames();
    }

    @Override
    public String[] getParameterValues(String name) {
      return request.getParameterValues(name);
    }

    @Override
    public String getProtocol() {
      return request.getProtocol();
    }

    @Override
    public BufferedReader getReader() throws IOException {
      return request.getReader();
    }

    @Override
    public String getRemoteAddr() {
      return request.getRemoteAddr();
    }

    @Override
    public String getPeerAddr() {
      return request.getPeerAddr();
    }

    @Override
    public String getRemoteHost() {
      return request.getRemoteHost();
    }

    @Override
    public int getRemotePort() {
      return request.getRemotePort();
    }

    @Override
    public String getLocalName() {
      return request.getLocalName();
    }

    @Override
    public String getLocalAddr() {
      return request.getLocalAddr();
    }

    @Override
    public int getLocalPort() {
      return request.getLocalPort();
    }

    @Override
    public RequestDispatcher getRequestDispatcher(String path) {
      return request.getRequestDispatcher(path);
    }

    @Override
    public String getScheme() {
      return request.getScheme();
    }

    @Override
    public String getServerName() {
      return request.getServerName();
    }

    @Override
    public int getServerPort() {
      return request.getServerPort();
    }

    @Override
    public boolean isSecure() {
      return request.isSecure();
    }

    @Override
    public void removeAttribute(String name) {
      request.removeAttribute(name);
    }

    @Override
    public void setAttribute(String name, Object value) {
      request.setAttribute(name, value);
    }

    @Override
    public void setCharacterEncoding(String enc) throws UnsupportedEncodingException {
      request.setCharacterEncoding(enc);
    }

    @Override
    public ServletContext getServletContext() {
      return request.getServletContext();
    }

    @Override
    public AsyncContext startAsync() {
      return request.startAsync();
    }

    @Override
    public AsyncContext startAsync(ServletRequest request, ServletResponse response) {
      return request.startAsync(request, response);
    }

    @Override
    public boolean isAsyncStarted() {
      return request.isAsyncStarted();
    }

    @Override
    public boolean isAsyncDispatching() {
      return request.isAsyncDispatching();
    }

    @Override
    public boolean isAsyncCompleting() {
      return request.isAsyncCompleting();
    }

    @Override
    public boolean isAsync() {
      return request.isAsync();
    }

    @Override
    public boolean isAsyncSupported() {
      return request.isAsyncSupported();
    }

    @Override
    public AsyncContext getAsyncContext() {
      return request.getAsyncContext();
    }

    @Override
    public AsyncContextImpl getAsyncContextInternal() {
      return request.getAsyncContextInternal();
    }

    @Override
    public DispatcherType getDispatcherType() {
      return request.getDispatcherType();
    }

    @Override
    public String getRequestId() {
      return request.getRequestId();
    }

    @Override
    public String getProtocolRequestId() {
      return request.getProtocolRequestId();
    }

    @Override
    public ServletConnection getServletConnection() {
      return request.getServletConnection();
    }

    @Override
    public void addCookie(Cookie cookie) {
      request.addCookie(cookie);
    }

    @Override
    public void addLocale(Locale locale) {
      request.addLocale(locale);
    }

    @Override
    public void clearCookies() {
      request.clearCookies();
    }

    @Override
    public void clearLocales() {
      request.clearLocales();
    }

    @Override
    public void setAuthType(String type) {
      request.setAuthType(type);
    }

    @Override
    public void setPathInfo(String path) {
      request.setPathInfo(path);
    }

    @Override
    public void setRequestedSessionCookie(boolean flag) {
      request.setRequestedSessionCookie(flag);
    }

    @Override
    public void setRequestedSessionId(String id) {
      request.setRequestedSessionId(id);
    }

    @Override
    public void setRequestedSessionURL(boolean flag) {
      request.setRequestedSessionURL(flag);
    }

    @Override
    public void setRequestedSessionSSL(boolean flag) {
      request.setRequestedSessionSSL(flag);
    }

    @Override
    public String getDecodedRequestURI() {
      return request.getDecodedRequestURI();
    }

    @Override
    public MessageBytes getDecodedRequestURIMB() {
      return request.getDecodedRequestURIMB();
    }

    @Override
    public void setUserPrincipal(Principal principal) {
      request.setUserPrincipal(principal);
    }

    @Override
    public boolean isTrailerFieldsReady() {
      return request.isTrailerFieldsReady();
    }

    @Override
    public Map<String, String> getTrailerFields() {
      return request.getTrailerFields();
    }

    @Override
    @SuppressWarnings("deprecation")
    public PushBuilder newPushBuilder() {
      return request.newPushBuilder();
    }

    @Override
    public <T extends HttpUpgradeHandler> T upgrade(Class<T> httpUpgradeHandlerClass) throws IOException, ServletException {
      return request.upgrade(httpUpgradeHandlerClass);
    }

    @Override
    public String getAuthType() {
      return request.getAuthType();
    }

    @Override
    public String getContextPath() {
      return request.getContextPath();
    }

    @Override
    public Cookie[] getCookies() {
      return request.getCookies();
    }

    @Override
    public ServerCookies getServerCookies() {
      return request.getServerCookies();
    }

    @Override
    public long getDateHeader(String name) {
      return request.getDateHeader(name);
    }

    @Override
    public String getHeader(String name) {
      return request.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
      return request.getHeaders(name);
    }

    @Override
    public Enumeration<String> getHeaderNames() {
      return request.getHeaderNames();
    }

    @Override
    public int getIntHeader(String name) {
      return request.getIntHeader(name);
    }

    @Override
    public HttpServletMapping getHttpServletMapping() {
      return request.getHttpServletMapping();
    }

    @Override
    public String getMethod() {
      return request.getMethod();
    }

    @Override
    public String getPathInfo() {
      return request.getPathInfo();
    }

    @Override
    public String getPathTranslated() {
      return request.getPathTranslated();
    }

    @Override
    public String getQueryString() {
      return request.getQueryString();
    }

    @Override
    public String getRemoteUser() {
      return request.getRemoteUser();
    }

    @Override
    public MessageBytes getRequestPathMB() {
      return request.getRequestPathMB();
    }

    @Override
    public String getRequestedSessionId() {
      return request.getRequestedSessionId();
    }

    @Override
    public String getRequestURI() {
      return request.getRequestURI();
    }

    @Override
    public StringBuffer getRequestURL() {
      return request.getRequestURL();
    }

    @Override
    public String getServletPath() {
      return request.getServletPath();
    }

    @Override
    public HttpSession getSession() {
      return request.getSession();
    }

    @Override
    public HttpSession getSession(boolean create) {
      return request.getSession(create);
    }

    @Override
    public boolean isRequestedSessionIdFromCookie() {
      return request.isRequestedSessionIdFromCookie();
    }

    @Override
    public boolean isRequestedSessionIdFromURL() {
      return request.isRequestedSessionIdFromURL();
    }

    @Override
    public boolean isRequestedSessionIdValid() {
      return request.isRequestedSessionIdValid();
    }

    @Override
    public boolean isUserInRole(String role) {
      return request.isUserInRole(role);
    }

    @Override
    public Principal getPrincipal() {
      return request.getPrincipal();
    }

    @Override
    public Principal getUserPrincipal() {
      return request.getUserPrincipal();
    }

    @Override
    public Session getSessionInternal() {
      return request.getSessionInternal();
    }

    @Override
    public void changeSessionId(String newSessionId) {
      request.changeSessionId(newSessionId);
    }

    @Override
    public String changeSessionId() {
      return request.changeSessionId();
    }

    @Override
    public Session getSessionInternal(boolean create) {
      return request.getSessionInternal(create);
    }

    @Override
    public boolean isParametersParsed() {
      return request.isParametersParsed();
    }

    @Override
    public boolean isFinished() {
      return request.isFinished();
    }

    @Override
    public boolean authenticate(HttpServletResponse response) throws IOException, ServletException {
      return request.authenticate(response);
    }

    @Override
    public void login(String username, String password) throws ServletException {
      request.login(username, password);
    }

    @Override
    public void logout() throws ServletException {
      request.logout();
    }

    @Override
    public Collection<Part> getParts() throws IOException, IllegalStateException, ServletException {
      return request.getParts();
    }

    @Override
    public Part getPart(String name) throws IOException, IllegalStateException, ServletException {
      return request.getPart(name);
    }

    @Override
    public int hashCode() {
      return request.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
      return request.equals(obj);
    }

    private HttpServletRequestWrapper newHttpServletRequestWrapper(HttpServletRequest httpRequest,
                                                                   ByteArrayInputStream arrayInputStream) {
      return new HttpServletRequestWrapper(httpRequest) {
        @Override
        public ServletInputStream getInputStream() throws IOException {
          return newServletInputStreamWrapper(arrayInputStream);
        }

      };
    }

    private ServletInputStream newServletInputStreamWrapper(ByteArrayInputStream arrayInputStream) {
      return new ServletInputStream() {

        @Override
        public boolean isFinished() {
          return arrayInputStream.available() == 0;
        }

        @Override
        public boolean isReady() {
          return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {
          // Noop
        }

        @Override
        public int read() throws IOException {
          return arrayInputStream.read();
        }

        @Override
        public int available() throws IOException {
          return arrayInputStream.available();
        }
      };
    }
  }

  private class ResponseWrapper extends Response {

    private final ByteArrayOutputStream buffer             = new ByteArrayOutputStream();

    private final ServletOutputStream   bufferOutputStream = newServletOutputStreamWrapper();

    private PrintWriter                 bufferWriter;

    private HttpServletResponseWrapper  bufferResponse;

    private Response                    response;

    public ResponseWrapper(Response response) {
      super(response.getCoyoteResponse());
      this.response = response;
    }

    @Override
    public void sendRedirect(String location, boolean clearBuffer) throws IOException {
      response.sendRedirect(location, clearBuffer);
    }

    @Override
    public void setCharacterEncoding(Charset charset) {
      response.setCharacterEncoding(charset);
    }

    @Override
    public void sendRedirect(String location, int status, boolean clearBuffer) throws IOException {
      response.sendRedirect(location, status, clearBuffer);
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
    public void setError() {
      response.setError();
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
    public void sendAcknowledgement(ContinueResponseTiming continueResponseTiming) {
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

    @Override
    public int hashCode() {
      return response.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
      return response.equals(obj);
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
