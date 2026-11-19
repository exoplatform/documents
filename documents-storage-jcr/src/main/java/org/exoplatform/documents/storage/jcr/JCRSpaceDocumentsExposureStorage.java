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
 *
*/
package org.exoplatform.documents.storage.jcr;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import javax.jcr.ItemNotFoundException;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.query.Query;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import org.exoplatform.documents.model.ExposedDocument;
import org.exoplatform.documents.storage.SpaceDocumentsExposureStorage;
import org.exoplatform.documents.storage.TrashStorage;
import org.exoplatform.documents.util.SpaceAccessClassifier;
import org.exoplatform.documents.storage.jcr.util.JCRDocumentsUtil;
import org.exoplatform.documents.storage.jcr.util.NodeTypeConstants;
import org.exoplatform.services.cms.documents.security.DocumentSpaceGuard;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.core.ExtendedNode;
import org.exoplatform.services.jcr.core.ExtendedSession;
import org.exoplatform.services.jcr.core.ManageableRepository;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.exoplatform.services.jcr.ext.hierarchy.NodeHierarchyCreator;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.security.IdentityConstants;
import org.exoplatform.social.core.space.model.Space;

/**
 * Reads what a space's documents expose outside the space through a system
 * session: the caller checks, before it calls, that the user may manage the
 * space.
 */
@Component
public class JCRSpaceDocumentsExposureStorage implements SpaceDocumentsExposureStorage {

  /**
   * The documents and folders under a root that carry their own access list,
   * formatted with the root's path. The JCR-SQL grammar refuses two
   * {@code jcr:path} clauses joined by OR, so each root has a query of its own.
   */
  public static final String         ACCESS_LISTS_QUERY        =
                                                              "SELECT * FROM nt:base WHERE jcr:path LIKE '%s/%%' AND jcr:mixinTypes = 'exo:privilegeable'";

  /** The root of the documents the DLP quarantines */
  public static final String         QUARANTINE_ROOT           = "/Quarantine";

  private static final String        DRIVE_FOLDER_NAME         = "Documents";

  private static final Log           LOG                       = ExoLogger.getLogger(JCRSpaceDocumentsExposureStorage.class);

  private final RepositoryService    repositoryService;

  private final NodeHierarchyCreator nodeHierarchyCreator;

  private final TrashStorage         trashStorage;

  public JCRSpaceDocumentsExposureStorage(RepositoryService repositoryService,
                                          NodeHierarchyCreator nodeHierarchyCreator,
                                          TrashStorage trashStorage) {
    this.repositoryService = repositoryService;
    this.nodeHierarchyCreator = nodeHierarchyCreator;
    this.trashStorage = trashStorage;
  }

  @Override
  public List<ExposedDocument> getSpaceDocuments(Space space, Collection<String> documentIds) {
    SessionProvider sessionProvider = SessionProvider.createSystemProvider();
    try {
      Session session = getSystemSession(sessionProvider);
      List<ExposedDocument> documents = new ArrayList<>();
      for (String documentId : documentIds) {
        Node node = getNode(session, documentId);
        if (node != null && belongsToSpace(node, space)) {
          documents.add(toExposedDocument(node, documentId));
        }
      }
      return documents;
    } catch (RepositoryException e) {
      throw new IllegalStateException("The documents of space " + space.getId() + " could not be read", e);
    } finally {
      sessionProvider.close();
    }
  }

  @Override
  public List<ExposedDocument> getSharedOutsideDocuments(Space space, Predicate<String> isMember) {
    SessionProvider sessionProvider = SessionProvider.createSystemProvider();
    try {
      Session session = getSystemSession(sessionProvider);
      String driveRootPath = getDriveRootPath(space);
      Set<String> driveRootIdentities = new HashSet<>();
      if (session.itemExists(driveRootPath)) {
        Map<String, List<String>> driveRootAccessList = getDocumentSpaceGuard().getAccessList((Node) session.getItem(driveRootPath));
        if (driveRootAccessList != null) {
          driveRootIdentities.addAll(driveRootAccessList.keySet());
        }
      }
      SpaceAccessClassifier classifier = new SpaceAccessClassifier(space.getGroupId(), isMember, driveRootIdentities);

      List<ExposedDocument> documents = new ArrayList<>();
      addSharedOutsideDocuments(session, String.format(ACCESS_LISTS_QUERY, driveRootPath), null, classifier, documents);
      Node trashHome = trashStorage.getTrashHomeNode();
      if (trashHome != null) {
        addSharedOutsideDocuments(session, String.format(ACCESS_LISTS_QUERY, trashHome.getPath()), space, classifier, documents);
      }
      addSharedOutsideDocuments(session, String.format(ACCESS_LISTS_QUERY, QUARANTINE_ROOT), space, classifier, documents);
      return documents;
    } catch (RepositoryException e) {
      throw new IllegalStateException("The documents shared outside space " + space.getId() + " could not be read", e);
    } finally {
      sessionProvider.close();
    }
  }

  @Override
  public ExposedDocument prepareLinkRemoval(Space space, String documentId, boolean removeEveryone) {
    SessionProvider sessionProvider = SessionProvider.createSystemProvider();
    try {
      Session session = getSystemSession(sessionProvider);
      Node node = getNode(session, documentId);
      if (node == null || !belongsToSpace(node, space)) {
        return null;
      }
      if (removeEveryone) {
        removeEveryoneAccess(node);
      }
      return toExposedDocument(node, documentId);
    } catch (RepositoryException e) {
      throw new IllegalStateException("The public link of document " + documentId + " could not be prepared for its removal", e);
    } finally {
      sessionProvider.close();
    }
  }

  protected DocumentSpaceGuard getDocumentSpaceGuard() {
    return DocumentSpaceGuard.getInstance();
  }

  protected Session getSystemSession(SessionProvider sessionProvider) throws RepositoryException {
    ManageableRepository repository = repositoryService.getCurrentRepository();
    return sessionProvider.getSession(repository.getConfiguration().getDefaultWorkspaceName(), repository);
  }

  /**
   * @param belongingSpace when not null, a listed node is kept only when it
   *          resolves to this space: the trash and the quarantine hold every
   *          space's documents
   */
  private void addSharedOutsideDocuments(Session session,
                                         String statement,
                                         Space belongingSpace,
                                         SpaceAccessClassifier classifier,
                                         List<ExposedDocument> documents) throws RepositoryException {
    Query query = session.getWorkspace().getQueryManager().createQuery(statement, Query.SQL);
    NodeIterator nodes = query.execute().getNodes();
    while (nodes.hasNext()) {
      Node node = nodes.nextNode();
      if (node.isNodeType(NodeTypeConstants.EXO_SYMLINK)
          || (belongingSpace != null && !belongsToSpace(node, belongingSpace))) {
        continue;
      }
      Map<String, List<String>> accessList = getDocumentSpaceGuard().getAccessList(node);
      String owner = getOwner(node);
      if (accessList != null && classifier.hasOutsideEntry(accessList.keySet(), owner)) {
        documents.add(toExposedDocument(node, ((ExtendedNode) node).getIdentifier()));
      }
    }
  }

  /**
   * Takes the everyone access off the node's own access list, through its
   * permission API; a node with no access list of its own has none to take
   * off
   */
  private void removeEveryoneAccess(Node node) throws RepositoryException {
    Map<String, List<String>> accessList = getDocumentSpaceGuard().getAccessList(node);
    if (accessList == null || !accessList.containsKey(IdentityConstants.ANY)) {
      return;
    }
    if (!node.isCheckedOut()) {
      node.checkout();
    }
    ((ExtendedNode) node).removePermission(IdentityConstants.ANY);
    node.save();
  }

  /**
   * A node that exists but points to an item that no longer does (a
   * shortcut's target, a version's document) is placed by its own stored
   * path: it belongs to the space only when that path lies in the space's
   * drive. Any other read error is the caller's failure.
   */
  private boolean belongsToSpace(Node node, Space space) throws RepositoryException {
    Space documentSpace;
    try {
      documentSpace = getDocumentSpaceGuard().getDocumentSpace(node);
    } catch (ItemNotFoundException | PathNotFoundException e) {
      boolean inSpaceDrive = node.getPath().startsWith(getDriveRootPath(space) + "/");
      LOG.warn("Node {} points to an item that no longer exists: placed by its own path, {} space {}",
               ((ExtendedNode) node).getIdentifier(),
               inSpaceDrive ? "in" : "not in",
               space.getId());
      return inSpaceDrive;
    }
    return documentSpace != null && StringUtils.equals(documentSpace.getId(), space.getId());
  }

  private String getDriveRootPath(Space space) {
    return JCRDocumentsUtil.getGroupsPath(nodeHierarchyCreator) + space.getGroupId() + "/" + DRIVE_FOLDER_NAME;
  }

  private ExposedDocument toExposedDocument(Node node, String documentId) throws RepositoryException {
    boolean folder = JCRDocumentsUtil.isFolder(node);
    return new ExposedDocument(documentId,
                               JCRDocumentsUtil.getTitle(node),
                               folder ? null : StringUtils.defaultIfBlank(JCRDocumentsUtil.getMimeType(node), null),
                               folder,
                               getOwner(node));
  }

  private static Node getNode(Session session, String documentId) throws RepositoryException {
    if (StringUtils.isBlank(documentId)) {
      return null;
    }
    try {
      return ((ExtendedSession) session).getNodeByIdentifier(documentId);
    } catch (ItemNotFoundException | PathNotFoundException e) {
      return null;
    }
  }

  private static String getOwner(Node node) throws RepositoryException {
    return node.hasProperty(NodeTypeConstants.EXO_OWNER) ? node.getProperty(NodeTypeConstants.EXO_OWNER).getString() : null;
  }

}
