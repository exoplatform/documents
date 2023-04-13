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

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.jcr.*;

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.documents.model.AbstractNode;
import org.exoplatform.documents.model.ActionData;
import org.exoplatform.documents.model.ActionStatus;
import org.exoplatform.documents.model.ActionType;
import org.exoplatform.documents.storage.DocumentFileStorage;
import org.exoplatform.documents.storage.JCRDeleteFileStorage;
import org.exoplatform.documents.storage.jcr.util.JCRDocumentsUtil;
import org.exoplatform.documents.storage.jcr.util.NodeTypeConstants;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.security.Identity;

public class ActionThread implements Runnable {

  private static final Log               log                 = ExoLogger.getLogger(ActionThread.class);

  private static final String            ZIP_EXTENSION       = ".zip";

  private static final String            ZIP_PREFIX          = "downloadzip";

  private static final String            TEMP_FOLDER_PREFIX  = "temp_download";

  private static final String            TEMP_DIRECTORY_PATH = "java.io.tmpdir";

  private final List<AbstractNode>       items;

  private final JCRDeleteFileStorage     jCrDeleteFileStorage;

  private final DocumentFileStorage      documentFileStorage;

  private final BulkStorageActionService bulkStorageActionService;

  private final ListenerService          listenerService;

  private final Identity                 identity;

  private final Long                     identityId;

  private final Session                  session;

  private ActionData                     actionData;

  private String                         parentPath;

  private String                         tempFolderPath;

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
    List<String> treatedItemsIds = new ArrayList<>();
    for (AbstractNode item : items) {
      if (checkCanceled()) {
        break;
      }
      try {
        jCrDeleteFileStorage.deleteDocument(session, item.getPath(), item.getId(), true, true, 0, identity, identityId);
        actionData.setStatus(ActionStatus.IN_PROGRESS.name());
        treatedItemsIds.add(item.getId());
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
    actionData.setTreatedItemsIds(treatedItemsIds);
    try {
      listenerService.broadcast("bulk_actions_document_event", identity, actionData);
    } catch (Exception e) {
      log.error("cannot broadcast bulk action event");
    }
  }

  private void downloadItems() {
    List<javax.jcr.Node> nodes = items.stream()
                                      .map(document -> JCRDocumentsUtil.getNodeByIdentifier(session, document.getId()))
                                      .toList();

    try {
      tempFolderPath = Files.createTempDirectory(TEMP_FOLDER_PREFIX).toString();
    } catch (IOException e) {
      log.error("Cannot create temp folder to download documents", e);
      return;
    }
    boolean hasFolders = items.stream().anyMatch(obj -> obj.isFolder());
    try {
      actionData.setStatus(ActionStatus.ZIP_FILE_CREATION.name());
      listenerService.broadcast("bulk_actions_document_event", identity, actionData);
    } catch (Exception e) {
      log.error("cannot broadcast bulk action event");
    }
    try {
      for (Node node : nodes) {
        if (checkCanceled()) {
          File folder = new File(tempFolderPath);
          cleanFiles(folder);
          break;
        }
        if (StringUtils.isEmpty(parentPath)) {
          parentPath = node.getParent().getPath();
        }
        if (hasFolders) {
          createTempFilesAndFolders(node, "", "");
        } else {
          createFile(node, "", "", hasFolders);
        }

      }
    } catch (Exception e) {
      log.error("Error when creating temp files for download", e);
      actionData.setStatus(ActionStatus.FAILED.name());
    }

    String zipName = ZIP_PREFIX + actionData.getActionId() + ZIP_EXTENSION;
    String zipPath = System.getProperty(TEMP_DIRECTORY_PATH) + File.separator + zipName;
    try {
      zipFiles(zipPath);
      File zipped = new File(zipPath);
      actionData.setDownloadZipPath(zipped.getPath());
      File folder = new File(tempFolderPath);
      cleanFiles(folder);
    } catch (Exception e) {
      log.error("Error when creating zip file", e);
      actionData.setStatus(ActionStatus.FAILED.name());
    }
    if (checkCanceled()) {
      File zip = new File(zipPath);
      zip.delete();
      return;
    }
    if (!actionData.getStatus().equals(ActionStatus.FAILED.name())) {
      actionData.setStatus(ActionStatus.DONE_SUCCSUSSFULLY.name());
    }
    try {
      listenerService.broadcast("bulk_actions_document_event", identity, actionData);
    } catch (Exception e) {
      log.error("cannot broadcast bulk action event");
    }
  }

  private void duplicateItems() {
    // TODO
  }

  private void moveItems() {
    // TODO
  }

  private File createFile(Node node, String symlinkPath, String sourcePath, boolean hasFolders) throws RepositoryException,
                                                                                                IOException {
    if (checkCanceled()) {
      return null;
    }
    Node jrcNode = node.getNode("jcr:content");
    InputStream inputStream = jrcNode.getProperty("jcr:data").getStream();
    String path = "";
    if (hasFolders) {
      String nodePath = node.getPath();
      if (StringUtils.isNotEmpty(symlinkPath) || StringUtils.isNotEmpty(sourcePath)) {
        nodePath = symlinkPath + nodePath.replace(sourcePath, "");
      }
      path = tempFolderPath + nodePath.replace(parentPath, "");
    } else {
      path = tempFolderPath + File.separator + node.getName();
    }
    File file = new File(path);
    try (OutputStream outputStream = new FileOutputStream(file)) {
      byte[] buffer = new byte[1024];
      int length;
      while ((length = inputStream.read(buffer)) > 0) {
        outputStream.write(buffer, 0, length);
      }
      inputStream.close();
    }
    return file;
  }

  private void createTempFilesAndFolders(Node node, String symlinkPath, String sourcePath) throws Exception {
    if (checkCanceled()) {
      return;
    }
    if (JCRDocumentsUtil.isFolder(node)) {
      String nodePath = node.getPath();
      if (StringUtils.isNotEmpty(symlinkPath) || StringUtils.isNotEmpty(sourcePath)) {
        nodePath = symlinkPath + nodePath.replace(sourcePath, "");
      }
      String path = tempFolderPath + nodePath.replace(parentPath, "");
      Files.createDirectories(Paths.get(path));
      NodeIterator nodeIterator = node.getNodes();
      while (nodeIterator.hasNext()) {
        Node child = nodeIterator.nextNode();
        createTempFilesAndFolders(child, symlinkPath, sourcePath);
      }
    } else {
      if (node.isNodeType(NodeTypeConstants.EXO_SYMLINK)) {
        String sourceID = node.getProperty(NodeTypeConstants.EXO_SYMLINK_UUID).getString();
        Node sourceNode = JCRDocumentsUtil.getNodeByIdentifier(session, sourceID);
        createTempFilesAndFolders(sourceNode, node.getPath(), sourceNode.getPath());
      } else {
        createFile(node, symlinkPath, sourcePath, true);
      }
    }

  }

  private void zipFiles(String zipFilePath) throws Exception {
    try (FileOutputStream fos = new FileOutputStream(zipFilePath)) {
      try (ZipOutputStream zos = new ZipOutputStream(fos)) {
        File folder = new File(tempFolderPath);
        zipFolder(folder, "", zos, fos, zipFilePath);
      }
    }
  }

  private void zipFolder(File folder,
                         String folderName,
                         ZipOutputStream zos,
                         FileOutputStream fos,
                         String zipFilePath) throws Exception {
    File[] files = folder.listFiles();
    for (File file : files) {
      if (checkCanceled()) {
        zos.close();
        fos.close();
        File zip = new File(zipFilePath);
        zip.delete();
        return;
      }
      if (file.isDirectory()) {
        if (StringUtils.isNotEmpty(folderName)) {
          zipFolder(file, folderName + "/" + file.getName(), zos, fos, zipFilePath);
        } else {
          zipFolder(file, file.getName(), zos, fos, zipFilePath);
        }
      } else {
        byte[] buffer = new byte[1024];
        try (FileInputStream fis = new FileInputStream(file)) {
          if (StringUtils.isNotEmpty(folderName)) {
            zos.putNextEntry(new ZipEntry(folderName + "/" + file.getName()));
          } else {
            zos.putNextEntry(new ZipEntry(file.getName()));
          }
          int length;
          while ((length = fis.read(buffer)) > 0) {
            zos.write(buffer, 0, length);
          }
          zos.closeEntry();
        }
      }
    }
  }

  private void cleanFiles(File file) {
    File[] files = file.listFiles();
    if (files != null) {
      for (File f : files) {
        cleanFiles(f);
      }
    }
    file.delete();
  }

  private boolean checkCanceled() {
    actionData = bulkStorageActionService.getActionDataById(actionData.getActionId());
    if (actionData.getStatus().equals(ActionStatus.CANCELED.name())) {
      try {
        listenerService.broadcast("bulk_actions_document_event", identity, actionData);
      } catch (Exception e) {
        log.error("cannot broadcast bulk action event");
      }
      return true;
    }
    return false;
  }

}
