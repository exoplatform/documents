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
package org.exoplatform.documents.storage.jcr;

import java.io.InputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.jcr.Item;
import javax.jcr.NamespaceRegistry;
import javax.jcr.Node;
import javax.jcr.Session;
import javax.jcr.lock.Lock;
import javax.xml.namespace.NamespaceContext;
import javax.xml.namespace.QName;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import org.exoplatform.documents.service.DocumentWebDavService;
import org.exoplatform.documents.storage.jcr.model.JcrNamespaceContext;
import org.exoplatform.documents.storage.jcr.plugin.WebdavReadCommandHandler;
import org.exoplatform.documents.storage.jcr.plugin.WebdavWriteCommandHandler;
import org.exoplatform.documents.webdav.model.WebDavException;
import org.exoplatform.documents.webdav.model.WebDavFileDownload;
import org.exoplatform.documents.webdav.model.WebDavItem;
import org.exoplatform.documents.webdav.model.WebDavItemOrder;
import org.exoplatform.documents.webdav.model.WebDavItemProperty;
import org.exoplatform.documents.webdav.model.WebDavLockResponse;
import org.exoplatform.portal.config.UserACL;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.core.ManageableRepository;
import org.exoplatform.services.jcr.impl.RepositoryContainer;
import org.exoplatform.services.jcr.impl.RepositoryServiceImpl;
import org.exoplatform.services.jcr.impl.WorkspaceContainer;
import org.exoplatform.services.jcr.impl.core.SessionImpl;
import org.exoplatform.services.jcr.webdav.util.NodeTypeUtil;
import org.exoplatform.services.security.ConversationState;

import lombok.SneakyThrows;

@Service
public class JcrWebDavService implements DocumentWebDavService {

  private static final String       DAS_VALUE = "<DAV:basicsearch>" + "<exo:sql xmlns:exo=\"http://exoplatform.com/jcr\"/>" +
      "<exo:xpath xmlns:exo=\"http://exoplatform.com/jcr\"/>";

  @Autowired
  private WebdavReadCommandHandler  readCommandHandler;

  @Autowired
  private WebdavWriteCommandHandler writeCommandHandler;

  @Autowired
  private RepositoryService         repositoryService;

  @Autowired
  private UserACL                   userAcl;

  private NamespaceContext          namespaceContext;

  @Override
  public NamespaceContext getNamespaceContext() {
    if (namespaceContext == null) {
      namespaceContext = buildNameSpaceContext();
    }
    return namespaceContext;
  }

  @Override
  public String getDaslValue() {
    return DAS_VALUE;
  }

  @Override
  @SneakyThrows
  public boolean isFile(String path) {
    Session session = getSession();
    try {
      return readCommandHandler.isFile(session, path);
    } finally {
      session.logout();
    }
  }

  @Override
  @SneakyThrows
  public WebDavFileDownload download(String path, String version, String baseUri, String username) throws WebDavException {
    Session session = getSession(username);
    try {
      return readCommandHandler.download(session,
                                         path,
                                         version);
    } finally {
      session.logout();
    }
  }

  @Override
  public long getLastModifiedDate(String path, String version) throws WebDavException {
    Session session = getSession();
    try {
      return readCommandHandler.getLastModifiedDate(session,
                                                    path,
                                                    version);
    } finally {
      session.logout();
    }
  }

  @Override
  public WebDavItem get(String path,
                        String propRequestType,
                        Set<QName> requestedPropertyNames,
                        boolean requestPropertyNamesOnly,
                        int depth,
                        String baseUri,
                        String username) {
    Session session = getSession(username);
    try {
      return readCommandHandler.get(session,
                                    path,
                                    requestedPropertyNames,
                                    requestPropertyNamesOnly,
                                    depth,
                                    baseUri,
                                    username);
    } finally {
      session.logout();
    }
  }

  @Override
  public List<WebDavItem> search(String path,
                                 String queryLanguage,
                                 String query,
                                 String baseUri,
                                 String username) {
    Session session = getSession(username);
    try {
      return readCommandHandler.search(session,
                                       queryLanguage,
                                       query,
                                       baseUri,
                                       username);
    } finally {
      session.logout();
    }
  }

  @Override
  public List<WebDavItem> getVersions(String path,
                                      Set<QName> requestedPropertyNames,
                                      String baseUri,
                                      String username) {
    Session session = getSession(username);
    try {
      return readCommandHandler.getVersions(session,
                                            path,
                                            requestedPropertyNames,
                                            baseUri);
    } finally {
      session.logout();
    }
  }

  @Override
  public void createFolder(String path,
                           String folderType,
                           String contentNodeType,
                           String mixinTypes,
                           List<String> lockTokens,
                           String username) throws WebDavException {
    Session session = getSession(username);
    try {
      checkLock(session, path, lockTokens);
      writeCommandHandler.createFolder(session,
                                       path,
                                       NodeTypeUtil.getMixinTypes(mixinTypes));
    } finally {
      session.logout();
    }
  }

  @Override
  public void saveFile(String path,
                       String fileType,
                       String contentNodeType,
                       String mediaType,
                       String mixinTypes,
                       InputStream inputStream,
                       List<String> lockTokens,
                       String username) throws WebDavException {
    Session session = getSession(username);
    try {
      checkLock(session, path, lockTokens);
      writeCommandHandler.saveFile(session,
                                   path,
                                   mediaType,
                                   NodeTypeUtil.getMixinTypes(mixinTypes),
                                   inputStream);
    } finally {
      session.logout();
    }
  }

  @Override
  public void delete(String path,
                     List<String> lockTokens,
                     String username) throws WebDavException {
    Session session = getSession(username);
    try {
      checkLock(session, path, lockTokens);
      writeCommandHandler.delete(session, path);
    } finally {
      session.logout();
    }
  }

  @Override
  public boolean move(String sourcePath,
                      String targetPath,
                      boolean overwrite,
                      List<String> lockTokens,
                      String username) throws WebDavException {
    Session session = getSession(username);
    try {
      checkLock(session, sourcePath, lockTokens);
      return writeCommandHandler.move(session,
                                      sourcePath,
                                      targetPath,
                                      overwrite);
    } finally {
      session.logout();
    }
  }

  @Override
  public void copy(String sourcePath,
                   String targetPath,
                   int depth,
                   boolean overwrite,
                   boolean removeDestination,
                   WebDavItemProperty webDavItemProperty,
                   List<String> lockTokens,
                   String username) throws WebDavException {
    Session session = getSession(username);
    try {
      checkLock(session, sourcePath, lockTokens);
      writeCommandHandler.copy(session,
                               sourcePath,
                               targetPath,
                               overwrite,
                               removeDestination);
    } finally {
      session.logout();
    }
  }

  @Override
  public Map<String, Collection<WebDavItemProperty>> saveProperties(String path,
                                                                    List<WebDavItemProperty> propertiesToSave,
                                                                    List<WebDavItemProperty> propertiesToRemove,
                                                                    List<String> lockTokens,
                                                                    String username) throws WebDavException {
    Session session = getSession(username);
    try {
      checkLock(session, path, lockTokens);
      return writeCommandHandler.saveProperties(session,
                                                path,
                                                propertiesToSave,
                                                propertiesToRemove);
    } finally {
      session.logout();
    }
  }

  @Override
  public void enableVersioning(String path,
                               List<String> lockTokens,
                               String username) throws WebDavException {
    Session session = getSession(username);
    try {
      checkLock(session, path, lockTokens);
      writeCommandHandler.enableVersioning(session, path);
    } finally {
      session.logout();
    }
  }

  @Override
  public void checkin(String path,
                      List<String> lockTokens,
                      String username) throws WebDavException {
    Session session = getSession(username);
    try {
      checkLock(session, path, lockTokens);
      writeCommandHandler.checkin(session, path);
    } finally {
      session.logout();
    }
  }

  @Override
  public void checkout(String path,
                       List<String> lockTokens,
                       String username) throws WebDavException {
    Session session = getSession(username);
    try {
      checkLock(session, path, lockTokens);
      writeCommandHandler.checkout(session, path);
    } finally {
      session.logout();
    }
  }

  @Override
  public void uncheckout(String path,
                         List<String> lockTokens,
                         String username) throws WebDavException {
    Session session = getSession(username);
    try {
      checkLock(session, path, lockTokens);
      writeCommandHandler.uncheckout(session, path);
    } finally {
      session.logout();
    }
  }

  @Override
  public WebDavLockResponse lock(String path,
                                 int depth,
                                 int lockTimeout,
                                 boolean bodyIsEmpty,
                                 List<String> lockTokens,
                                 String username) throws WebDavException {
    Session session = getSession(username);
    try {
      checkLock(session, path, lockTokens);
      // TODO handle lockTimeout
      return writeCommandHandler.lock(session, path, depth, bodyIsEmpty, username);
    } finally {
      session.logout();
    }
  }

  @Override
  public void unlock(String path,
                     List<String> lockTokens,
                     String username) throws WebDavException {
    Session session = getSession(username);
    try {
      checkLock(session, path, lockTokens);
      writeCommandHandler.unlock(session, path);
    } finally {
      session.logout();
    }
  }

  @Override
  public boolean order(String path,
                       List<WebDavItemOrder> members,
                       List<String> lockTokens,
                       String username) throws WebDavException {
    Session session = getSession(username);
    try {
      checkLock(session, path, lockTokens);
      return writeCommandHandler.order(session, path, members);
    } finally {
      session.logout();
    }
  }

  @Override
  public void changeAcl(String path,
                        WebDavItemProperty requestBody,
                        List<String> lockTokens,
                        String username) throws WebDavException {
    Session session = getSession(username);
    try {
      checkLock(session, path, lockTokens);
      writeCommandHandler.changeAcl(session, path, requestBody);
    } finally {
      session.logout();
    }
  }

  @SneakyThrows
  private JcrNamespaceContext buildNameSpaceContext() {
    Map<String, String> prefixes = new HashMap<>();
    Map<String, String> namespaces = new HashMap<>();

    prefixes.put("DAV:", "D");
    namespaces.put("D", "DAV:");

    NamespaceRegistry namespaceRegistry = repositoryService.getDefaultRepository().getNamespaceRegistry();
    String[] jcrPrefixes = namespaceRegistry.getPrefixes();
    for (String p : jcrPrefixes) {
      String u = namespaceRegistry.getURI(p);
      namespaces.put(p, u);
      prefixes.put(u, p);
    }
    return new JcrNamespaceContext(prefixes, namespaces);
  }

  @SneakyThrows
  private Session getSession(String username) {
    ManageableRepository repository = repositoryService.getDefaultRepository();
    RepositoryContainer repositoryContainer = ((RepositoryServiceImpl) repositoryService).getRepositoryContainer("repository");
    WorkspaceContainer workspaceContainer = repositoryContainer.getWorkspaceContainer(repository.getConfiguration()
                                                                                                .getDefaultWorkspaceName());
    return new SessionImpl(repository.getConfiguration().getDefaultWorkspaceName(),
                           new ConversationState(userAcl.getUserIdentity(username)),
                           workspaceContainer);
  }

  @SneakyThrows
  private Session getSession() {
    ManageableRepository repository = repositoryService.getDefaultRepository();
    return repository.getSystemSession(repository.getConfiguration().getDefaultWorkspaceName());
  }

  @SneakyThrows
  private void checkLock(Session session, String path, List<String> tokens) throws WebDavException {
    if (tokens != null && session.itemExists(path)) {
      Item item = session.getItem(path);
      if (item instanceof Node node && node.isLocked()) {
        Lock lock = node.getLock();
        String lockToken = lock.getLockToken();
        if (tokens.stream().noneMatch(l -> StringUtils.equalsIgnoreCase(l, lockToken))) {
          throw new WebDavException(HttpStatus.SC_LOCKED, "Resource already locked");
        }
      }
    }
  }

}
