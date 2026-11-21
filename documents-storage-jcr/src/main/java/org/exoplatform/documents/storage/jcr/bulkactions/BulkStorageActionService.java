/*
 * Copyright (C) 2003-2023 eXo Platform SAS.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see<http://www.gnu.org/licenses/>.
 */
package org.exoplatform.documents.storage.jcr.bulkactions;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.jcr.Node;
import javax.jcr.Session;

import org.picocontainer.Startable;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.documents.model.AbstractNode;
import org.exoplatform.documents.model.ActionData;
import org.exoplatform.documents.model.ActionStatus;
import org.exoplatform.documents.model.ActionType;
import org.exoplatform.documents.storage.DocumentFileStorage;
import org.exoplatform.documents.storage.JCRDeleteFileStorage;
import org.exoplatform.documents.storage.jcr.util.JCRDocumentsUtil;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.security.Identity;
import org.exoplatform.upload.UploadService;

public class BulkStorageActionService implements Startable {

  private static final Log              LOG        = ExoLogger.getLogger(BulkStorageActionService.class.getName());

  private ExecutorService               bulkActionThreadPool;

  private static final String           TEMP_DIRECTORY_PATH = "java.io.tmpdir";

  private static final String           ZIP_PREFIX          = "downloadzip";

  public static final String            TEMP_DOWNLOAD_FOLDER_PREFIX = "temp_download";

  public static final String            TEMP_IMPORT_FOLDER_PREFIX   = "temp_import";

  private long                          DEFAULT_ZIP_UPLOAD_LIMIT    = 3000;

  // test hook, run between the removal of the earlier entries and the add of the new one
  Runnable                              afterEarlierEntriesRemoved  = () -> {
                                                                    };

  private static final List<ActionData> actionList = new CopyOnWriteArrayList<>();

  public void executeBulkAction(Session session,
                                DocumentFileStorage documentFileStorage,
                                JCRDeleteFileStorage jcrDeleteFileStorage,
                                ListenerService listenerService,
                                UploadService uploadService,
                                List<AbstractNode> items,
                                ActionData actionData,
                                Node parent,
                                Map<String,Object> params,
                                long authenticatedUserId) {
    actionData.setStatus(ActionStatus.STARTED.name());
    // an action identifier is unique per user in the list: the older entries are replaced
    // (the threads of those keep working on their own ActionData), but a running import,
    // whose size counts in the uploads limit, stays. The list is a copy-on-write one, read
    // on snapshots by every thread, and the replacement is made one start at a time.
    synchronized (actionList) {
      actionList.removeIf(older -> !ActionType.IMPORT_ZIP.name().equals(older.getActionType())
          && older.getActionId() != null && older.getActionId().equals(actionData.getActionId())
          && older.getIdentity() != null && actionData.getIdentity() != null
          && older.getIdentity().getUserId().equals(actionData.getIdentity().getUserId()));
      afterEarlierEntriesRemoved.run();
      actionList.add(actionData);
    }
    checkTotalUplaodsLimit(actionData.getIdentity(), true);
    bulkActionThreadPool.execute(new ActionThread(documentFileStorage,
                                                  jcrDeleteFileStorage,
                                                  this,
                                                  listenerService,
                                                  uploadService,
                                                  actionData,
                                                  parent,
                                                  params,
                                                  session,
                                                  items,
                                                  authenticatedUserId));
  }

  @Override
  public void start() {
    bulkActionThreadPool = Executors.newCachedThreadPool(new ThreadFactoryBuilder().setNameFormat("documents-bulk-action-%d")
                                                                                   .build());
  }

  @Override
  public void stop() {
    if (bulkActionThreadPool != null) {
      bulkActionThreadPool.shutdownNow();
    }
    File temp = new File(System.getProperty(TEMP_DIRECTORY_PATH));
    if (cleanTempFiles(temp)) {
      LOG.info("All temp files were deleted");
    }
  }

  private boolean cleanTempFiles(File file) {
    File[] files = file.listFiles();
    if (files != null) {
      for (File f : files) {
        cleanTempFiles(f);
      }
    }
    if (file.getName().startsWith(TEMP_DOWNLOAD_FOLDER_PREFIX) || file.getName().startsWith(TEMP_IMPORT_FOLDER_PREFIX)
        || file.getName().startsWith(ZIP_PREFIX)) {
      JCRDocumentsUtil.cleanFiles(file);
    }
    return true;
  }

  /**
   * @param id the action's identifier
   * @param userId the user who started the action
   * @return the most recent action with this identifier started by this user, or null
   */
  public ActionData getActionData(String id, String userId) {
    return actionList.stream()
                     .filter(resource -> id.equals(resource.getActionId()) && resource.getIdentity() != null
                         && resource.getIdentity().getUserId().equals(userId))
                     .reduce((older, newer) -> newer)
                     .orElse(null);
  }

  public void removeActionData(ActionData actionData) {
    // by instance: ActionData compares by value and an equal entry of a newer action must stay
    actionList.removeIf(entry -> entry == actionData);
    checkTotalUplaodsLimit(actionData.getIdentity(), true);
  }

  public boolean checkTotalUplaodsLimit(Identity identity, boolean broadcast) {
    boolean canImport = true;
    ActionData actionData = new ActionData();
    actionData.setIdentity(identity);
    actionData.setActionType(ActionType.IMPORT_ZIP.name());
    if (actionList.isEmpty()) {
      actionData.setStatus(ActionStatus.IMPORT_LIMIT_NOT_EXCEEDED.name());
    } else {
      long totalUploadsLimit;
      try {
        totalUploadsLimit = Long.parseLong(System.getProperty("exo.import.zip.uploads.limit",
                                                              String.valueOf(DEFAULT_ZIP_UPLOAD_LIMIT)));
      } catch (Exception e) {
        totalUploadsLimit = DEFAULT_ZIP_UPLOAD_LIMIT;
      }
      double total = actionList.stream().mapToDouble(ActionData::getSize).sum();
      if (total > totalUploadsLimit * 1024 * 1024) {
        actionData.setStatus(ActionStatus.IMPORT_LIMIT_EXCEEDED.name());
        canImport = false;
      } else {
        actionData.setStatus(ActionStatus.IMPORT_LIMIT_NOT_EXCEEDED.name());
      }
    }
    if (broadcast) {
      try {
        ListenerService listenerService = CommonsUtils.getService(ListenerService.class);
        listenerService.broadcast("bulk_actions_document_event", actionData.getIdentity(), actionData);
      } catch (Exception e) {
        LOG.error("cannot broadcast bulk action event");
      }
    }
    return canImport;
  }

}
