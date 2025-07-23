/**
 * Copyright (C) 2025 eXo Platform SAS
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
package org.exoplatform.documents.webdav.model.constant;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.namespace.QName;

import org.exoplatform.common.http.HTTPStatus;
import org.exoplatform.documents.webdav.model.WebDavItemProperty;

public class PropertyConstants {

  private PropertyConstants() {
    // Constants Class
  }

  public static final String                  ALLOW_METHODS              =
                                                            "CANCELUPLOAD, CHECKIN, CHECKOUT, COPY, DELETE, GET, HEAD, LOCK, MKCALENDAR, MKCOL, MOVE, OPTIONS, POST, PROPFIND, PROPPATCH, PUT, REPORT, SEARCH, UNCHECKOUT, UNLOCK, UPDATE, VERSION-CONTROL";

  public static final List<String>            ALLOW_METHODS_LIST         = Arrays.stream(ALLOW_METHODS.split(","))                                                                                                                                               // NOSONAR
                                                                                 .map(String::trim)
                                                                                 .toList();

  public static final String                  REQUEST_SINGLE_PROP        = "prop";

  public static final String                  REQUEST_SINGLE_PROP_NAME   = "propname";

  public static final String                  REQUEST_INCLUDED_PROPS     = "include";

  public static final String                  REQUEST_ALL_PROPS          = "allprop";

  public static final List<String>            ALLOWED_REQUEST_PROP_TYPES = List.of(REQUEST_ALL_PROPS,
                                                                                   REQUEST_INCLUDED_PROPS,
                                                                                   REQUEST_SINGLE_PROP,
                                                                                   REQUEST_SINGLE_PROP_NAME);

  public static final String                  SUPPORTED_METHOD           = "supported-method";

  public static final QName                   SUPPORTEDMETHOD            = new QName("DAV:", SUPPORTED_METHOD);

  public static final QName                   LOCKENTRY                  = new QName("DAV:", "lockentry");

  public static final QName                   COLLECTION                 = new QName("DAV:", "collection");

  public static final QName                   LOCKTOKEN                  = new QName("DAV:", "locktoken");

  public static final QName                   TIMEOUT                    = new QName("DAV:", "timeout");

  public static final QName                   DEPTH                      = new QName("DAV:", "depth");

  public static final QName                   ACTIVELOCK                 = new QName("DAV:", "activelock");

  /**
   * WebDAV childcount property. See
   * <a href='http://www.ietf.org/rfc/rfc2518.txt'>Versioning Extensions to
   * WebDAV</a> for more information.
   */
  public static final QName                   CHILDCOUNT                 = new QName("DAV:", "childcount");

  /**
   * WebDAV creationdate property. See
   * <a href='http://www.ietf.org/rfc/rfc2518.txt'>Versioning Extensions to
   * WebDAV</a> for more information.
   */
  public static final QName                   CREATIONDATE               = new QName("DAV:", "creationdate");

  /**
   * WebDAV creationdate property. See
   * <a href='http://www.ietf.org/rfc/rfc2518.txt'>Versioning Extensions to
   * WebDAV</a> for more information.
   */
  public static final QName                   GET_ETAG                   = new QName("DAV:", "getetag");

  /**
   * WebDAV displayname property. See
   * <a href='http://www.ietf.org/rfc/rfc2518.txt'>Versioning Extensions to
   * WebDAV</a> for more information.
   */
  public static final QName                   DISPLAYNAME                = new QName("DAV:", "displayname");

  /**
   * WebDAV getcontentlanguage property. See
   * <a href='http://www.ietf.org/rfc/rfc2518.txt'>Versioning Extensions to
   * WebDAV</a> for more information.
   */
  public static final QName                   GETCONTENTLANGUAGE         = new QName("DAV:", "getcontentlanguage");

  /**
   * WebDAV getcontentlength property. See
   * <a href='http://www.ietf.org/rfc/rfc2518.txt'>Versioning Extensions to
   * WebDAV</a> for more information.
   */
  public static final QName                   GETCONTENTLENGTH           = new QName("DAV:", "getcontentlength");

  /**
   * WebDAV getcontenttype property. See
   * <a href='http://www.ietf.org/rfc/rfc2518.txt'>Versioning Extensions to
   * WebDAV</a> for more information.
   */
  public static final QName                   GETCONTENTTYPE             = new QName("DAV:", "getcontenttype");

  /**
   * WebDAV property. See
   * <a href='http://www.ietf.org/rfc/rfc2518.txt'>Versioning Extensions to
   * WebDAV</a> for more information.
   */
  public static final QName                   GETLASTMODIFIED            = new QName("DAV:", "getlastmodified");

  /**
   * WebDAV getlastmodified property. See
   * <a href='http://www.ietf.org/rfc/rfc2518.txt'>Versioning Extensions to
   * WebDAV</a> for more information.
   */
  public static final QName                   HASCHILDREN                = new QName("DAV:", "haschildren");

  /**
   * WebDAV iscollection property. See
   * <a href='http://www.ietf.org/rfc/rfc2518.txt'>Versioning Extensions to
   * WebDAV</a> for more information.
   */
  public static final QName                   ISCOLLECTION               = new QName("DAV:", "iscollection");

  /**
   * WebDAV isfolder property. See
   * <a href='http://www.ietf.org/rfc/rfc2518.txt'>Versioning Extensions to
   * WebDAV</a> for more information.
   */
  public static final QName                   ISFOLDER                   = new QName("DAV:", "isfolder");

  /**
   * WebDAV isroot property. See
   * <a href='http://www.ietf.org/rfc/rfc2518.txt'>Versioning Extensions to
   * WebDAV</a> for more information.
   */
  public static final QName                   ISROOT                     = new QName("DAV:", "isroot");

  /**
   * WebDAV isversioned property. See
   * <a href='http://www.ietf.org/rfc/rfc2518.txt'>Versioning Extensions to
   * WebDAV</a> for more information.
   */
  public static final QName                   ISVERSIONED                = new QName("DAV:", "isversioned");

  /**
   * WebDAV parentname property. See
   * <a href='http://www.ietf.org/rfc/rfc2518.txt'>Versioning Extensions to
   * WebDAV</a> for more information.
   */
  public static final QName                   PARENTNAME                 = new QName("DAV:", "parentname");

  /**
   * WebDAV resourcetype property. See
   * <a href='http://www.ietf.org/rfc/rfc2518.txt'>Versioning Extensions to
   * WebDAV</a> for more information.
   */
  public static final QName                   RESOURCETYPE               = new QName("DAV:", "resourcetype");

  /**
   * WebDAV supportedlock property. See
   * <a href='http://www.ietf.org/rfc/rfc2518.txt'>Versioning Extensions to
   * WebDAV</a> for more information.
   */
  public static final QName                   SUPPORTEDLOCK              = new QName("DAV:", "supportedlock");

  /**
   * WebDAV lockdiscovery property. See
   * <a href='http://www.ietf.org/rfc/rfc2518.txt'>Versioning Extensions to
   * WebDAV</a> for more information.
   */
  public static final QName                   LOCKDISCOVERY              = new QName("DAV:", "lockdiscovery");

  /**
   * WebDAV supported-method-set property. See
   * <a href='http://www.ietf.org/rfc/rfc2518.txt'>Versioning Extensions to
   * WebDAV</a> for more information.
   */
  public static final QName                   SUPPORTEDMETHODSET         = new QName("DAV:", "supported-method-set");

  /**
   * WebDAV lockscope property. See
   * <a href='http://www.ietf.org/rfc/rfc2518.txt'>Versioning Extensions to
   * WebDAV</a> for more information.
   */
  public static final QName                   LOCKSCOPE                  = new QName("DAV:", "lockscope");

  /**
   * WebDAV locktype property. See
   * <a href='http://www.ietf.org/rfc/rfc2518.txt'>Versioning Extensions to
   * WebDAV</a> for more information.
   */
  public static final QName                   LOCKTYPE                   = new QName("DAV:", "locktype");

  /**
   * WebDAV owner property. See
   * <a href='http://www.ietf.org/rfc/rfc2518.txt'>Versioning Extensions to
   * WebDAV</a> for more information.
   */
  public static final QName                   OWNER                      = new QName("DAV:", "owner");

  /**
   * WebDAV exclusive property. See
   * <a href='http://www.ietf.org/rfc/rfc2518.txt'>Versioning Extensions to
   * WebDAV</a> for more information.
   */
  public static final QName                   EXCLUSIVE                  = new QName("DAV:", "exclusive");

  /**
   * WebDAV write property. See
   * <a href='http://www.ietf.org/rfc/rfc2518.txt'>Versioning Extensions to
   * WebDAV</a> for more information.
   */
  public static final QName                   WRITE                      = new QName("DAV:", "write");

  /**
   * WebDAV ordering-type property. See
   * <a href='http://www.ietf.org/rfc/rfc2518.txt'>Versioning Extensions to
   * WebDAV</a> for more information.
   */
  public static final QName                   ORDERING_TYPE              = new QName("DAV:", "ordering-type");

  /**
   * jcr:data property.
   */
  public static final QName                   JCR_DATA                   = new QName("jcr:", "data");

  /**
   * jcr:content property.
   */
  public static final QName                   JCR_CONTENT                = new QName("jcr:", "content");

  /**
   * dav:isreadonly property for MicroSoft Webfolders extension.
   */
  public static final QName                   IS_READ_ONLY               = new QName("DAV:", "isreadonly");

  /**
   * dav:include element for dav:allprop of PROPFIND method See
   * <a href='http://www.webdav.org/specs/rfc4918.html#METHOD_PROPFIND'>HTTP
   * Extensions for Web Distributed Authoring and Versioning (WebDAV)</a> for
   * more information..
   */
  public static final QName                   DAV_ALLPROP_INCLUDE        = new QName("DAV:", REQUEST_INCLUDED_PROPS);

  /**
   * dav:allprop element for dav:allprop of PROPFIND method See
   * <a href='http://www.webdav.org/specs/rfc4918.html#METHOD_PROPFIND'>HTTP
   * Extensions for Web Distributed Authoring and Versioning (WebDAV)</a> for
   * more information..
   */
  public static final QName                   DAV_ALLPROP                = new QName("DAV:", REQUEST_ALL_PROPS);

  /**
   * WebDAV DeltaV predecessor-set property. See
   * <a href='http://www.ietf.org/rfc/rfc3253.txt'>Versioning Extensions to
   * WebDAV</a> for more information.
   */
  public static final QName                   PREDECESSORSET             = new QName("DAV:", "predecessor-set");

  /**
   * WebDAV DeltaV successor-set property. See
   * <a href='http://www.ietf.org/rfc/rfc3253.txt'>Versioning Extensions to
   * WebDAV</a> for more information.
   */
  public static final QName                   SUCCESSORSET               = new QName("DAV:", "successor-set");

  /**
   * WebDAV DeltaV version-history property. See
   * <a href='http://www.ietf.org/rfc/rfc3253.txt'>Versioning Extensions to
   * WebDAV</a> for more information.
   */
  public static final QName                   VERSIONHISTORY             = new QName("DAV:", "version-history");

  /**
   * WebDAV DeltaV version-name property. See
   * <a href='http://www.ietf.org/rfc/rfc3253.txt'>Versioning Extensions to
   * WebDAV</a> for more information.
   */
  public static final QName                   VERSIONNAME                = new QName("DAV:", "version-name");

  /**
   * WebDAV DeltaV checked-in property. See
   * <a href='http://www.ietf.org/rfc/rfc3253.txt'>Versioning Extensions to
   * WebDAV</a> for more information.
   */
  public static final QName                   CHECKEDIN                  = new QName("DAV:", "checked-in");

  /**
   * WebDAV DeltaV checked-out property. See
   * <a href='http://www.ietf.org/rfc/rfc3253.txt'>Versioning Extensions to
   * WebDAV</a> for more information.
   */
  public static final QName                   CHECKEDOUT                 = new QName("DAV:", "checked-out");

  public static final QName                   HREF                       = new QName("DAV:", "href");

  /**
   * Creation date pattern.
   */
  public static final String                  CREATION_PATTERN           = "yyyy-MM-dd'T'HH:mm:ss'Z'";

  /**
   * Last-Modified date pattern.
   */
  public static final String                  MODIFICATION_PATTERN       = "EEE, dd MMM yyyy HH:mm:ss z";

  public static final String                  HTTPVER                    = "HTTP/1.1";

  protected static final Map<Integer, String> HTTP_STATUS_DESCRIPTIONS   = new HashMap<>();

  static {
    HTTP_STATUS_DESCRIPTIONS.put(HTTPStatus.CONTINUE, "Continue");
    HTTP_STATUS_DESCRIPTIONS.put(HTTPStatus.SWITCHING_PROTOCOLS, "Switching Protocols");
    HTTP_STATUS_DESCRIPTIONS.put(HTTPStatus.OK, "OK");
    HTTP_STATUS_DESCRIPTIONS.put(HTTPStatus.CREATED, "Created");
    HTTP_STATUS_DESCRIPTIONS.put(HTTPStatus.ACCEPTED, "Accepted");
    HTTP_STATUS_DESCRIPTIONS.put(HTTPStatus.NOT_AUTHORITATIVE, "Non-Authoritative Information");
    HTTP_STATUS_DESCRIPTIONS.put(HTTPStatus.NO_CONTENT, "No Content");
    HTTP_STATUS_DESCRIPTIONS.put(HTTPStatus.RESET, "Reset Content");
    HTTP_STATUS_DESCRIPTIONS.put(HTTPStatus.PARTIAL, "Partial Content");
    HTTP_STATUS_DESCRIPTIONS.put(HTTPStatus.MULTISTATUS, "Multi Status");
    HTTP_STATUS_DESCRIPTIONS.put(HTTPStatus.MULT_CHOICE, "Multiple Choices");
    HTTP_STATUS_DESCRIPTIONS.put(HTTPStatus.MOVED_PERM, "Moved Permanently");
    HTTP_STATUS_DESCRIPTIONS.put(HTTPStatus.FOUND, "Found");
    HTTP_STATUS_DESCRIPTIONS.put(HTTPStatus.SEE_OTHER, "See Other");
    HTTP_STATUS_DESCRIPTIONS.put(HTTPStatus.NOT_MODIFIED, "Not Modified");
    HTTP_STATUS_DESCRIPTIONS.put(HTTPStatus.USE_PROXY, "Use Proxy");
    HTTP_STATUS_DESCRIPTIONS.put(HTTPStatus.TEMP_REDIRECT, "Temporary Redirect");
    HTTP_STATUS_DESCRIPTIONS.put(HTTPStatus.BAD_REQUEST, "Bad Request");
    HTTP_STATUS_DESCRIPTIONS.put(HTTPStatus.UNAUTHORIZED, "Unauthorized");
    HTTP_STATUS_DESCRIPTIONS.put(HTTPStatus.PAYMENT_REQUIRED, "Payment Required");
    HTTP_STATUS_DESCRIPTIONS.put(HTTPStatus.FORBIDDEN, "Forbidden");
    HTTP_STATUS_DESCRIPTIONS.put(HTTPStatus.NOT_FOUND, "Not Found");
    HTTP_STATUS_DESCRIPTIONS.put(HTTPStatus.METHOD_NOT_ALLOWED, "Method Not Allowed");
    HTTP_STATUS_DESCRIPTIONS.put(HTTPStatus.NOT_ACCEPTABLE, "Not Acceptable");
    HTTP_STATUS_DESCRIPTIONS.put(HTTPStatus.PROXY_AUTH, "Proxy Authentication Required");
    HTTP_STATUS_DESCRIPTIONS.put(HTTPStatus.REQUEST_TIMEOUT, "Request Timeout");
    HTTP_STATUS_DESCRIPTIONS.put(HTTPStatus.CONFLICT, "Conflict");
    HTTP_STATUS_DESCRIPTIONS.put(HTTPStatus.GONE, "Gone");
    HTTP_STATUS_DESCRIPTIONS.put(HTTPStatus.LENGTH_REQUIRED, "Length Required");
    HTTP_STATUS_DESCRIPTIONS.put(HTTPStatus.PRECON_FAILED, "Precondition Failed");
    HTTP_STATUS_DESCRIPTIONS.put(HTTPStatus.REQ_TOO_LONG, "Request Entity Too Large");
    HTTP_STATUS_DESCRIPTIONS.put(HTTPStatus.REQUEST_URI_TOO_LONG, "Request-URI Too Long");
    HTTP_STATUS_DESCRIPTIONS.put(HTTPStatus.UNSUPPORTED_TYPE, "Unsupported Media Type");
    HTTP_STATUS_DESCRIPTIONS.put(HTTPStatus.REQUESTED_RANGE_NOT_SATISFIABLE, "Requested Range Not Satisfiable");
    HTTP_STATUS_DESCRIPTIONS.put(HTTPStatus.EXPECTATION_FAILED, "Expectation Failed");
    HTTP_STATUS_DESCRIPTIONS.put(HTTPStatus.INTERNAL_ERROR, "Internal Server Error");
    HTTP_STATUS_DESCRIPTIONS.put(HTTPStatus.NOT_IMPLEMENTED, "Not Implemented");
    HTTP_STATUS_DESCRIPTIONS.put(HTTPStatus.BAD_GATEWAY, "Bad Gateway");
    HTTP_STATUS_DESCRIPTIONS.put(HTTPStatus.UNAVAILABLE, "Service Unavailable");
    HTTP_STATUS_DESCRIPTIONS.put(HTTPStatus.GATEWAY_TIMEOUT, "Gateway Timeout");
    HTTP_STATUS_DESCRIPTIONS.put(HTTPStatus.HTTP_VERSION_NOT_SUPPORTED, "HTTP Version Not Supported");
  }

  public static String getStatusDescription(int status) {
    return String.format("%s %d %s", HTTPVER, status, HTTP_STATUS_DESCRIPTIONS.getOrDefault(status, ""));
  }

  public static WebDavItemProperty getSupportedLock() {
    WebDavItemProperty supportedLock = new WebDavItemProperty(SUPPORTEDLOCK);
    WebDavItemProperty lockEntry = new WebDavItemProperty(LOCKENTRY);
    supportedLock.addChild(lockEntry);
    WebDavItemProperty lockScope = new WebDavItemProperty(LOCKSCOPE);
    lockScope.addChild(new WebDavItemProperty(EXCLUSIVE));
    lockEntry.addChild(lockScope);
    WebDavItemProperty lockType = new WebDavItemProperty(LOCKTYPE);
    lockType.addChild(new WebDavItemProperty(WRITE));
    lockEntry.addChild(lockType);
    return supportedLock;
  }

  public static WebDavItemProperty getLockDiscovery(String token, String lockOwner, String timeOut) {
    WebDavItemProperty lockDiscovery = new WebDavItemProperty(LOCKDISCOVERY);

    WebDavItemProperty activeLock =
                                  lockDiscovery.addChild(new WebDavItemProperty(ACTIVELOCK));

    WebDavItemProperty lockType = activeLock.addChild(new WebDavItemProperty(LOCKTYPE));
    lockType.addChild(new WebDavItemProperty(WRITE));

    WebDavItemProperty lockScope = activeLock.addChild(new WebDavItemProperty(LOCKSCOPE));
    lockScope.addChild(new WebDavItemProperty(EXCLUSIVE));

    WebDavItemProperty depth = activeLock.addChild(new WebDavItemProperty(DEPTH));
    depth.setValue("Infinity");

    if (lockOwner != null) {
      WebDavItemProperty owner = activeLock.addChild(new WebDavItemProperty(OWNER));
      owner.setValue(lockOwner);
    }

    WebDavItemProperty timeout = activeLock.addChild(new WebDavItemProperty(TIMEOUT));
    timeout.setValue("Second-" + timeOut);

    if (token != null) {
      WebDavItemProperty lockToken = activeLock.addChild(new WebDavItemProperty(LOCKTOKEN));
      WebDavItemProperty lockHref = lockToken.addChild(new WebDavItemProperty(HREF));
      lockHref.setValue(token);
    }

    return lockDiscovery;
  }

  public static WebDavItemProperty getSupportedMethodSet() {
    WebDavItemProperty supportedMethodProp = new WebDavItemProperty(SUPPORTEDMETHODSET);
    ALLOW_METHODS_LIST.forEach(m -> supportedMethodProp.addChild(new WebDavItemProperty(SUPPORTEDMETHOD))
                                                       .setAttribute("name", m));
    return supportedMethodProp;
  }

  public static WebDavItemProperty getIsFolderItemProperty() {
    WebDavItemProperty collectionProp = new WebDavItemProperty(COLLECTION);
    WebDavItemProperty resourceType = new WebDavItemProperty(RESOURCETYPE);
    resourceType.addChild(collectionProp);
    return resourceType;
  }

}
