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
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.jcr.Node;
import javax.jcr.Session;

import org.exoplatform.documents.storage.jcr.util.JCRDocumentsUtil;
import org.exoplatform.upload.UploadService;
import org.picocontainer.Startable;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import org.exoplatform.documents.model.AbstractNode;
import org.exoplatform.documents.model.ActionData;
import org.exoplatform.documents.model.ActionStatus;
import org.exoplatform.documents.storage.DocumentFileStorage;
import org.exoplatform.documents.storage.JCRDeleteFileStorage;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

public class BulkStorageActionService implements Startable {

  private static final Log              LOG        = ExoLogger.getLogger(BulkStorageActionService.class.getName());

  private ExecutorService               bulkActionThreadPool;

  private static final String           TEMP_DIRECTORY_PATH = "java.io.tmpdir";

  private static final String           ZIP_PREFIX          = "downloadzip";

  public static final String           TEMP_DOWNLOAD_FOLDER_PREFIX  = "temp_download";

  public static final String           TEMP_IMPORT_FOLDER_PREFIX  = "temp_import";

  private static final List<ActionData> actionList = new ArrayList<>();

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
    actionList.add(actionData);
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
    if(cleanTempFiles(temp)){
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
    if (file.getName().startsWith(TEMP_DOWNLOAD_FOLDER_PREFIX) || file.getName().startsWith(TEMP_IMPORT_FOLDER_PREFIX) || file.getName().startsWith(ZIP_PREFIX)) {
      JCRDocumentsUtil.cleanFiles(file);
    }
    return true;
  }
  public ActionData getActionDataById(String id) {
    return actionList.stream().filter(resource -> id == resource.getActionId()).findFirst().orElse(null);
  }

  public void removeActionData(ActionData actionData) {
    actionList.remove(actionData);
  }

}
