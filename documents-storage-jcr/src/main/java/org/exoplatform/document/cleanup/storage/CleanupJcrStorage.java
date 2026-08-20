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

import javax.jcr.ItemNotFoundException;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.PathNotFoundException;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import org.exoplatform.document.cleanup.constant.CleanupExemptionResult;
import org.exoplatform.document.cleanup.listener.CleanupJcrObservationListener;
import org.exoplatform.document.cleanup.model.CleanupCandidate;
import org.exoplatform.document.cleanup.model.CleanupParams;
import org.exoplatform.document.cleanup.model.CleanupPurgeResult;
import org.exoplatform.document.cleanup.model.CleanupRevalidation;
import org.exoplatform.document.cleanup.util.CleanupConstants;
import org.exoplatform.document.cleanup.util.CleanupCriterionEvaluator;
import org.exoplatform.document.cleanup.util.CleanupThrowableUtil;
import org.exoplatform.documents.storage.jcr.util.JCRDocumentsUtil;
import org.exoplatform.documents.storage.jcr.util.NodeTypeConstants;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.config.RepositoryConfigurationException;
import org.exoplatform.services.jcr.core.ExtendedNode;
import org.exoplatform.services.jcr.core.ExtendedSession;
import org.exoplatform.services.jcr.core.ManageableRepository;
import org.exoplatform.services.jcr.observation.ExtendedEvent;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.spi.SpaceService;

/**
 * JCR implementation of {@link CleanupJcrStorage} against the collaboration
 * workspace, always through a system session (async cleanup workers carry no
 * user conversation state).
 * <p>
 * SESSION LIFECYCLE — {@link #getSystemSession()} opens a BRAND-NEW system
 * session on every call, so every method obtaining one MUST release it in a
 * {@code finally} block through {@link #logout(Session)}. The per-item
 * primitives (revalidate / addExemptionMixin / removeExemptionMixin /
 * deleteNode / purgeVersions) are invoked once per campaign item: leaking one
 * session per call would leave tens of thousands of live sessions behind on a
 * large campaign. {@link #scanRoot} holds its session for the WHOLE streamed
 * walk (the {@link NodeIterator} is lazy) and logs out only once the iteration
 * is over, never inside the batch loop.
 * <p>
 * THE ONE EXCEPTION is the observation listener: a JCR listener is bound to the
 * session that registered it, so logging that session out would silently kill
 * the listener. {@link #registerObservationListener(BiConsumer)} therefore
 * keeps ONE dedicated long-lived session in {@link #observationSession}, logged
 * out only by {@link #unregisterObservationListener()} (or by a failed
 * registration).
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
  private IdentityManager               identityManager;

  @Autowired
  private SpaceService                  spaceService;

  @Value("${documents.cleanup.jcr.session.timeout:3600000}")
  private long                          jcrSessionTimeout;

  private CleanupJcrObservationListener observationListener;

  /**
   * Dedicated long-lived session owning the observation listener registration:
   * never logged out per call, see the class comment
   */
  private Session                       observationSession;

  /**
   * Enumerates the PARTITIONS of a dry-run scan — the subtrees a reader thread
   * each walks on its own — by applying the per-root policy of
   * {@link CleanupConstants}: the direct children of a split root
   * ({@link CleanupConstants#SPLIT_SCAN_ROOTS}) become units, an unsplit root
   * ({@link CleanupConstants#UNSPLIT_SCAN_ROOTS}) is itself one unit. Depth 1
   * only — read the SPLIT_SCAN_ROOTS comment on what a direct child of /Users
   * really is before touching this.
   * <p>
   * A root that does not exist is SKIPPED without failing the scan (/Trash is
   * legitimately absent on a fresh instance), and the paths come back sorted so
   * that re-planning a resumed campaign is deterministic.
   *
   * @return the planned unit paths, sorted
   * @throws IllegalStateException on a JCR failure, so the caller leaves the
   *           campaign resumable instead of scanning a partial tree
   */
  public List<String> listScanUnits() {
    Session session = null;
    try {
      session = getSystemSession();
      List<String> unitPaths = new ArrayList<>();
      for (String rootPath : CleanupConstants.SPLIT_SCAN_ROOTS) {
        Node rootNode = getNodeByPathOrNull(session, rootPath);
        if (rootNode == null) {
          LOG.debug("Cleanup scan root {} does not exist, no scan unit planned for it", rootPath);
          continue;
        }
        NodeIterator children = rootNode.getNodes();
        while (children.hasNext()) {
          unitPaths.add(children.nextNode().getPath());
        }
      }
      for (String rootPath : CleanupConstants.UNSPLIT_SCAN_ROOTS) {
        if (getNodeByPathOrNull(session, rootPath) == null) {
          LOG.debug("Cleanup scan root {} does not exist, no scan unit planned for it", rootPath);
        } else {
          unitPaths.add(rootPath);
        }
      }
      return unitPaths.stream().sorted().toList();
    } catch (Exception e) {
      throw new IllegalStateException("Error enumerating the cleanup scan units", e);
    } finally {
      logout(session);
    }
  }

  /**
   * @param rootPath scanned tree root path (e.g. /Users or /Groups/spaces)
   * @return total number of nt:file nodes under the given root
   */
  public long countFiles(String rootPath) {
    Session session = null;
    try {
      session = getSystemSession();
      QueryManager queryManager = session.getWorkspace().getQueryManager();
      Query query = queryManager.createQuery(buildScanQuery(rootPath), Query.SQL);
      return query.execute().getNodes().getSize();
    } catch (Exception e) {
      LOG.warn("Error counting nt:file nodes under {}", rootPath, e);
      return 0;
    } finally {
      logout(session);
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
  public void scanRoot(String rootPath, // NOSONAR
                       String resumeAfterPath,
                       int batchSize,
                       CleanupParams params,
                       ScanBatchConsumer batchConsumer) { // NOSONAR
    // ONE session for the whole streamed walk: the NodeIterator below is lazy,
    // so the session must stay open until the iteration is over — logged out
    // in the finally wrapping it, never inside the batch loop
    Session session = null;
    try {
      session = getSystemSession();
      QueryManager queryManager = session.getWorkspace().getQueryManager();
      Query query = queryManager.createQuery(buildScanQuery(rootPath), Query.SQL);
      // Single ordered query per root per scan run; on resume, fast-forward
      // past the checkpoint path instead of trusting a positional offset
      NodeIterator nodes = query.execute().getNodes();
      boolean fastForwarding = StringUtils.isNotBlank(resumeAfterPath);
      List<CleanupCandidate> candidates = new ArrayList<>();
      int scannedInBatch = 0;
      String lastScannedPath = null;
      while (nodes.hasNext()) { // NOSONAR
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
    } catch (Exception e) {
      // Propagate so the scan worker leaves the campaign resumable from its
      // last persisted checkpoint instead of moving on to the next root
      throw new IllegalStateException("Error scanning nt:file nodes under " + rootPath +
          (StringUtils.isBlank(resumeAfterPath) ? "" : " (resuming after " + resumeAfterPath + ")"), e);
    } finally {
      logout(session);
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
   * time. A transient JCR read failure yields a distinct UNKNOWN outcome —
   * never 'spared' nor 'gone' — so a flaky repository can neither permanently
   * spare an item nor let it be deleted on doubt: the node lookup is made
   * directly against the session (not through the null-swallowing helper) to
   * tell 'the node no longer exists' apart from 'the repository failed'.
   *
   * @param nodeUuid JCR node identifier
   * @param params campaign parameters snapshot
   * @return revalidation outcome (gone, exempted, spared, still candidate, or
   *         unknown on a JCR read failure)
   */
  public CleanupRevalidation revalidate(String nodeUuid, CleanupParams params) {
    Session session = null;
    try {
      session = getSystemSession();
      Node node = getNodeByIdentifierOrNull(session, nodeUuid);
      if (node == null) {
        return CleanupRevalidation.gone();
      } else if (node.isNodeType(EXO_CLEANUP_EXEMPTION)) {
        return CleanupRevalidation.exempted();
      } else {
        return CleanupRevalidation.of(toCandidate(node, params));
      }
    } catch (Exception e) {
      LOG.warn("Error revalidating cleanup candidate node {}", nodeUuid, e);
      return CleanupRevalidation.unknown();
    } finally {
      logout(session);
    }
  }

  /**
   * Node lookup distinguishing a MISSING node (null) from a repository failure
   * (propagated {@link RepositoryException}), unlike
   * {@link JCRDocumentsUtil#getNodeByIdentifier(Session, String)} which
   * swallows both into null. EVERY cleanup primitive goes through this lookup:
   * reporting an item NOT_FOUND / GONE on a transient repository failure would
   * durably discard the user's keep decision, or record a file as vanished
   * while it is still there.
   */
  /**
   * Node lookup BY PATH tolerating a missing node (null), used only by the unit
   * enumeration: a scan root absent from the workspace must skip its units, not
   * fail the whole scan.
   */
  private Node getNodeByPathOrNull(Session session, String path) throws RepositoryException {
    try {
      return session.itemExists(path) ? (Node) session.getItem(path) : null;
    } catch (PathNotFoundException | ItemNotFoundException e) {
      return null;
    }
  }

  private Node getNodeByIdentifierOrNull(Session session, String nodeUuid) throws RepositoryException {
    try {
      return ((ExtendedSession) session).getNodeByIdentifier(nodeUuid);
    } catch (PathNotFoundException | ItemNotFoundException e) {
      return null;
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
   *         transient) JCR read or write failure — never NOT_FOUND on doubt, so
   *         the user's keep decision is never discarded
   */
  public CleanupExemptionResult addExemptionMixin(String nodeUuid, String username) {
    Session session = null;
    try {
      session = getSystemSession();
      Node node = getNodeByIdentifierOrNull(session, nodeUuid);
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
    } catch (Exception e) {
      // No session.refresh here: the session is logged out in the finally right
      // below, which discards the pending changes anyway
      LOG.warn("Error adding cleanup exemption mixin on node {}", nodeUuid, e);
      return CleanupExemptionResult.FAILED;
    } finally {
      logout(session);
    }
  }

  /**
   * Removes the exo:cleanupExemption mixin from the node (un-keep), mirroring
   * {@link #addExemptionMixin(String, String)}: a checked-in versionable node
   * is checked out first, and real modification dates stay untouched.
   *
   * @param nodeUuid JCR node identifier
   * @return {@link CleanupExemptionResult#ADDED} when the node no longer
   *         carries the mixin (applied, idempotent),
   *         {@link CleanupExemptionResult#NOT_FOUND} when it doesn't exist
   *         anymore, {@link CleanupExemptionResult#FAILED} on a (possibly
   *         transient) JCR read or write failure — never NOT_FOUND on doubt, so
   *         a flaky repository never marks a still-existing file GONE
   */
  public CleanupExemptionResult removeExemptionMixin(String nodeUuid) {
    Session session = null;
    try {
      session = getSystemSession();
      Node node = getNodeByIdentifierOrNull(session, nodeUuid);
      if (node == null) {
        return CleanupExemptionResult.NOT_FOUND;
      }
      if (node.isNodeType(NodeTypeConstants.MIX_VERSIONABLE) && !node.isCheckedOut()) {
        // A checked-in versionable node rejects property writes: check it out
        // first. Deliberately NO checkin afterward — leaving the node
        // checked-out is acceptable and avoids creating a spurious version.
        node.checkout();
      }
      if (node.isNodeType(EXO_CLEANUP_EXEMPTION)) {
        // Removing the mixin also drops its exo:cleanupExempted* properties
        node.removeMixin(EXO_CLEANUP_EXEMPTION);
        session.save();
      }
      return CleanupExemptionResult.ADDED;
    } catch (Exception e) {
      // No session.refresh here: the session is logged out in the finally right
      // below, which discards the pending changes anyway
      LOG.warn("Error removing cleanup exemption mixin from node {}", nodeUuid, e);
      return CleanupExemptionResult.FAILED;
    } finally {
      logout(session);
    }
  }

  /**
   * Hard-deletes a file node (no trash): removes pointing symlinks first, then
   * the node, then now-empty ancestor folders up to (excluding) the drive root.
   *
   * @param nodeUuid JCR node identifier
   * @return purge outcome with reclaimed bytes, GONE only when the node really
   *         disappeared, or SKIPPED with a reason (a JCR read failure is
   *         SKIPPED, never GONE). A failure happening AFTER the removing
   *         {@code session.save()} — the empty-ancestor cleanup is the only step
   *         left — leaves the item SKIPPED (an administrator must still see the
   *         file needed attention) but CARRIES the bytes of the file that is
   *         really deleted, exactly as {@link #purgeVersions(String, int)} does
   *         for the versions it already removed: dropping them would
   *         under-report the campaign's reclaimed total
   */
  public CleanupPurgeResult deleteNode(String nodeUuid) {
    Session session = null;
    long reclaimedBytes = 0;
    boolean deleted = false;
    try {
      session = getSystemSession();
      Node node = getNodeByIdentifierOrNull(session, nodeUuid);
      if (node == null) {
        return CleanupPurgeResult.gone();
      }
      reclaimedBytes = getContentSize(node) + JCRDocumentsUtil.computeVersionsSize(node);
      String nodePath = node.getPath();
      // Remove the pointing symlinks first, then the node, in ONE save: a
      // failure of the node removal (e.g. referential integrity) rolls the
      // symlink removals back with it instead of leaving shortcuts deleted
      // while their target survives
      if (!node.isNodeType(NodeTypeConstants.EXO_SYMLINK)) {
        for (Node symlink : getPointingSymlinks(session, nodeUuid)) {
          symlink.remove();
        }
      }
      Node parentNode = node.getParent();
      node.remove();
      session.save();
      // From here on the file IS gone: any later failure must still carry its
      // bytes on the SKIPPED result
      deleted = true;
      removeEmptyAncestors(session, parentNode, nodePath);
      return CleanupPurgeResult.purged(reclaimedBytes);
    } catch (ReferentialIntegrityException e) {
      // The reason stays a BARE message code (the console localizes it, the
      // grouped-failures aggregate groups on it); the exception text goes to the
      // administrator-only detail
      return CleanupPurgeResult.skipped("cleanup.referentialIntegrity",
                                        CleanupThrowableUtil.formatFailureDetail(e),
                                        deleted ? reclaimedBytes : 0);
    } catch (Exception e) {
      // LOG first, persist the compact detail second: if the persist fails, the
      // full stack trace is already in the server log
      LOG.warn("Error hard-deleting node {}", nodeUuid, e);
      return CleanupPurgeResult.skipped("cleanup.deleteError",
                                        CleanupThrowableUtil.formatFailureDetail(e),
                                        deleted ? reclaimedBytes : 0);
    } finally {
      logout(session);
    }
  }

  /**
   * Symlinks pointing at the given node, queried through the SYSTEM session
   * {@link #deleteNode(String)} already holds.
   * <p>
   * Deliberately NOT through {@link org.exoplatform.documents.storage.TrashStorage}:
   * its {@code getAllLinks(Node, String)} overload resolves its session through
   * {@code SessionProviderService.getSessionProvider(null)}, a bare ThreadLocal
   * populated per REQUEST. The purge runs on a worker thread, where it is always
   * null, so that overload systematically returned an EMPTY list and every purge
   * left dangling shortcuts behind pointing at hard-deleted files. Its 3-arg
   * variant taking a SessionProvider isn't on the TrashStorage interface, and
   * the system SessionProvider is a thread-local singleton nothing releases on a
   * worker thread — whereas the session held here is released by the caller's
   * finally, and the returned symlink nodes are attached to it, so the caller's
   * own {@code session.save()} commits their removal.
   * <p>
   * A repository failure PROPAGATES (unlike the TrashStorage overload, which
   * swallows it into an empty list): the caller then reports SKIPPED instead of
   * announcing a purge that silently left the shortcuts in place.
   *
   * @param session held system session, also the session the caller saves
   * @param nodeUuid identifier of the targeted node — the same value the node
   *          was looked up with, so no cast to {@link ExtendedNode} is needed
   */
  private List<Node> getPointingSymlinks(Session session, String nodeUuid) throws RepositoryException {
    String workspaceName = session.getWorkspace().getName();
    String queryString = "SELECT * FROM " + NodeTypeConstants.EXO_SYMLINK + " WHERE " + NodeTypeConstants.EXO_SYMLINK_UUID
        + "='" + nodeUuid + "' AND " + NodeTypeConstants.EXO_WORKSPACE + "='" + workspaceName + "'";
    QueryManager queryManager = session.getWorkspace().getQueryManager();
    NodeIterator symlinkNodes = queryManager.createQuery(queryString, Query.SQL).execute().getNodes();
    List<Node> symlinks = new ArrayList<>();
    while (symlinkNodes.hasNext()) {
      symlinks.add(symlinkNodes.nextNode());
    }
    return symlinks;
  }

  /**
   * Purges oldest versions of a file down to the given maximum, always keeping
   * the current (base) version.
   *
   * @param nodeUuid JCR node identifier
   * @param maxVersionsPerFile number of versions to keep
   * @return purge outcome with reclaimed bytes, GONE only when the node really
   *         disappeared, or SKIPPED with a reason (a JCR read failure is
   *         SKIPPED, never GONE). A version removal failing PARTWAY leaves the
   *         item SKIPPED — an administrator must still see the file needs
   *         attention — but the bytes already reclaimed by the removals that DID
   *         succeed are carried on the result: {@code removeVersion} is
   *         immediate, so those bytes are really gone and dropping them would
   *         under-report the campaign's reclaimed total.
   */
  public CleanupPurgeResult purgeVersions(String nodeUuid, int maxVersionsPerFile) {
    Session session = null;
    long reclaimedBytes = 0;
    try {
      session = getSystemSession();
      Node node = getNodeByIdentifierOrNull(session, nodeUuid);
      if (node == null) {
        return CleanupPurgeResult.gone();
      } else if (!node.isNodeType(NodeTypeConstants.MIX_VERSIONABLE)) {
        return CleanupPurgeResult.skipped("cleanup.notVersionable");
      }
      VersionHistory versionHistory = node.getVersionHistory();
      String baseVersionName = node.getBaseVersion().getName();
      int versionCount = countVersions(versionHistory);
      int toRemove = versionCount - maxVersionsPerFile;
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
    } catch (Exception e) {
      // LOG first, persist the compact detail second: if the persist fails, the
      // full stack trace is already in the server log
      LOG.warn("Error purging versions of node {}", nodeUuid, e);
      // SKIPPED, but carrying whatever was already reclaimed: see the javadoc
      return CleanupPurgeResult.skipped("cleanup.purgeVersionsError",
                                        CleanupThrowableUtil.formatFailureDetail(e),
                                        reclaimedBytes);
    } finally {
      logout(session);
    }
  }

  /**
   * Registers a JCR observation listener forwarding (nodePath, eventType) pairs
   * for changes under the scanned roots. Idempotent. Unlike every other method
   * here, the session obtained is NOT logged out: the listener is bound to it
   * for its whole lifetime, so it is kept in {@link #observationSession} and
   * released only by {@link #unregisterObservationListener()}.
   *
   * @param pathAndEventTypeCallback callback receiving the event node path and
   *          the event type name
   * @return true when the listener is registered (or already was), false when
   *         the registration failed (e.g. JCR not ready yet) and may be retried
   */
  public synchronized boolean registerObservationListener(BiConsumer<String, String> pathAndEventTypeCallback) {
    if (observationListener != null) {
      return true;
    }
    try {
      observationSession = getSystemSession();
      ObservationManager observationManager = observationSession.getWorkspace().getObservationManager();
      observationListener = new CleanupJcrObservationListener(pathAndEventTypeCallback);
      observationManager.addEventListener(observationListener,
                                          Event.PROPERTY_CHANGED | Event.NODE_REMOVED | ExtendedEvent.NODE_MOVED,
                                          "/",
                                          true,
                                          null,
                                          null,
                                          false);
      return true;
    } catch (Exception e) {
      // A failed registration owns no listener: release the session it opened
      // instead of leaking it until the next retry succeeds
      observationListener = null;
      logout(observationSession);
      observationSession = null;
      // Callers decide the log level: startup retries in a backoff loop and
      // warns only after the final failure
      LOG.debug("Error registering cleanup JCR observation listener", e);
      return false;
    }
  }

  /**
   * Unregisters the JCR observation listener, if registered, and logs out the
   * session that owned it. Idempotent.
   */
  public synchronized void unregisterObservationListener() {
    if (observationListener == null) {
      return;
    }
    try {
      if (observationSession != null) {
        observationSession.getWorkspace().getObservationManager().removeEventListener(observationListener);
      }
    } catch (Exception e) {
      LOG.warn("Error unregistering cleanup JCR observation listener", e);
    } finally {
      // Logout only AFTER removeEventListener: the listener lives on this
      // session
      logout(observationSession);
      observationSession = null;
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
    // Candidacy policy defined in the api module, applied here at scan time to
    // avoid shipping every node upward. The exemption mixin doesn't disqualify
    // a node anymore: a still-qualifying exempted file is emitted flagged
    // exempted, so it stays visible as 'Kept' in every campaign
    var action = CleanupCriterionEvaluator.evaluate(createdTime,
                                                    lastModifiedTime,
                                                    fileSize,
                                                    versionsSize,
                                                    versionCount,
                                                    path,
                                                    params,
                                                    System.currentTimeMillis());
    if (action == null) {
      return null;
    }
    Identity ownerIdentity = JCRDocumentsUtil.getOwnerIdentityFromNodePath(path, identityManager, spaceService);
    long ownerIdentityId = ownerIdentity == null ? 0 : Long.parseLong(ownerIdentity.getId());
    CleanupCandidate candidate = new CleanupCandidate(((ExtendedNode) node).getIdentifier(),
                                                      path,
                                                      ownerIdentityId,
                                                      fileSize,
                                                      versionsSize,
                                                      action,
                                                      createdTime,
                                                      lastModifiedTime);
    if (node.isNodeType(EXO_CLEANUP_EXEMPTION)) {
      candidate.setExempted(true);
      candidate.setExemptedBy(getStringProperty(node, EXO_CLEANUP_EXEMPTED_BY));
      candidate.setExemptedDate(getDateProperty(node, EXO_CLEANUP_EXEMPTED_DATE));
    }
    return candidate;
  }

  private String getStringProperty(Node node, String propertyName) {
    try {
      if (node.hasProperty(propertyName)) {
        return node.getProperty(propertyName).getString();
      }
    } catch (RepositoryException e) {
      LOG.debug("Error reading string property {} of node", propertyName, e);
    }
    return null;
  }

  private long getDateProperty(Node node, String propertyName) {
    try {
      if (node.hasProperty(propertyName)) {
        return node.getProperty(propertyName).getDate().getTimeInMillis();
      }
    } catch (RepositoryException e) {
      LOG.debug("Error reading date property {} of node", propertyName, e);
    }
    return 0;
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
      // No session.refresh here: deleteNode logs this session out as soon as it
      // returns, which discards the pending changes anyway
      LOG.debug("Error removing empty ancestors of {}", deletedNodePath, e);
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

  /**
   * Opens a BRAND-NEW system session on the collaboration workspace: the
   * cleanup roots (/Users and /Groups/spaces) live there, so the workspace is
   * named explicitly rather than taken from the repository default workspace,
   * which a deployment is free to configure as something else. Every caller
   * MUST release the returned session (see the class comment).
   */
  private Session getSystemSession() throws RepositoryException, RepositoryConfigurationException {
    ManageableRepository repository = repositoryService.getDefaultRepository();
    ExtendedSession dynamicSession = (ExtendedSession) repository.getSystemSession(COLLABORATION);
    dynamicSession.setTimeout(jcrSessionTimeout);
    return dynamicSession;
  }

  /**
   * Releases a system session obtained from {@link #getSystemSession()}. A
   * logout failure is only logged at debug level: it must never mask the
   * outcome — nor the exception — of the cleanup operation that just ran.
   */
  private void logout(Session session) {
    try {
      if (session != null) {
        session.logout();
      }
    } catch (Exception e) { // NOSONAR
      LOG.debug("Error logging out the cleanup JCR system session", e);
    }
  }

}
