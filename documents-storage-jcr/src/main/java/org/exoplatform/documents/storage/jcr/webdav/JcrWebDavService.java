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
package org.exoplatform.documents.storage.jcr.webdav;

import java.io.InputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.jcr.Item;
import javax.jcr.NamespaceRegistry;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.lock.Lock;
import javax.xml.namespace.NamespaceContext;
import javax.xml.namespace.QName;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;

import org.exoplatform.documents.storage.jcr.webdav.model.JcrNamespaceContext;
import org.exoplatform.documents.storage.jcr.webdav.plugin.WebdavReadCommandHandler;
import org.exoplatform.documents.storage.jcr.webdav.plugin.WebdavWriteCommandHandler;
import org.exoplatform.documents.webdav.model.WebDavException;
import org.exoplatform.documents.webdav.model.WebDavFileDownload;
import org.exoplatform.documents.webdav.model.WebDavItem;
import org.exoplatform.documents.webdav.model.WebDavItemOrder;
import org.exoplatform.documents.webdav.model.WebDavItemProperty;
import org.exoplatform.documents.webdav.model.WebDavLockResponse;
import org.exoplatform.documents.webdav.service.DocumentWebDavService;
import org.exoplatform.portal.config.UserACL;
import org.exoplatform.services.jcr.core.ManageableRepository;
import org.exoplatform.services.jcr.impl.RepositoryContainer;
import org.exoplatform.services.jcr.impl.RepositoryServiceImpl;
import org.exoplatform.services.jcr.impl.WorkspaceContainer;
import org.exoplatform.services.jcr.impl.core.SessionImpl;
import org.exoplatform.services.jcr.webdav.util.NodeTypeUtil;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.security.ConversationState;

import lombok.SneakyThrows;

public class JcrWebDavService implements DocumentWebDavService {

  public static final String          REPOSITORY_NAME = "repository";

  public static final String          DAS_VALUE       =
                                                "<DAV:basicsearch>" + "<exo:sql xmlns:exo=\"http://exoplatform.com/jcr\"/>" +
                                                    "<exo:xpath xmlns:exo=\"http://exoplatform.com/jcr\"/>";

  protected static final Log          LOG             = ExoLogger.getLogger(JcrWebDavService.class);

  protected WebdavReadCommandHandler  readCommandHandler;

  protected WebdavWriteCommandHandler writeCommandHandler;

  protected RepositoryServiceImpl     repositoryService;

  protected UserACL                   userAcl;

  private NamespaceContext            namespaceContext;

  public JcrWebDavService(WebdavReadCommandHandler readCommandHandler,
                          WebdavWriteCommandHandler writeCommandHandler,
                          RepositoryServiceImpl repositoryService,
                          UserACL userAcl) {
    this.readCommandHandler = readCommandHandler;
    this.writeCommandHandler = writeCommandHandler;
    this.repositoryService = repositoryService;
    this.userAcl = userAcl;
  }

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
  public boolean isFile(String webDavPath) {
    Session session = getSystemSession();
    try {
      return readCommandHandler.isFile(session, webDavPath);
    } finally {
      session.logout();
    }
  }

  @Override
  @SneakyThrows
  public WebDavFileDownload download(String webDavPath, String version, String baseUri, String username) throws WebDavException {
    Session session = getSession(username);
    try {
      return readCommandHandler.download(session,
                                         webDavPath,
                                         version);
    } finally {
      session.logout();
    }
  }

  @Override
  public long getLastModifiedDate(String webDavPath, String version) throws WebDavException {
    Session session = getSystemSession();
    try {
      return readCommandHandler.getLastModifiedDate(session,
                                                    webDavPath,
                                                    version);
    } finally {
      session.logout();
    }
  }

  @Override
  public WebDavItem get(String webDavPath,
                        String propRequestType,
                        Set<QName> requestedPropertyNames,
                        boolean requestPropertyNamesOnly,
                        int depth,
                        String baseUri,
                        String username) throws WebDavException {
    Session session = getSession(username);
    try {
      return readCommandHandler.get(session,
                                    webDavPath,
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
  public String getWebDavPath(String jcrPath, String username) throws WebDavException {
    Session session = getSession(username);
    try {
      return readCommandHandler.getWebDavPath(session, jcrPath, username);
    } finally {
      session.logout();
    }
  }

  @Override
  public List<WebDavItem> search(String webDavPath,
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
  public List<WebDavItem> getVersions(String webDavPath,
                                      Set<QName> requestedPropertyNames,
                                      String baseUri,
                                      String username) {
    Session session = getSession(username);
    try {
      return readCommandHandler.getVersions(session,
                                            webDavPath,
                                            requestedPropertyNames,
                                            baseUri);
    } finally {
      session.logout();
    }
  }

  @Override
  public void createFolder(String webDavPath,
                           String folderType,
                           String contentNodeType,
                           String mixinTypes,
                           List<String> lockTokens,
                           String username) throws WebDavException {
    Session session = getSession(username);
    try {
      checkLock(session, webDavPath, lockTokens);
      writeCommandHandler.createFolder(session,
                                       webDavPath,
                                       NodeTypeUtil.getMixinTypes(mixinTypes));
    } finally {
      session.logout();
    }
  }

  @Override
  public void saveFile(String webDavPath,
                       String fileType,
                       String contentNodeType,
                       String mediaType,
                       String mixinTypes,
                       InputStream inputStream,
                       List<String> lockTokens,
                       String username) throws WebDavException {
    Session session = getSession(username);
    try {
      checkLock(session, webDavPath, lockTokens);
      writeCommandHandler.saveFile(session,
                                   webDavPath,
                                   mediaType,
                                   NodeTypeUtil.getMixinTypes(mixinTypes),
                                   inputStream);
    } finally {
      session.logout();
    }
  }

  @Override
  public void delete(String webDavPath,
                     List<String> lockTokens,
                     String username) throws WebDavException {
    Session session = getSession(username);
    try {
      checkLock(session, webDavPath, lockTokens);
      writeCommandHandler.delete(session, webDavPath);
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
  public Map<String, Collection<WebDavItemProperty>> saveProperties(String webDavPath,
                                                                    List<WebDavItemProperty> propertiesToSave,
                                                                    List<WebDavItemProperty> propertiesToRemove,
                                                                    List<String> lockTokens,
                                                                    String username) throws WebDavException {
    Session session = getSession(username);
    try {
      checkLock(session, webDavPath, lockTokens);
      return writeCommandHandler.saveProperties(session,
                                                webDavPath,
                                                propertiesToSave,
                                                propertiesToRemove);
    } finally {
      session.logout();
    }
  }

  @Override
  public void enableVersioning(String webDavPath,
                               List<String> lockTokens,
                               String username) throws WebDavException {
    Session session = getSession(username);
    try {
      checkLock(session, webDavPath, lockTokens);
      writeCommandHandler.enableVersioning(session, webDavPath);
    } finally {
      session.logout();
    }
  }

  @Override
  public void checkin(String webDavPath,
                      List<String> lockTokens,
                      String username) throws WebDavException {
    Session session = getSession(username);
    try {
      checkLock(session, webDavPath, lockTokens);
      writeCommandHandler.checkin(session, webDavPath);
    } finally {
      session.logout();
    }
  }

  @Override
  public void checkout(String webDavPath,
                       List<String> lockTokens,
                       String username) throws WebDavException {
    Session session = getSession(username);
    try {
      checkLock(session, webDavPath, lockTokens);
      writeCommandHandler.checkout(session, webDavPath);
    } finally {
      session.logout();
    }
  }

  @Override
  public void uncheckout(String webDavPath,
                         List<String> lockTokens,
                         String username) throws WebDavException {
    Session session = getSession(username);
    try {
      checkLock(session, webDavPath, lockTokens);
      writeCommandHandler.uncheckout(session, webDavPath);
    } finally {
      session.logout();
    }
  }

  @Override
  public WebDavLockResponse lock(String webDavPath,
                                 int depth,
                                 int lockTimeout,
                                 boolean bodyIsEmpty,
                                 List<String> lockTokens,
                                 String username) throws WebDavException {
    Session session = getSession(username);
    try {
      checkLock(session, webDavPath, lockTokens);
      return writeCommandHandler.lock(session,
                                      webDavPath,
                                      depth,
                                      lockTimeout,
                                      bodyIsEmpty,
                                      username);
    } finally {
      session.logout();
    }
  }

  @Override
  public void unlock(String webDavPath,
                     List<String> lockTokens,
                     String username) throws WebDavException {
    Session session = getSession(username);
    try {
      checkLock(session, webDavPath, lockTokens);
      writeCommandHandler.unlock(session, webDavPath, lockTokens);
    } finally {
      session.logout();
    }
  }

  @Override
  public boolean order(String webDavPath,
                       List<WebDavItemOrder> members,
                       List<String> lockTokens,
                       String username) throws WebDavException {
    Session session = getSession(username);
    try {
      checkLock(session, webDavPath, lockTokens);
      return writeCommandHandler.order(session, webDavPath, members);
    } finally {
      session.logout();
    }
  }

  public void unlockTimedOutNodes() {
    List<String> nodePaths = writeCommandHandler.getOutdatedLockedNodePaths();
    if (CollectionUtils.isNotEmpty(nodePaths)) {
      Session session = getSystemSession();
      nodePaths.forEach(jcrPath -> {
        try {
          writeCommandHandler.unlockNode(session, jcrPath);
          LOG.info("Node with path {} was automatically unlocked after the lock timed out", jcrPath);
        } catch (Exception e) {
          LOG.warn("Error while automatically unlocking node with path {}. Delete it to not attempt unlocking it again",
                   jcrPath,
                   e);
          writeCommandHandler.removeLockTimeout(jcrPath);
        }
      });
    }
  }

  @SneakyThrows
  public Session getSession(String username) {
    ManageableRepository repository = repositoryService.getDefaultRepository();
    RepositoryContainer repositoryContainer = repositoryService.getRepositoryContainer(REPOSITORY_NAME);
    WorkspaceContainer workspaceContainer = repositoryContainer.getWorkspaceContainer(repository.getConfiguration()
                                                                                                .getDefaultWorkspaceName());
    return newSession(username, repository, workspaceContainer);
  }

  @SneakyThrows
  public Session getSystemSession() {
    ManageableRepository repository = repositoryService.getDefaultRepository();
    return repository.getSystemSession(repository.getConfiguration().getDefaultWorkspaceName());
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
  private void checkLock(Session session, String jcrPath, List<String> tokens) throws WebDavException {
    if (tokens != null && session.itemExists(jcrPath)) {
      Item item = session.getItem(jcrPath);
      if (item instanceof Node node && node.isLocked()) {
        Lock lock = node.getLock();
        String lockToken = lock.getLockToken();
        if (tokens.stream().noneMatch(l -> StringUtils.equalsIgnoreCase(l, lockToken))) {
          throw new WebDavException(HttpStatus.SC_LOCKED,
                                    String.format("Resource with path '%s' is already locked by a different owner %s",
                                                  jcrPath,
                                                  lock.getLockOwner()));
        }
      }
    }
  }

  public Session newSession(String username,
                            ManageableRepository repository,
                            WorkspaceContainer workspaceContainer) throws RepositoryException {
    return new SessionImpl(repository.getConfiguration().getDefaultWorkspaceName(),
                           new ConversationState(userAcl.getUserIdentity(username)),
                           workspaceContainer);
  }

}
