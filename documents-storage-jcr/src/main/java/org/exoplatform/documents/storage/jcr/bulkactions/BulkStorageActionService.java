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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.jcr.Session;

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
import org.exoplatform.services.security.Identity;

public class BulkStorageActionService implements Startable {

  private static final Log              LOG        = ExoLogger.getLogger(BulkStorageActionService.class.getName());

  private ExecutorService               bulkActionThreadPool;

  private static final String           TEMP_DIRECTORY_PATH = "java.io.tmpdir";

  private static final String           ZIP_PREFIX          = "downloadzip";

  private static final String           TEMP_FOLDER_PREFIX  = "temp_download";

  private static final List<ActionData> actionList = new ArrayList<>();

  public void executeBulkAction(Session session,
                                int actionId,
                                DocumentFileStorage documentFileStorage,
                                JCRDeleteFileStorage jcrDeleteFileStorage,
                                ListenerService listenerService,
                                List<AbstractNode> items,
                                String actionType,
                                Identity identity,
                                long authenticatedUserId) {
    ActionData actionData = new ActionData();
    actionData.setActionId(actionId);
    actionData.setStatus(ActionStatus.STARTED.name());
    actionData.setActionType(actionType);
    actionData.setNumberOfItems(items.size());
    actionData.setIdentity(identity);
    actionList.add(actionData);
    bulkActionThreadPool.execute(new ActionThread(documentFileStorage,
                                                  jcrDeleteFileStorage,
                                                  this,
                                                  listenerService,
                                                  actionData,
                                                  session,
                                                  items,
                                                  identity,
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
    cleanTempFiles(temp);
  }

  private void cleanTempFiles(File file) {
    File[] files = file.listFiles();
    if (files != null) {
      for (File f : files) {
        cleanTempFiles(f);
      }
    }
    if (file.getName().startsWith(TEMP_FOLDER_PREFIX) || file.getName().startsWith(ZIP_PREFIX)) {
      file.delete();
    }
  }
  public ActionData getActionDataById(int id) {
    return actionList.stream().filter(resource -> id == resource.getActionId()).findFirst().orElse(null);
  }

  public void removeActionData(ActionData actionData) {
    actionList.remove(actionData);
  }

}
