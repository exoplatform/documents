/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <gnu.org/licenses>.
 */
package org.exoplatform.document.cleanup.storage;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.function.BiConsumer;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.ReferentialIntegrityException;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.observation.Event;
import javax.jcr.observation.ObservationManager;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.version.Version;
import javax.jcr.version.VersionHistory;
import javax.jcr.version.VersionIterator;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.exoplatform.document.cleanup.constant.CleanupExemptionResult;
import org.exoplatform.document.cleanup.listener.CleanupJcrObservationListener;
import org.exoplatform.document.cleanup.model.CleanupCandidate;
import org.exoplatform.document.cleanup.model.CleanupParams;
import org.exoplatform.document.cleanup.model.CleanupPurgeResult;
import org.exoplatform.document.cleanup.model.CleanupRevalidation;
import org.exoplatform.document.cleanup.util.CleanupConstants;
import org.exoplatform.document.cleanup.util.CleanupCriterionEvaluator;
import org.exoplatform.documents.storage.TrashStorage;
import org.exoplatform.documents.storage.jcr.util.JCRDocumentsUtil;
import org.exoplatform.documents.storage.jcr.util.NodeTypeConstants;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.core.ExtendedNode;
import org.exoplatform.services.jcr.ext.app.SessionProviderService;
import org.exoplatform.services.jcr.observation.ExtendedEvent;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.spi.SpaceService;

import lombok.Synchronized;

/**
 * JCR implementation of {@link CleanupJcrStorage} against the collaboration
 * workspace, always through a system session (async cleanup workers carry no
 * user conversation state).
 */
@Component
public class CleanupJcrStorage {

  private static final Log              LOG                       = ExoLogger.getLogger(CleanupJcrStorage.class);

  public static final String            COLLABORATION             = "collaboration";

  public static final String            EXO_CLEANUP_EXEMPTION     = "exo:cleanupExemption";

  public static final String            EXO_CLEANUP_EXEMPTED_DATE = "exo:cleanupExemptedDate";

  public static final String            EXO_CLEANUP_EXEMPTED_BY   = "exo:cleanupExemptedBy";

  private static final String           JCR_ROOT_VERSION          = "jcr:rootVersion";

  @Autowired
  private RepositoryService             repositoryService;

  @Autowired
  private SessionProviderService        sessionProviderService;

  @Autowired
  private TrashStorage                  trashStorage;

  @Autowired
  private IdentityManager               identityManager;

  @Autowired
  private SpaceService                  spaceService;

  private CleanupJcrObservationListener observationListener;

  /**
   * @param rootPath scanned tree root path (e.g. /Users or /Groups/spaces)
   * @return total number of nt:file nodes under the given root
   */
  public long countFiles(String rootPath) {
    try {
      Session session = getSystemSession();
      QueryManager queryManager = session.getWorkspace().getQueryManager();
      Query query = queryManager.createQuery(buildScanQuery(rootPath), Query.SQL);
      return query.execute().getNodes().getSize();
    } catch (RepositoryException e) {
      LOG.warn("Error counting nt:file nodes under {}", rootPath, e);
      return 0;
    }
  }

  /**
   * Scans the path-ordered nt:file nodes under the given root with a SINGLE
   * query, streaming the qualifying cleanup candidates to the consumer in
   * batches of {@code batchSize} scanned nodes (the last batch may cover
   * fewer). When resuming, the result is fast-forwarded BY PATH: nodes whose
   * path is not strictly greater than {@code resumeAfterPath} are skipped
   * without processing. Unlike a numeric offset, this stays correct when the
   * tree changed during the interruption (no file shifted before the cursor is
   * silently missed), including when the checkpointed node itself no longer
   * exists.
   *
   * @param rootPath scanned tree root path
   * @param resumeAfterPath path of the last node processed before the
   *          interruption (resume checkpoint), null or blank for a fresh scan
   * @param batchSize number of scanned nodes per consumer invocation
   * @param params campaign parameters snapshot
   * @param batchConsumer receives each scanned batch; returns false to abort
   *          the scan of this root
   * @throws IllegalStateException on a JCR failure, so the caller can leave the
   *           scan resumable from its last persisted checkpoint
   */
  public void scanRoot(String rootPath,
                       String resumeAfterPath,
                       int batchSize,
                       CleanupParams params,
                       ScanBatchConsumer batchConsumer) { // NOSONAR
    try {
      Session session = getSystemSession();
      QueryManager queryManager = session.getWorkspace().getQueryManager();
      Query query = queryManager.createQuery(buildScanQuery(rootPath), Query.SQL);
      // Single ordered query per root per scan run; on resume, fast-forward
      // past the checkpoint path instead of trusting a positional offset
      NodeIterator nodes = query.execute().getNodes();
      boolean fastForwarding = StringUtils.isNotBlank(resumeAfterPath);
      List<CleanupCandidate> candidates = new ArrayList<>();
      int scannedInBatch = 0;
      String lastScannedPath = null;
      while (nodes.hasNext()) {
        Node node = nodes.nextNode();
        String path;
        try { // NOSONAR
          path = node.getPath();
        } catch (RepositoryException e) {
          LOG.warn("Error reading scanned node path, skipping it", e);
          continue;
        }
        if (fastForwarding) {
          if (path.compareTo(resumeAfterPath) <= 0) {
            // Skipped without processing: already covered before the
            // interruption (strictly-greater comparison, so a checkpointed
            // node that no longer exists still positions correctly)
            continue;
          }
          fastForwarding = false;
        }
        lastScannedPath = path;
        try { // NOSONAR
          CleanupCandidate candidate = toCandidate(node, params);
          if (candidate != null) {
            candidates.add(candidate);
          }
        } catch (Exception e) {
          LOG.warn("Error evaluating cleanup candidate node, skipping it", e);
        }
        if (++scannedInBatch == batchSize) {
          if (!batchConsumer.onBatch(candidates, lastScannedPath, scannedInBatch)) {
            return;
          }
          candidates = new ArrayList<>();
          scannedInBatch = 0;
        }
      }
      if (scannedInBatch > 0) {
        batchConsumer.onBatch(candidates, lastScannedPath, scannedInBatch);
      }
    } catch (RepositoryException e) {
      // Propagate so the scan worker leaves the campaign resumable from its
      // last persisted checkpoint instead of moving on to the next root
      throw new IllegalStateException("Error scanning nt:file nodes under " + rootPath
          + (StringUtils.isBlank(resumeAfterPath) ? "" : " (resuming after " + resumeAfterPath + ")"), e);
    }
  }

  /**
   * Receives the scanned batches streamed by
   * {@link CleanupJcrStorage#scanRoot(String, String, int, CleanupParams, ScanBatchConsumer)}.
   */
  @FunctionalInterface
  public interface ScanBatchConsumer {

    /**
     * @param candidates qualifying candidates of the batch, possibly empty
     * @param lastScannedPath path of the last node scanned by the batch (the
     *          resume checkpoint), never null since a batch scans at least one
     *          node
     * @param scannedCount number of nodes scanned by the batch
     * @return true to continue the scan, false to abort the scan of this root
     */
    boolean onBatch(List<CleanupCandidate> candidates, String lastScannedPath, int scannedCount);

  }

  /**
   * Re-evaluates a node against the campaign criteria, at execution or refresh
   * time.
   *
   * @param nodeUuid JCR node identifier
   * @param params campaign parameters snapshot
   * @return revalidation outcome (gone, exempted, spared or still candidate)
   */
  public CleanupRevalidation revalidate(String nodeUuid, CleanupParams params) {
    try {
      Session session = getSystemSession();
      Node node = JCRDocumentsUtil.getNodeByIdentifier(session, nodeUuid);
      if (node == null) {
        return CleanupRevalidation.gone();
      } else if (node.isNodeType(EXO_CLEANUP_EXEMPTION)) {
        return CleanupRevalidation.exempted();
      } else {
        return CleanupRevalidation.of(toCandidate(node, params));
      }
    } catch (RepositoryException e) {
      LOG.warn("Error revalidating cleanup candidate node {}", nodeUuid, e);
      return CleanupRevalidation.of(null);
    }
  }

  /**
   * Adds the exo:cleanupExemption mixin on the node, keeping real modification
   * dates untouched.
   *
   * @param nodeUuid JCR node identifier
   * @param username user who decided to keep the file
   * @return {@link CleanupExemptionResult#ADDED} when the node is now exempted,
   *         {@link CleanupExemptionResult#NOT_FOUND} when it doesn't exist
   *         anymore, {@link CleanupExemptionResult#FAILED} on a (possibly
   *         transient) JCR write failure
   */
  public CleanupExemptionResult addExemptionMixin(String nodeUuid, String username) {
    Session session = null;
    try {
      session = getSystemSession();
      Node node = JCRDocumentsUtil.getNodeByIdentifier(session, nodeUuid);
      if (node == null) {
        return CleanupExemptionResult.NOT_FOUND;
      }
      if (node.isNodeType(NodeTypeConstants.MIX_VERSIONABLE) && !node.isCheckedOut()) {
        // A checked-in versionable node rejects property writes: check it out
        // first. Deliberately NO checkin afterward — leaving the node
        // checked-out is acceptable and avoids creating a spurious version.
        node.checkout();
      }
      if (!node.isNodeType(EXO_CLEANUP_EXEMPTION)) {
        node.addMixin(EXO_CLEANUP_EXEMPTION);
      }
      node.setProperty(EXO_CLEANUP_EXEMPTED_DATE, Calendar.getInstance());
      node.setProperty(EXO_CLEANUP_EXEMPTED_BY, username);
      session.save();
      return CleanupExemptionResult.ADDED;
    } catch (RepositoryException e) {
      refresh(session);
      LOG.warn("Error adding cleanup exemption mixin on node {}", nodeUuid, e);
      return CleanupExemptionResult.FAILED;
    }
  }

  /**
   * Hard-deletes a file node (no trash): removes pointing symlinks first, then
   * the node, then now-empty ancestor folders up to (excluding) the drive root.
   *
   * @param nodeUuid JCR node identifier
   * @return purge outcome with reclaimed bytes, or SKIPPED with a reason
   */
  public CleanupPurgeResult deleteNode(String nodeUuid) {
    Session session = null;
    try {
      session = getSystemSession();
      Node node = JCRDocumentsUtil.getNodeByIdentifier(session, nodeUuid);
      if (node == null) {
        return CleanupPurgeResult.gone();
      }
      long reclaimedBytes = getContentSize(node) + JCRDocumentsUtil.computeVersionsSize(node);
      String nodePath = node.getPath();
      // Remove pointing symlinks first
      if (!node.isNodeType(NodeTypeConstants.EXO_SYMLINK)) {
        for (Node symlink : trashStorage.getAllLinks(node, NodeTypeConstants.EXO_SYMLINK)) {
          symlink.remove();
          symlink.getSession().save();
        }
      }
      Node parentNode = node.getParent();
      node.remove();
      session.save();
      removeEmptyAncestors(session, parentNode, nodePath);
      return CleanupPurgeResult.purged(reclaimedBytes);
    } catch (ReferentialIntegrityException e) {
      refresh(session);
      return CleanupPurgeResult.skipped("cleanup.referentialIntegrity: " + e.getMessage());
    } catch (RepositoryException e) {
      refresh(session);
      LOG.warn("Error hard-deleting node {}", nodeUuid, e);
      return CleanupPurgeResult.skipped("cleanup.deleteError: " + e.getMessage());
    }
  }

  /**
   * Purges oldest versions of a file down to the given maximum, always keeping
   * the current (base) version.
   *
   * @param nodeUuid JCR node identifier
   * @param maxVersionsPerFile number of versions to keep
   * @return purge outcome with reclaimed bytes, or SKIPPED with a reason
   */
  public CleanupPurgeResult purgeVersions(String nodeUuid, int maxVersionsPerFile) {
    try {
      Session session = getSystemSession();
      Node node = JCRDocumentsUtil.getNodeByIdentifier(session, nodeUuid);
      if (node == null) {
        return CleanupPurgeResult.gone();
      } else if (!node.isNodeType(NodeTypeConstants.MIX_VERSIONABLE)) {
        return CleanupPurgeResult.skipped("cleanup.notVersionable");
      }
      VersionHistory versionHistory = node.getVersionHistory();
      String baseVersionName = node.getBaseVersion().getName();
      int versionCount = countVersions(versionHistory);
      int toRemove = versionCount - maxVersionsPerFile;
      long reclaimedBytes = 0;
      if (toRemove > 0) {
        // VersionIterator is ordered by creation date: oldest versions first
        VersionIterator versions = versionHistory.getAllVersions();
        while (versions.hasNext() && toRemove > 0) {
          Version version = versions.nextVersion();
          String versionName = version.getName();
          if (JCR_ROOT_VERSION.equals(versionName) || baseVersionName.equals(versionName)) {
            continue;
          }
          long versionSize = getVersionSize(version);
          versionHistory.removeVersion(versionName);
          reclaimedBytes += versionSize;
          toRemove--;
        }
      }
      return CleanupPurgeResult.purged(reclaimedBytes);
    } catch (RepositoryException e) {
      LOG.warn("Error purging versions of node {}", nodeUuid, e);
      return CleanupPurgeResult.skipped("cleanup.purgeVersionsError: " + e.getMessage());
    }
  }

  /**
   * Registers a JCR observation listener forwarding (nodePath, eventType) pairs
   * for changes under the scanned roots. Idempotent.
   *
   * @param pathAndEventTypeCallback callback receiving the event node path and
   *          the event type name
   * @return true when the listener is registered (or already was), false when
   *         the registration failed (e.g. JCR not ready yet) and may be retried
   */
  @Synchronized
  public boolean registerObservationListener(BiConsumer<String, String> pathAndEventTypeCallback) {
    if (observationListener != null) {
      return true;
    }
    try {
      ObservationManager observationManager = getSystemSession().getWorkspace().getObservationManager();
      observationListener = new CleanupJcrObservationListener(pathAndEventTypeCallback);
      observationManager.addEventListener(observationListener,
                                          Event.PROPERTY_CHANGED | Event.NODE_REMOVED | ExtendedEvent.NODE_MOVED,
                                          "/",
                                          true,
                                          null,
                                          null,
                                          false);
      return true;
    } catch (RepositoryException e) {
      observationListener = null;
      // Callers decide the log level: startup retries in a backoff loop and
      // warns only after the final failure
      LOG.debug("Error registering cleanup JCR observation listener", e);
      return false;
    }
  }

  /**
   * Unregisters the JCR observation listener, if registered. Idempotent.
   */
  public synchronized void unregisterObservationListener() {
    if (observationListener == null) {
      return;
    }
    try {
      getSystemSession().getWorkspace().getObservationManager().removeEventListener(observationListener);
    } catch (RepositoryException e) {
      LOG.warn("Error unregistering cleanup JCR observation listener", e);
    } finally {
      observationListener = null;
    }
  }

  private CleanupCandidate toCandidate(Node node, CleanupParams params) throws RepositoryException {
    String path = node.getPath();
    long createdTime = node.hasProperty(NodeTypeConstants.JCR_CREATED_DATE) ? node.getProperty(NodeTypeConstants.JCR_CREATED_DATE)
                                                                                  .getDate()
                                                                                  .getTimeInMillis() :
                                                                            0;
    long lastModifiedTime = JCRDocumentsUtil.getLastModifiedDate(node);
    long fileSize = getContentSize(node);
    long versionsSize = JCRDocumentsUtil.computeVersionsSize(node);
    int versionCount = node.isNodeType(NodeTypeConstants.MIX_VERSIONABLE) ? countVersions(node.getVersionHistory()) : 0;
    boolean hasExemptionMixin = node.isNodeType(EXO_CLEANUP_EXEMPTION);
    // Candidacy policy defined in the api module, applied here at scan time to
    // avoid shipping every node upward
    var action = CleanupCriterionEvaluator.evaluate(createdTime,
                                                    lastModifiedTime,
                                                    fileSize,
                                                    versionsSize,
                                                    versionCount,
                                                    path,
                                                    hasExemptionMixin,
                                                    params,
                                                    System.currentTimeMillis());
    if (action == null) {
      return null;
    }
    Identity ownerIdentity = JCRDocumentsUtil.getOwnerIdentityFromNodePath(path, identityManager, spaceService);
    long ownerIdentityId = ownerIdentity == null ? 0 : Long.parseLong(ownerIdentity.getId());
    return new CleanupCandidate(((ExtendedNode) node).getIdentifier(),
                                path,
                                ownerIdentityId,
                                fileSize,
                                versionsSize,
                                action,
                                createdTime,
                                lastModifiedTime);
  }

  /**
   * Removes now-empty ancestor folders of a deleted node, stopping (and
   * excluding) at the drive root: the user private/public folder for /Users/...
   * paths, the space Documents folder for /Groups/spaces/... paths.
   */
  private void removeEmptyAncestors(Session session, Node parentNode, String deletedNodePath) {
    int minDepth = CleanupConstants.getDriveMinDepth(deletedNodePath);
    try {
      Node current = parentNode;
      while (current != null
             && current.getDepth() > minDepth
             && !current.hasNodes()
             && (current.isNodeType(NodeTypeConstants.NT_FOLDER) || current.isNodeType(NodeTypeConstants.NT_UNSTRUCTURED))) {
        Node ancestor = current.getParent();
        current.remove();
        session.save();
        current = ancestor;
      }
    } catch (RepositoryException e) {
      LOG.debug("Error removing empty ancestors of {}", deletedNodePath, e);
      refresh(session);
    }
  }

  private long getContentSize(Node node) {
    try {
      if (node.hasNode(NodeTypeConstants.JCR_CONTENT)) {
        Node content = node.getNode(NodeTypeConstants.JCR_CONTENT);
        if (content.hasProperty(NodeTypeConstants.JCR_DATA)) {
          return content.getProperty(NodeTypeConstants.JCR_DATA).getLength();
        }
      }
    } catch (RepositoryException e) {
      LOG.debug("Error reading content size of node", e);
    }
    return 0;
  }

  private int countVersions(VersionHistory versionHistory) throws RepositoryException {
    // getAllVersions includes the root version, excluded from the count
    return (int) versionHistory.getAllVersions().getSize() - 1;
  }

  private long getVersionSize(Version version) {
    try {
      if (version.hasNode(NodeTypeConstants.JCR_FROZEN_NODE)) {
        Node frozen = version.getNode(NodeTypeConstants.JCR_FROZEN_NODE);
        if (frozen.hasNode(NodeTypeConstants.JCR_CONTENT)
            && frozen.getNode(NodeTypeConstants.JCR_CONTENT).hasProperty(NodeTypeConstants.JCR_DATA)) {
          return frozen.getNode(NodeTypeConstants.JCR_CONTENT).getProperty(NodeTypeConstants.JCR_DATA).getLength();
        }
      }
    } catch (RepositoryException e) {
      LOG.debug("Error reading version size", e);
    }
    return 0;
  }

  private String buildScanQuery(String rootPath) {
    return "SELECT * FROM " + NodeTypeConstants.NT_FILE + " WHERE jcr:path LIKE '" + rootPath + "/%' ORDER BY jcr:path";
  }

  private Session getSystemSession() throws RepositoryException {
    return sessionProviderService.getSystemSessionProvider(null)
                                 .getSession(COLLABORATION, repositoryService.getCurrentRepository());
  }

  private void refresh(Session session) {
    try {
      if (session != null) {
        session.refresh(false);
      }
    } catch (RepositoryException e) {
      LOG.debug("Error refreshing JCR session after a failed cleanup operation", e);
    }
  }

}
