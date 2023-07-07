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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

import javax.jcr.Node;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

import org.exoplatform.commons.utils.MimeTypeResolver;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.documents.model.AbstractNode;
import org.exoplatform.documents.model.ActionData;
import org.exoplatform.documents.model.ActionStatus;
import org.exoplatform.documents.model.ActionType;
import org.exoplatform.documents.storage.DocumentFileStorage;
import org.exoplatform.documents.storage.JCRDeleteFileStorage;
import org.exoplatform.documents.storage.jcr.util.JCRDocumentsUtil;
import org.exoplatform.services.jcr.util.Text;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.upload.UploadService;

import net.lingala.zip4j.ZipFile;

public class ActionThread implements Runnable {

  private static final Log               log                 = ExoLogger.getLogger(ActionThread.class);

  private static final String            ZIP_EXTENSION       = ".zip";

  private static final String            ZIP_PREFIX          = "downloadzip";

  private static final String            TEMP_DIRECTORY_PATH = "java.io.tmpdir";

  public static final String            NT_FILE                = "nt:file";

  public static final String            NT_FOLDER              = "nt:folder";

  public static final String            JCR_CONTENT            = "jcr:content";

  public static final String            JCR_DATA               = "jcr:data";

  public static final String            MIX_REFERENCEABLE      = "mix:referenceable";

  public static final String            EXO_TITLE              = "exo:title";

  public static final String            JCR_LAST_MODIFIED      = "jcr:lastModified";

  public static final String            JCR_ENCODING           = "jcr:encoding";

  public static final String            JCR_MIME_TYPE          = "jcr:mimeType";

  public static final String            NT_RESOURCE            = "nt:resource";

  public static final String            EXO_RSS_ENABLE         = "exo:rss-enable";

  public static final String            EXO_NAME               = "exo:name";

  public static final String            EXO_DATE_CREATED       = "exo:dateCreated";

  public static final String            EXO_DATE_MODIFIED      = "exo:dateModified";

  public static final String            EXO_LAST_MODIFIED_DATE = "exo:lastModifiedDate";

  public static final String            EXO_LAST_MODIFIER      = "exo:lastModifier";

  public static final String            EXO_MODIFY             = "exo:modify";

  public static final String            EXO_SORTABLE           = "exo:sortable";

  public static final String            MIX_VERSIONABLE        = "mix:versionable";

  private static final MimeTypeResolver mimeTypes              = new MimeTypeResolver();

  private long                          startTime              = 0;


  private final List<AbstractNode>       items;

  private final JCRDeleteFileStorage     jCrDeleteFileStorage;

  private final DocumentFileStorage      documentFileStorage;

  private final BulkStorageActionService bulkStorageActionService;

  private final ListenerService          listenerService;
  
  private final UploadService          uploadService;

  private final Long                     identityId;

  private final Session                  session;

  private ActionData                     actionData;

  private String                         parentPath;

  private Map<String, Object>            params;

  private final Node                    parent;


  public ActionThread(DocumentFileStorage documentFileStorage,
                      JCRDeleteFileStorage jCrDeleteFileStorage,
                      BulkStorageActionService bulkStorageActionService,
                      ListenerService listenerService,
                      UploadService uploadService,
                      ActionData actionData,
                      Node parent,
                      Map<String, Object> params,
                      Session session,
                      List<AbstractNode> items,
                      Long identityId) {
    this.jCrDeleteFileStorage = jCrDeleteFileStorage;
    this.documentFileStorage = documentFileStorage;
    this.bulkStorageActionService = bulkStorageActionService;
    this.uploadService = uploadService;
    this.listenerService = listenerService;
    this.actionData = actionData;
    this.params = params;
    this.items = items;
    this.session = session;
    this.identityId = identityId;
    this.parent = parent;
  }

  @Override
  public void run() {
    try {
      RequestLifeCycle.begin(PortalContainer.getInstance());
      processAction();
    } catch (Exception e) {
      log.error("Cannot execute Action {} operation", actionData.getActionType(), e);
      actionData.setStatus(ActionStatus.FAILED.name());
      brodcastEvent();
    } finally {
      RequestLifeCycle.end();
    }
  }

  public void processAction() throws RepositoryException {
    actionData = bulkStorageActionService.getActionDataById(actionData.getActionId());
    if (actionData.getActionType().equals(ActionType.DELETE.name())) {
      actionData.setStatus(ActionStatus.IN_PROGRESS.name());
      deleteItems();
    }
    if (actionData.getActionType().equals(ActionType.DOWNLOAD.name())) {
      downloadItems();
    }
    if (actionData.getActionType().equals(ActionType.MOVE.name())) {
      actionData.setStatus(ActionStatus.IN_PROGRESS.name());
      moveItems();
    }
    if (actionData.getActionType().equals(ActionType.IMPORT_ZIP.name())) {
      importFromZip();
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
        jCrDeleteFileStorage.deleteDocument(session, item.getPath(), item.getId(), true, true, 0, actionData.getIdentity(), identityId);
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
      actionData.setStatus(ActionStatus.DONE_SUCCESSFULLY.name());
    }
    actionData.setTreatedItemsIds(treatedItemsIds);
    brodcastEvent();
  }

  private void downloadItems() {
    List<javax.jcr.Node> nodes = items.stream()
                                      .map(document -> JCRDocumentsUtil.getNodeByIdentifier(session, document.getId()))
                                      .toList();

    try {
      String tempFolderPath = Files.createTempDirectory(BulkStorageActionService.TEMP_DOWNLOAD_FOLDER_PREFIX).toString();

    boolean hasFolders = items.stream().anyMatch(AbstractNode::isFolder);
    brodcastEvent();
    try {
      for (Node node : nodes) {
        if (checkCanceled()) {
          File folder = new File(tempFolderPath);
          JCRDocumentsUtil.cleanFiles(folder);
          break;
        }
        if (StringUtils.isEmpty(parentPath)) {
          parentPath = node.getParent().getPath();
        }
        if (hasFolders) {
          JCRDocumentsUtil.createTempFilesAndFolders(node, "", "", tempFolderPath, parentPath);
        } else {
          JCRDocumentsUtil.createFile(node, "", "", tempFolderPath, parentPath);
        }

      }
    } catch (Exception e) {
      log.error("Error when creating temp files for download", e);
      actionData.setStatus(ActionStatus.FAILED.name());
    }

    String zipName = ZIP_PREFIX + actionData.getActionId() + ZIP_EXTENSION;
    String zipPath = System.getProperty(TEMP_DIRECTORY_PATH) + File.separator + zipName;
    try {
      JCRDocumentsUtil.zipFiles(zipPath, tempFolderPath);
      File zipped = new File(zipPath);
      actionData.setDownloadZipPath(zipped.getPath());
      File folder = new File(tempFolderPath);
      JCRDocumentsUtil.cleanFiles(folder);
    } catch (Exception e) {
      log.error("Error when creating zip file", e);
      actionData.setStatus(ActionStatus.FAILED.name());
    }
    if (checkCanceled()) {
      File zip = new File(zipPath);
      deleteFile(zip);
      return;
    }
    if (!actionData.getStatus().equals(ActionStatus.FAILED.name())) {
      actionData.setStatus(ActionStatus.DONE_SUCCESSFULLY.name());
    }
    brodcastEvent();
  } catch (IOException e) {
    log.error("Cannot create temp folder to download documents", e);
  }
  }

  private void duplicateItems() {
    // TODO
  }

  private void moveItems() {
    int errors = 0;
    List<String> treatedItemsIds = new ArrayList<>();
    for (AbstractNode item : items) {
      if (checkCanceled()) {
        break;
      }
      try {
        actionData.setStatus(ActionStatus.IN_PROGRESS.name());
        documentFileStorage.moveDocument(session,
                                         (Long) params.get("ownerId"),
                                         item.getId(),
                                         (String) params.get("destPath"),
                                         actionData.getIdentity(),
                                         "keepBoth");
        treatedItemsIds.add(item.getId());
      } catch (Exception e) {
        log.error("Error while moving document {} to path {}", item.getName(), params.get("destPath"), e);
        errors++;
      }
    }
    actionData.setTreatedItemsIds(treatedItemsIds);
    if (errors > 0) {
      actionData.setStatus(ActionStatus.DONE_WITH_ERRORS.name());
    } else {
      actionData.setStatus(ActionStatus.DONE_SUCCESSFULLY.name());
    }
    brodcastEvent();
  }
  
  private boolean checkCanceled() {
    actionData = bulkStorageActionService.getActionDataById(actionData.getActionId());
    if (actionData.getStatus().equals(ActionStatus.CANCELED.name())) {
      brodcastEvent();
      return true;
    }
    return false;
  }
  
  private void deleteFile(File file) {
    try {
      Files.delete(file.toPath());
    } catch (IOException e) {
      log.error("Error while deleting file", e);
    }
  }

  public void importFromZip() throws RepositoryException {
    try (ZipFile zip = new ZipFile(uploadService.getUploadResource(actionData.getActionId()).getStoreLocation())) {
      startTime = System.currentTimeMillis();
      actionData.setStatus(ActionStatus.UNZIPPING.name());
      brodcastEvent();
      zip.extractAll(actionData.getTempFolderPath());
      List<String> files = new ArrayList<>();
      listFiles(new File(actionData.getTempFolderPath()), files);
      actionData.setFiles(files);
      uploadService.removeUploadResource(actionData.getActionId());
      actionData.setStatus(ActionStatus.CREATING_DOCUMENTS.name());
      brodcastEvent();
      createItems();
    } catch (IOException e) {
      actionData.setStatus(ActionStatus.CANNOT_UNZIP_FILE.name());
      log.error("Cannot unzip the zip file", e);
      brodcastEvent();
    } finally {
      bulkStorageActionService.removeActionData(actionData);
      JCRDocumentsUtil.cleanFiles(new File(actionData.getTempFolderPath()));
    }
  }

  private void listFiles(File dir, List<String> files) {
    File[] dirFiles = dir.listFiles();
    if (dirFiles != null && dirFiles.length > 0) {
      for (File file : dirFiles) {
        if (file.isDirectory()) {
          listFiles(file, files);
        } else {
          files.add(file.getAbsolutePath());
        }
      }
    }
  }

  public void createItems() throws RepositoryException {
    String tempFolderPath = actionData.getTempFolderPath();
    Map<String, String> folderReplaced = new HashMap<>();
    Map<String, String> folderCreated = new HashMap<>();
    for (String filePath : actionData.getFiles()) {
      try {
        boolean ignored = false;
        File file = new File(filePath);
        filePath = filePath.replace("\\", "/");
        actionData.setDocumentInProgress(filePath.replace(tempFolderPath, ""));
        brodcastEvent();
        tempFolderPath = tempFolderPath.replace("\\", "/");
        String folderPath = filePath.substring(0, filePath.lastIndexOf("/"));
        folderPath = folderPath.replace(tempFolderPath, "");
        Node folderNode = parent;
        if (StringUtils.isNotEmpty(folderPath)) {
          for (String folderName : folderPath.split("/")) {
            if (StringUtils.isNotEmpty(folderName)) {
              String name = Text.escapeIllegalJcrChars(JCRDocumentsUtil.cleanName(folderName));
              name = URLDecoder.decode(name, StandardCharsets.UTF_8);
              if (folderNode.hasNode(name)) {
                String existingFolderId = folderNode.getNode(name).getUUID();
                if (actionData.getConflict().equals("duplicate")) {
                  if (folderCreated.containsKey(existingFolderId)) {
                    folderNode = folderNode.getNode(folderCreated.get(existingFolderId));
                  } else if (folderReplaced.containsKey(existingFolderId)) {
                    folderNode = folderNode.getNode(folderReplaced.get(existingFolderId));
                  } else {
                    int i = 1;
                    String newName = name + " (" + i + ")";
                    String newTitle = folderName + " (" + i + ")";
                    while (folderNode.hasNode(newName)) {
                      i++;
                      newName = name + " (" + i + ")";
                      newTitle = folderName + " (" + i + ")";
                    }
                    folderReplaced.put(existingFolderId, newName);
                    Node addedNode = folderNode.addNode(newName, NT_FOLDER);
                    addedNode.setProperty(EXO_TITLE, newTitle);
                    if (addedNode.canAddMixin(MIX_REFERENCEABLE)) {
                      addedNode.addMixin(MIX_REFERENCEABLE);
                    }
                    folderNode.save();
                    folderNode = folderNode.getNode(newName);
                    folderCreated.put(folderNode.getUUID(), newName);
                  }
                } else {
                  if (folderCreated.containsKey(existingFolderId)) {
                    folderNode = folderNode.getNode(folderCreated.get(existingFolderId));
                  } else {
                    actionData.addIgnoredFile(filePath.replace(tempFolderPath, ""));
                    ignored = true;
                  }
                }
              } else {
                Node addedNode = folderNode.addNode(name, NT_FOLDER);
                addedNode.setProperty(EXO_TITLE, folderName);
                if (addedNode.canAddMixin(MIX_REFERENCEABLE)) {
                  addedNode.addMixin(MIX_REFERENCEABLE);
                }
                folderNode.save();
                folderNode = folderNode.getNode(name);
                folderCreated.put(folderNode.getUUID(), name);
              }
            }
          }
        }
        if (ignored) {
          actionData.incrementImportCount();
          brodcastEvent();
          continue;
        }
        String title = file.getName();
        String name = Text.escapeIllegalJcrChars(JCRDocumentsUtil.cleanName(title.toLowerCase()));
        name = URLDecoder.decode(name, StandardCharsets.UTF_8);
        if (!folderNode.hasNode(name)) {
          createFile(folderNode, file, name, title);
          actionData.addCreatedFile(filePath.replace(tempFolderPath, ""));
        } else {
          handleImportConflict(file, folderNode, name, title, filePath);
        }
      } catch (Exception e) {
        log.error("Cannot create file {}", filePath.replace(tempFolderPath, ""), e);
        actionData.addFailedFile(filePath.replace(tempFolderPath, ""));
      }
      actionData.incrementImportCount();
      brodcastEvent();
    }
    session.save();
    if (actionData.getFailedFiles().size() == actionData.getImportedFilesCount()) {
      actionData.setStatus(ActionStatus.FAILED.name());
    } else if (!actionData.getFailedFiles().isEmpty()) {
      actionData.setStatus(ActionStatus.DONE_WITH_ERRORS.name());
    } else {
      actionData.setStatus(ActionStatus.DONE_SUCCESSFULLY.name());
    }
    actionData.setDuration(System.currentTimeMillis() - startTime);
    brodcastEvent();
  }

  public void handleImportConflict(File file, Node folderNode, String name, String title, String filePath) throws Exception {
    if (actionData.getConflict().equals("updateAll")) {
      Node existingNode = folderNode.getNode(name);
      createNewVersion(existingNode, file);
      actionData.addUpdatedFile(filePath.replace(actionData.getTempFolderPath(), ""));
    } else if (actionData.getConflict().equals("duplicate")) {
      int i = 1;
      String extension = FilenameUtils.getExtension(name);
      String fileBaseName = FilenameUtils.getBaseName(name);
      String titleBase = FilenameUtils.getBaseName(title);
      String newFileName = fileBaseName + "(" + i + ")." + extension;
      String newFileTitle = titleBase + "(" + i + ")." + extension;
      while (folderNode.hasNode(newFileName)) {
        i++;
        newFileName = fileBaseName + "(" + i + ")." + extension;
        newFileTitle = titleBase + "(" + i + ")." + extension;
      }
      createFile(folderNode, file, newFileName, newFileTitle);
      actionData.addDuplicatedFile(filePath.replace(actionData.getTempFolderPath(), ""));
    } else {
      actionData.addIgnoredFile(filePath.replace(actionData.getTempFolderPath(), ""));
    }
  }

  public void createNewVersion(Node node, File file) throws RepositoryException, IOException {
    if (node.isNodeType(MIX_VERSIONABLE)) {
      try (FileInputStream fileInputStream = new FileInputStream(file)) {
        Node destContentNode = node.getNode(JCR_CONTENT);
        destContentNode.setProperty(JCR_DATA, fileInputStream);
        destContentNode.setProperty(JCR_LAST_MODIFIED, Calendar.getInstance());
        if (node.isNodeType(EXO_MODIFY)) {
          node.setProperty(EXO_DATE_MODIFIED, Calendar.getInstance());
          node.setProperty(EXO_LAST_MODIFIED_DATE, Calendar.getInstance());
        }
        node.save();
        if (!node.isCheckedOut()) {
          node.checkout();
        }
        node.checkin();
        node.checkout();
        node.getSession().save();
      }
    }
  }

  private void createFile(Node folderNode, File file, String name, String title) throws Exception{
    try (FileInputStream fileInputStream = new FileInputStream(file)) {
    Node fileNode = folderNode.addNode(name, NT_FILE);
    if (!fileNode.isNodeType(EXO_RSS_ENABLE) && fileNode.canAddMixin(EXO_RSS_ENABLE)) {
      fileNode.addMixin(EXO_RSS_ENABLE);
    }
    fileNode.setProperty(EXO_TITLE, title);
    fileNode.setProperty(EXO_NAME, name);
    if (fileNode.canAddMixin(EXO_MODIFY)) {
      fileNode.addMixin(EXO_MODIFY);
    }
    Calendar now = Calendar.getInstance();
    fileNode.setProperty(EXO_DATE_MODIFIED, now);
    fileNode.setProperty(EXO_LAST_MODIFIED_DATE, now);
    fileNode.setProperty(EXO_LAST_MODIFIER, actionData.getIdentity().getUserId());
    if (fileNode.canAddMixin(EXO_SORTABLE)) {
      fileNode.addMixin(EXO_SORTABLE);
    }
    if (fileNode.canAddMixin(MIX_VERSIONABLE)) {
      fileNode.addMixin(MIX_VERSIONABLE);
    }
    Node jcrContent = fileNode.addNode(JCR_CONTENT, NT_RESOURCE);
    jcrContent.setProperty(JCR_DATA, fileInputStream);
    jcrContent.setProperty(JCR_LAST_MODIFIED, java.util.Calendar.getInstance());
    jcrContent.setProperty(JCR_ENCODING, "UTF-8");
    String mimeType = mimeTypes.getMimeType(file.getName());
    jcrContent.setProperty(JCR_MIME_TYPE, mimeType);
    folderNode.save();
  }
  }
  private void brodcastEvent() {
    try {
      listenerService.broadcast("bulk_actions_document_event", actionData.getIdentity(), actionData);
    } catch (Exception e) {
      log.error("cannot broadcast bulk action event");
    }
  }

}
