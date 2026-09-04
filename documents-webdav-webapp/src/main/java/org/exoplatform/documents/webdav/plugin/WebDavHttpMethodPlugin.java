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
package org.exoplatform.documents.webdav.plugin;

import static org.exoplatform.documents.webdav.model.constant.PropertyConstants.DAV_ALLPROP_INCLUDE;
import static org.exoplatform.documents.webdav.model.constant.PropertyConstants.getStatusDescription;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

import javax.xml.namespace.QName;
import javax.xml.stream.*;
import javax.xml.stream.events.Characters;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.springframework.beans.factory.annotation.Autowired;

import org.exoplatform.common.http.HTTPStatus;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.documents.webdav.model.WebDavException;
import org.exoplatform.documents.webdav.model.WebDavItem;
import org.exoplatform.documents.webdav.model.WebDavItemProperty;
import org.exoplatform.documents.webdav.service.DocumentWebDavService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.rest.ExtHttpHeaders;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.ToString;

@Getter
public abstract class WebDavHttpMethodPlugin {

  public static final String      CONTEXT_PATH                   = "/webdav/drives";                                 // NOSONAR

  public static final String      CONTEXT_PATH_ROOT              = CONTEXT_PATH + "/";                               // NOSONAR

  public static final String      CONTEXT_PATH_SINGLE_DRIVE      = CONTEXT_PATH + "/d";

  public static final String      CONTEXT_PATH_SINGLE_DRIVE_ROOT = CONTEXT_PATH_SINGLE_DRIVE + "/";                  // NOSONAR

  public static final String      OPAQUE_LOCK_TOKEN              = "opaquelocktoken";

  public static final String      INFINITY_DEPTH                 = "Infinity";

  public static final String      IF_MODIFIED_SINCE_PATTERN      = "EEE, dd MMM yyyy HH:mm:ss z";

  public static final String      DEFAULT_XML_ENCODING           = StandardCharsets.UTF_8.name();

  protected static final Log      LOG                            = ExoLogger.getLogger(WebDavHttpMethodPlugin.class);

  @Autowired
  @EqualsAndHashCode.Exclude
  @ToString.Exclude
  protected DocumentWebDavService documentWebDavService;

  private String                  method;

  protected WebDavHttpMethodPlugin(String method) {
    this.method = method;
  }

  /**
   * Handles the WebDav Request switch designated method
   * 
   * @param httpRequest {@link HttpServletRequest}
   * @param httpResponse {@link HttpServletResponse}
   * @throws WebDavException when an error happened while handling the operation
   */
  public abstract void handle(HttpServletRequest httpRequest, HttpServletResponse httpResponse) throws WebDavException;

  /**
   * Creates the list of Lock tokens from Lock-Token and If headers.
   * 
   * @param httpRequest {@link HttpServletRequest}
   * @return the {@link List} of lock tokens hold by current WebDav Session sent
   *         in HTTP Request Headers
   */
  protected List<String> getLockTokens(HttpServletRequest httpRequest) {
    String lockTokenHeader = httpRequest.getHeader(ExtHttpHeaders.LOCKTOKEN);
    String ifHeader = httpRequest.getHeader(ExtHttpHeaders.IF);
    ArrayList<String> lockTokens = new ArrayList<>();
    if (lockTokenHeader != null) {
      if (lockTokenHeader.startsWith("<")) {
        lockTokenHeader = lockTokenHeader.substring(1, lockTokenHeader.length() - 1);
      }
      if (lockTokenHeader.contains(OPAQUE_LOCK_TOKEN)) {
        lockTokenHeader = lockTokenHeader.split(":")[1];
      }
      lockTokens.add(lockTokenHeader);
    }

    if (ifHeader != null) {
      String headerLockToken = ifHeader.substring(ifHeader.indexOf("("));
      headerLockToken = headerLockToken.substring(2, headerLockToken.length() - 2);
      if (headerLockToken.contains(OPAQUE_LOCK_TOKEN)) {
        headerLockToken = headerLockToken.split(":")[1];
      }
      lockTokens.add(headerLockToken);
    }
    return lockTokens;
  }

  protected String getResourcePath(HttpServletRequest httpRequest) {
    String resourcePath = Arrays.stream(httpRequest.getRequestURI().substring(getBaseUri(httpRequest).length()).split("/"))
                                .map(WebDavHttpMethodPlugin::decodePathSegment)
                                .collect(Collectors.joining("/"));
    return StringUtils.isBlank(resourcePath) ? "/" : resourcePath;
  }

  @SneakyThrows
  protected URI getResourceUri(HttpServletRequest httpRequest) {
    return new URI(getBaseUrl(httpRequest) + Arrays.stream(getResourcePath(httpRequest).split("/"))
                                                   .map(s -> URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20"))
                                                   .collect(Collectors.joining("/")));
  }

  /**
   * Decodes a raw URI path segment. Unlike {@link URLDecoder#decode(String, java.nio.charset.Charset)},
   * which implements {@code application/x-www-form-urlencoded} semantics and wrongly turns a literal
   * '+' into a space, this only unescapes percent-encoded sequences and leaves literal '+' untouched,
   * since '+' has no special meaning in a URI path segment (RFC 3986).
   */
  protected static String decodePathSegment(String segment) {
    return URLDecoder.decode(segment.replace("+", "%2B"), StandardCharsets.UTF_8);
  }

  protected String getBaseUrl(HttpServletRequest httpRequest) {
    return CommonsUtils.getCurrentDomain() + getBaseUri(httpRequest);
  }

  private String getBaseUri(HttpServletRequest httpRequest) {
    if (httpRequest.getRequestURI().contains(CONTEXT_PATH_SINGLE_DRIVE)) {
      return CONTEXT_PATH_SINGLE_DRIVE;
    } else {
      return CONTEXT_PATH;
    }
  }

  @SneakyThrows
  protected WebDavItemProperty parseRequestBodyAsWebDavItemProperty(HttpServletRequest httpRequest) {
    try (InputStream inputStream = httpRequest.getInputStream()) {
      if (inputStream.available() > 0) {
        return parseWebDavItemProperty(inputStream);
      }
    }
    return null;
  }

  protected String getDepth(HttpServletRequest httpRequest) {
    return httpRequest.getHeader(ExtHttpHeaders.DEPTH);
  }

  protected int getDepthInt(HttpServletRequest httpRequest) {
    String depth = getDepth(httpRequest);
    return StringUtils.isBlank(depth) || Strings.CI.equals(depth, INFINITY_DEPTH) ? -1 : Integer.parseInt(depth);
  }

  protected String getDestinationPath(HttpServletRequest httpRequest) {
    String resourcePath = Arrays.stream(Arrays.asList(httpRequest.getHeader(ExtHttpHeaders.DESTINATION)
                                                                 .split(getBaseUri(httpRequest)))
                                              .getLast()
                                              .split("/"))
                                .map(WebDavHttpMethodPlugin::decodePathSegment)
                                .collect(Collectors.joining("/"));
    return StringUtils.isBlank(resourcePath) ? "/" : resourcePath;
  }

  protected boolean getOverwriteParameter(HttpServletRequest httpRequest) {
    return Strings.CI.equals("f", httpRequest.getHeader(ExtHttpHeaders.OVERWRITE));
  }

  protected boolean getRemoveDestinationParameter(HttpServletRequest httpRequest) {
    return Strings.CI.equals("t", httpRequest.getHeader(ExtHttpHeaders.OVERWRITE));
  }

  @SneakyThrows
  protected boolean checkModified(HttpServletRequest httpRequest,
                                  String resourcePath,
                                  String version) {
    long lastModifiedDate = documentWebDavService.getLastModifiedDate(resourcePath, version);
    String ifNoneMatch = httpRequest.getHeader(ExtHttpHeaders.IF_NONE_MATCH); // NOSONAR
    if (ifNoneMatch != null) {
      if ("*".equals(ifNoneMatch)) {
        return true;
      } else if (lastModifiedDate > 0) {
        String resourceEntityTag = String.format("W/%s", lastModifiedDate);
        return Arrays.stream(ifNoneMatch.split(",")).noneMatch(resourceEntityTag::equalsIgnoreCase);
      }
    }
    String ifModifiedSince = httpRequest.getHeader(ExtHttpHeaders.IF_MODIFIED_SINCE); // NOSONAR
    if (ifModifiedSince != null && lastModifiedDate > 0) {
      Date modifiedDate = new Date(lastModifiedDate);
      DateFormat dateFormat = new SimpleDateFormat(IF_MODIFIED_SINCE_PATTERN, Locale.US);
      Date ifModifiedSinceDate = dateFormat.parse(ifModifiedSince);
      return modifiedDate.getTime() > ifModifiedSinceDate.getTime();
    }
    return true;
  }

  @SneakyThrows
  protected void writeResponse(HttpServletResponse httpResponse, int httpCode, String content) {
    httpResponse.setStatus(httpCode);
    PrintWriter writer = httpResponse.getWriter();
    writer.print(content);
    writer.close();
  }

  public final Map<String, Collection<WebDavItemProperty>> getRequestedPropertyStats(WebDavItem resource,
                                                                                     Set<QName> requestPropertyNames) {
    Map<String, Collection<WebDavItemProperty>> propStats = new HashMap<>();
    String statName = getStatusDescription(HTTPStatus.OK);
    if (requestPropertyNames == null) {
      propStats.put(statName, resource.getProperties(false));
    } else {
      if (requestPropertyNames.contains(DAV_ALLPROP_INCLUDE)) {
        propStats.put(statName, resource.getProperties(false));
        requestPropertyNames.remove(DAV_ALLPROP_INCLUDE);
      }
      for (QName propName : requestPropertyNames) {
        WebDavItemProperty prop = resource.getProperty(propName);
        propStats.getOrDefault(statName, new ArrayList<>())
                 .add(prop);
      }
    }
    return propStats;
  }

  protected Set<QName> getRequestPropertyNames(WebDavItemProperty body) {
    if (body == null) {
      return null; // NOSONAR must be null
    } else {
      HashSet<QName> names = new HashSet<>();
      WebDavItemProperty propBody = body.getChild(DAV_ALLPROP_INCLUDE);
      if (propBody != null) {
        names.add(DAV_ALLPROP_INCLUDE);
      } else {
        propBody = body.getChild(0);
      }
      List<WebDavItemProperty> properties = propBody.getChildren();
      Iterator<WebDavItemProperty> propIter = properties.iterator();
      while (propIter.hasNext()) {
        WebDavItemProperty property = propIter.next();
        names.add(property.getName());
      }
      return names;
    }
  }

  protected String getRequestPropertyType(WebDavItemProperty body) {
    if (body == null) {
      return "allprop";
    } else if (body.getChild(DAV_ALLPROP_INCLUDE) != null) {
      return "include";
    } else {
      QName name = body.getChild(0).getName();
      if (name.getNamespaceURI().equals("DAV:")) {
        return name.getLocalPart();
      } else {
        return null;
      }
    }
  }

  @SneakyThrows
  private WebDavItemProperty parseWebDavItemProperty(InputStream entityStream) { // NOSONAR
    WebDavItemProperty rootProperty = null;
    LinkedList<WebDavItemProperty> curProperty = new LinkedList<>();
    try {
      XMLInputFactory factory = XMLInputFactory.newInstance();
      factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
      factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
      factory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false);

      factory.setXMLResolver((publicID, systemID, baseURI, namespace) -> {
        throw new XMLStreamException("External entities disabled");
      });

      XMLEventReader reader = factory.createXMLEventReader(entityStream);
      XMLEventReader fReader = factory.createFilteredReader(reader,
                                                            event -> !(event.isCharacters()
                                                                       && ((Characters) event).isWhiteSpace()));
      while (fReader.hasNext()) {
        XMLEvent event = fReader.nextEvent();
        switch (event.getEventType()) {
        case XMLStreamConstants.START_ELEMENT:
          StartElement element = event.asStartElement();
          QName name = element.getName();
          WebDavItemProperty prop = new WebDavItemProperty(name);
          if (!curProperty.isEmpty())
            curProperty.getLast().addChild(prop);
          else
            rootProperty = prop;
          curProperty.addLast(prop);
          break;
        case XMLStreamConstants.END_ELEMENT:
          curProperty.removeLast();
          break;
        case XMLStreamConstants.CHARACTERS:
          String chars = event.asCharacters().getData();
          curProperty.getLast().setValue(chars);
          break;
        default:
          break;
        }
      }

      return rootProperty;
    } catch (FactoryConfigurationError e) {
      throw new IOException(e.getMessage(), e);
    } catch (XMLStreamException e) {
      if (LOG.isDebugEnabled()) {
        LOG.warn("An XMLStreamException occurs", e);
      }
      return null;
    } catch (RuntimeException e) {
      if ("com.ctc.wstx.exc.WstxLazyException".equals(e.getClass().getName())) { // NOSONAR
        if (LOG.isDebugEnabled()) {
          LOG.warn(e.getMessage(), e);
        }
        return null;
      } else {
        throw e;
      }
    }
  }

}
