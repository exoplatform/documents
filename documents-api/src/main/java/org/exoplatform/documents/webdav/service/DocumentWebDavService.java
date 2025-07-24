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
package org.exoplatform.documents.webdav.service;

import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.namespace.NamespaceContext;
import javax.xml.namespace.QName;

import org.exoplatform.documents.webdav.model.WebDavException;
import org.exoplatform.documents.webdav.model.WebDavFileDownload;
import org.exoplatform.documents.webdav.model.WebDavItem;
import org.exoplatform.documents.webdav.model.WebDavItemOrder;
import org.exoplatform.documents.webdav.model.WebDavItemProperty;
import org.exoplatform.documents.webdav.model.WebDavLockResponse;

public interface DocumentWebDavService {

  /**
   * @return {@link NamespaceContext} ,which will be used to write XML
   *         namespaces in resulted responses
   */
  NamespaceContext getNamespaceContext();

  /**
   * @return HTTP 1.1 "Allow" header. See
   *         <a href='http://msdn.microsoft.com/en-us/library/ms965954.aspx'>
   *         WebDAV/DASL Request and Response Syntax</a> for more information.
   */
  String getDaslValue();

  /**
   * @param resourcePath File or Folder Path
   * @return true if the resource designated by the path is a file, else false
   */
  boolean isFile(String resourcePath);

  /**
   * Webdav GET of a given resource
   * 
   * @param resourcePath File or Folder Path
   * @param version Resource Version
   * @param baseUri Base Request URI
   * @param username user login accessing the resource
   * @return {@link WebDavFileDownload} containing the content of the file
   * @throws WebDavException when:
   *           <ul>
   *           <li>- Parameters aren't compliant to expected method inputs</li>
   *           <li>- The user doesn't have access to designated resource</li>
   *           <li>- The designated resource isn't found</li>
   *           </ul>
   */
  WebDavFileDownload download(String resourcePath,
                              String version,
                              String baseUri,
                              String username) throws WebDavException;

  /**
   * @param resourcePath File or Folder Path
   * @param propRequestType Requested properties retrieval type
   * @param requestedPropertyNames Requested property names
   * @param requestPropertyNamesOnly whether to get property names only or with
   *          the associated value(s)
   * @param depth resources retireval depth
   * @param baseUri Base Request URI
   * @param username user login accessing the resource
   * @return {@link WebDavItem} corresponding to the requested resource
   * @throws WebDavException
   */
  WebDavItem get(String resourcePath,
                 String propRequestType,
                 Set<QName> requestedPropertyNames,
                 boolean requestPropertyNamesOnly,
                 int depth,
                 String baseUri,
                 String username) throws WebDavException;

  /**
   * Creates a new folder in the designated location
   * 
   * @param resourcePath Folder Path to create
   * @param folderType Folder Type
   * @param contentNodeType Content Node Type
   * @param mixinTypes Folder Mixin Types
   * @param lockTokens Already Hold Lock Tokens by the WebDav Session
   * @param username user login making the changes
   * @throws WebDavException when:
   *           <ul>
   *           <li>- Parameters aren't compliant to expected method inputs</li>
   *           <li>- The user doesn't have access to designated resource</li>
   *           <li>- The designated resource isn't found</li>
   *           <li>- The designated resource is locked</li>
   *           </ul>
   */
  void createFolder(String resourcePath,
                    String folderType,
                    String contentNodeType,
                    String mixinTypes,
                    List<String> lockTokens,
                    String username) throws WebDavException;

  /**
   * Creates or Updates a designated resource
   * 
   * @param resourcePath File or Folder Path
   * @param fileType File Type
   * @param contentNodeType Content Node Type
   * @param mediaType File Media Type
   * @param mixinTypes Mixin Types
   * @param inputStream File content
   * @param lockTokens Already Hold Lock Tokens by the WebDav Session
   * @param username user login making the changes
   * @throws WebDavException when:
   *           <ul>
   *           <li>- Parameters aren't compliant to expected method inputs</li>
   *           <li>- The user doesn't have access to designated resource</li>
   *           <li>- The designated parent of the resource isn't found</li>
   *           <li>- The designated resource is locked</li>
   *           </ul>
   */
  void saveFile(String resourcePath, // NOSONAR
                String fileType,
                String contentNodeType,
                String mediaType,
                String mixinTypes,
                InputStream inputStream,
                List<String> lockTokens,
                String username) throws WebDavException;

  /**
   * Create, update and delete properties of a designated resource
   * 
   * @param resourcePath File or Folder Path
   * @param propertiesToSave {@link List} of properties to save
   * @param propertiesToRemove {@link List} of properties to remove
   * @param lockTokens Already Hold Lock Tokens by the WebDav Session
   * @param username user login making the changes
   * @return {@link Map} of result of changes by type of result (Http Status)
   *         and list of properties
   * @throws WebDavException
   */
  Map<String, Collection<WebDavItemProperty>> saveProperties(String resourcePath,
                                                             List<WebDavItemProperty> propertiesToSave,
                                                             List<WebDavItemProperty> propertiesToRemove,
                                                             List<String> lockTokens,
                                                             String username) throws WebDavException;

  /**
   * Webdav DELETE of a given resource
   * 
   * @param resourcePath File or Folder Path
   * @param lockTokens Already Hold Lock Tokens by the WebDav Session
   * @param username user login making the changes
   * @throws WebDavException when:
   *           <ul>
   *           <li>- Parameters aren't compliant to expected method inputs</li>
   *           <li>- The user doesn't have access to designated resource</li>
   *           <li>- The designated resource isn't found</li>
   *           </ul>
   */
  void delete(String resourcePath, List<String> lockTokens, String username) throws WebDavException;

  /**
   * Webdav COPY of a given resource into a designated resource path
   * 
   * @param resourcePath File or Folder Path
   * @param destinationPath File destination Path
   * @param depth resources copy depth
   * @param overwrite whether to overwrite existing file if exists or not
   * @param removeDestination whether to remove destination if exists or not
   * @param webDavItemProperty {@link WebDavItemProperty}
   * @param lockTokens Already Hold Lock Tokens by the WebDav Session
   * @param username user login making the changes
   * @throws WebDavException when:
   *           <ul>
   *           <li>- Parameters aren't compliant to expected method inputs</li>
   *           <li>- The user doesn't have access to designated resource</li>
   *           <li>- The designated resource isn't found</li>
   *           <li>- The destination designated resource path already exists and
   *           overwrite = false</li>
   *           </ul>
   */
  void copy(String resourcePath, // NOSONAR
            String destinationPath,
            int depth,
            boolean overwrite,
            boolean removeDestination,
            WebDavItemProperty webDavItemProperty,
            List<String> lockTokens,
            String username) throws WebDavException;

  /**
   * @param resourcePath File or Folder Path
   * @param destinationPath File destination Path
   * @param overwrite whether to overwrite existing file if exists or not
   * @param lockTokens Already Hold Lock Tokens by the WebDav Session
   * @param username user login making the changes
   * @return true if item already exists and was replaced else false
   * @throws WebDavException when:
   *           <ul>
   *           <li>- Parameters aren't compliant to expected method inputs</li>
   *           <li>- The user doesn't have access to designated resources</li>
   *           <li>- The designated source resource isn't found</li>
   *           <li>- The destination designated resource path already exists and
   *           overwrite = false</li>
   *           </ul>
   */
  boolean move(String resourcePath,
               String destinationPath,
               boolean overwrite,
               List<String> lockTokens,
               String username) throws WebDavException;

  /**
   * @param resourcePath File or Folder Path
   * @param members {@link List} of {@link WebDavItemOrder} as identified
   *          members from WebDav client
   * @param lockTokens Already Hold Lock Tokens by the WebDav Session
   * @param username user login making the changes
   * @return true if order change applied else false
   * @throws WebDavException
   */
  boolean order(String resourcePath,
                List<WebDavItemOrder> members,
                List<String> lockTokens,
                String username) throws WebDavException;

  /**
   * @param resourcePath File or Folder Path
   * @param queryLanguage Query language (XPath or SQL)
   * @param query Query
   * @param baseUri Base Request URI
   * @param username user login making the changes
   * @return {@link List} of {@link WebDavItem}
   */
  List<WebDavItem> search(String resourcePath,
                          String queryLanguage,
                          String query,
                          String baseUri,
                          String username);

  /**
   * Enables Versioning on the designated resource
   * 
   * @param resourcePath File or Folder Path
   * @param lockTokens Already Hold Lock Tokens by the WebDav Session
   * @param username user login making the changes
   * @throws WebDavException when:
   *           <ul>
   *           <li>- The user doesn't have access to designated resource</li>
   *           <li>- The designated resource isn't found</li>
   *           <li>- The designated resource is locked</li>
   *           </ul>
   */
  void enableVersioning(String resourcePath, List<String> lockTokens, String username) throws WebDavException;

  /**
   * @param resourcePath File or Folder Path
   * @param requestedPropertyNames Requested property names
   * @param baseUri Base Request URI
   * @param username user login making the changes
   * @return {@link List} of {@link WebDavItem} representing the item versions
   */
  List<WebDavItem> getVersions(String resourcePath,
                               Set<QName> requestedPropertyNames,
                               String baseUri,
                               String username);

  /**
   * Webdav CheckIn of a given resource in order to prepare generation of a
   * version
   * 
   * @param resourcePath File or Folder Path
   * @param lockTokens Already Hold Lock Tokens by the WebDav Session
   * @param username user login making the changes
   * @throws WebDavException when:
   *           <ul>
   *           <li>- Parameters aren't compliant to expected method inputs</li>
   *           <li>- The user doesn't have access to designated resource</li>
   *           <li>- The designated resource isn't found</li>
   *           <li>- The designated resource is already checked in</li>
   *           <li>- The designated resource is locked</li>
   *           </ul>
   */
  void checkin(String resourcePath,
               List<String> lockTokens,
               String username) throws WebDavException;

  /**
   * Webdav CheckOut of a given resource in order to generate a version
   * 
   * @param resourcePath File or Folder Path
   * @param lockTokens Already Hold Lock Tokens by the WebDav Session
   * @param username user login making the changes
   * @throws WebDavException when:
   *           <ul>
   *           <li>- Parameters aren't compliant to expected method inputs</li>
   *           <li>- The user doesn't have access to designated resource</li>
   *           <li>- The designated resource isn't found</li>
   *           <li>- The designated resource is already checked out</li>
   *           </ul>
   */
  void checkout(String resourcePath,
                List<String> lockTokens,
                String username) throws WebDavException;

  /**
   * @param resourcePath File or Folder Path
   * @param lockTokens Already Hold Lock Tokens by the WebDav Session
   * @param username user login making the changes
   * @throws WebDavException when:
   *           <ul>
   *           <li>- Parameters aren't compliant to expected method inputs</li>
   *           <li>- The user doesn't have access to designated resource</li>
   *           <li>- The designated resource isn't found</li>
   *           <li>- The designated resource is not checked out</li>
   *           </ul>
   */
  void uncheckout(String resourcePath, List<String> lockTokens, String username) throws WebDavException;

  /**
   * @param resourcePath File or Folder Path
   * @param version File version
   * @return last Modified Date of a resource designated by its path
   * @throws WebDavException when:
   *           <ul>
   *           <li>- Parameters aren't compliant to expected method inputs</li>
   *           <li>- The designated resource isn't found</li>
   *           </ul>
   */
  long getLastModifiedDate(String resourcePath, String version) throws WebDavException;

  /**
   * Add a lock on a designated resource
   * 
   * @param resourcePath File or Folder Path
   * @param depth resources copy depth
   * @param lockTimeout Lock timeout
   * @param bodyIsEmpty whether the body doesn't exists or not
   * @param lockTokens Already Hold Lock Tokens by the WebDav Session
   * @param username user login making the changes
   * @return {@link WebDavLockResponse}
   * @throws WebDavException when:
   *           <ul>
   *           <li>- Parameters aren't compliant to expected method inputs</li>
   *           <li>- The designated resource is locked</li>
   *           </ul>
   */
  WebDavLockResponse lock(String resourcePath,
                          int depth,
                          int lockTimeout,
                          boolean bodyIsEmpty,
                          List<String> lockTokens,
                          String username) throws WebDavException;

  /**
   * Removes a previously added lock from a designated resource
   * 
   * @param resourcePath File or Folder Path
   * @param lockTokens Already Hold Lock Tokens by the WebDav Session
   * @param username user login making the changes
   * @throws WebDavException
   */
  void unlock(String resourcePath, List<String> lockTokens, String username) throws WebDavException;

}
