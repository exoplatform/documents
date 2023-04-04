/*
 * Copyright (C) 2023 eXo Platform SAS.
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

package org.exoplatform.documents.storage.jcr.bulkactions;


import java.util.List;

import javax.jcr.PathNotFoundException;
import javax.jcr.Session;

import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.documents.model.AbstractNode;
import org.exoplatform.documents.model.ActionData;
import org.exoplatform.documents.model.ActionStatus;
import org.exoplatform.documents.model.ActionType;
import org.exoplatform.documents.storage.DocumentFileStorage;
import org.exoplatform.documents.storage.JCRDeleteFileStorage;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.security.Identity;

public class ActionThread implements Runnable {

  private static final Log               log = ExoLogger.getLogger(ActionThread.class);

  private final List<AbstractNode>       items;

  private final JCRDeleteFileStorage     jCrDeleteFileStorage;

  private final DocumentFileStorage      documentFileStorage;

  private final BulkStorageActionService bulkStorageActionService;

  private final ListenerService          listenerService;

  private final Identity                 identity;

  private final Long                     identityId;

  private final Session                  session;

  private ActionData                     actionData;

  public ActionThread(DocumentFileStorage documentFileStorage,
                      JCRDeleteFileStorage jCrDeleteFileStorage,
                      BulkStorageActionService bulkStorageActionService,
                      ListenerService listenerService,
                      ActionData actionData,
                      Session session,
                      List<AbstractNode> items,
                      Identity identity,
                      Long identityId) {
    this.jCrDeleteFileStorage = jCrDeleteFileStorage;
    this.documentFileStorage = documentFileStorage;
    this.bulkStorageActionService = bulkStorageActionService;
    this.listenerService = listenerService;
    this.actionData = actionData;
    this.items = items;
    this.session = session;
    this.identity = identity;
    this.identityId = identityId;
  }

  @Override
  public void run() {
    try {
      RequestLifeCycle.begin(PortalContainer.getInstance());
      processAction();
    } catch (Exception e) {
      log.error("Cannot execute Action {} operation", actionData.getActionType(), e);
    } finally {
      RequestLifeCycle.end();
    }
  }

  public void processAction() {
    actionData = bulkStorageActionService.getActionDataById(actionData.getActionId());
    if (actionData.getActionType().equals(ActionType.DELETE.name())) {
      actionData.setStatus(ActionStatus.IN_PROGRESS.name());
      deleteItems();
    }
    if (actionData.getActionType().equals(ActionType.DOWNLOAD.name())) {
      downloadItems();
    }
  }

  private void deleteItems() {
    int errors = 0;
    for (AbstractNode item : items) {
      actionData = bulkStorageActionService.getActionDataById(actionData.getActionId());
      if (actionData.getStatus().equals(ActionStatus.CANCELLED.name())) {
        break;
      }
      try {
        jCrDeleteFileStorage.deleteDocument(session, item.getPath(), item.getId(), true, true, 0, identity, identityId);
        actionData.setStatus(ActionStatus.IN_PROGRESS.name());
      } catch (PathNotFoundException path) {
        log.error("The document with this path is not found" + item.getPath(), path);
        errors++;
      } catch (Exception e) {
        log.error("Error when deleting the document" + item.getPath(), e);
        errors++;
      }
    }
    if (errors > 0) {
      actionData.setStatus(ActionStatus.DONE_WITH_ERRORS.name());
    } else {
      actionData.setStatus(ActionStatus.DONE_SUCCSUSSFULLY.name());
    }

    try {
      listenerService.broadcast("bulk_actions_document_event", identity, actionData);
    } catch (Exception e) {
      log.error("cannot broadcast delete_files_event");
    }
  }

  private void downloadItems() {
    // TODO
  }

  private void duplicateItems() {
    // TODO
  }

  private void moveItems() {
    // TODO
  }

}
