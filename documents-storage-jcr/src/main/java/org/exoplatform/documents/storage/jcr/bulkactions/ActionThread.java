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

import static org.exoplatform.documents.storage.jcr.util.JCRDocumentsUtil.cleanFiles;

import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
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
import org.exoplatform.services.jcr.ext.utils.VersionHistoryUtils;
import org.exoplatform.services.jcr.util.Text;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.upload.UploadService;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;


public class ActionThread implements Runnable {

  private static final Log               log                 = ExoLogger.getLogger(ActionThread.class);

  private static final String            ZIP_EXTENSION       = ".zip";

  private static final String            ZIP_PREFIX          = "downloadzip";

  private static final String            TEMP_DIRECTORY_PATH = "java.io.tmpdir";

  private static final int               BUFFER_SIZE          = 8192;

  private static final int               UNIX_FILE_TYPE_MASK  = 0170000;

  private static final int               UNIX_SYMLINK_FLAG    = 0120000;

  public static final String             NT_FILE                = "nt:file";

  public static final String             NT_FOLDER              = "nt:folder";

  public static final String             JCR_CONTENT            = "jcr:content";

  public static final String             JCR_DATA               = "jcr:data";

  public static final String             MIX_REFERENCEABLE      = "mix:referenceable";

  public static final String             EXO_TITLE              = "exo:title";

  public static final String             JCR_LAST_MODIFIED      = "jcr:lastModified";

  public static final String             JCR_ENCODING           = "jcr:encoding";

  public static final String             JCR_MIME_TYPE          = "jcr:mimeType";

  public static final String             NT_RESOURCE            = "nt:resource";

  public static final String             EXO_RSS_ENABLE         = "exo:rss-enable";

  public static final String             EXO_NAME               = "exo:name";

  public static final String             EXO_DATE_CREATED       = "exo:dateCreated";

  public static final String             EXO_DATE_MODIFIED      = "exo:dateModified";

  public static final String             EXO_LAST_MODIFIED_DATE = "exo:lastModifiedDate";

  public static final String             EXO_LAST_MODIFIER      = "exo:lastModifier";

  public static final String             EXO_MODIFY             = "exo:modify";

  public static final String             EXO_SORTABLE           = "exo:sortable";

  public static final String             MIX_VERSIONABLE        = "mix:versionable";

  private static final List<Charset> CHARSETS = Arrays.asList(
          StandardCharsets.ISO_8859_1,
          Charset.forName("Windows-1252"),
          Charset.forName("CP437"),
          Charset.forName("Cp850"));

  private static final MimeTypeResolver  mimeTypes              = new MimeTypeResolver();

  private long                           startTime              = 0;

  private final List<AbstractNode>       items;

  private final JCRDeleteFileStorage     jCrDeleteFileStorage;

  private final DocumentFileStorage      documentFileStorage;

  private final BulkStorageActionService bulkStorageActionService;

  private final ListenerService          listenerService;

  private final UploadService            uploadService;

  private final Long                     identityId;

  private final Session                  session;

  private ActionData                     actionData;

  private Node                           parentNode;

  private String                         tempFolderPath;

  private Map<String, Object>            params;

  private final Node                     parent;

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
    if (actionData.getActionType().equals(ActionType.PERMANENTLY_DELETE.name())) {
      actionData.setStatus(ActionStatus.IN_PROGRESS.name());
      permanentlyDeleteItems();
    }
    if (actionData.getActionType().equals(ActionType.RESTORE.name())) {
      actionData.setStatus(ActionStatus.IN_PROGRESS.name());
      restoreItems();
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
        jCrDeleteFileStorage.deleteDocument(session,
                                            item.getPath(),
                                            item.getId(),
                                            true,
                                            true,
                                            0,
                                            actionData.getIdentity(),
                                            identityId);
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

  private void permanentlyDeleteItems() {
    int errors = 0;
    List<String> treatedItemsIds = new ArrayList<>();
    for (AbstractNode item : items) {
      if (checkCanceled()) {
        break;
      }
      try {
        jCrDeleteFileStorage.deleteDocumentPermanently(item.getPath(), item.getId());
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

  private void restoreItems() {
    int errors = 0;
    List<String> treatedItemsIds = new ArrayList<>();
    for (AbstractNode item : items) {
      if (checkCanceled()) {
        break;
      }
      try {
        jCrDeleteFileStorage.restoreFromTrash(item.getPath());
        actionData.setStatus(ActionStatus.IN_PROGRESS.name());
        treatedItemsIds.add(item.getId());
      } catch (PathNotFoundException path) {
        log.error("The document with this path is not found" + item.getPath(), path);
        errors++;
      } catch (Exception e) {
        log.error("Error when restoring the document" + item.getPath(), e);
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

  public static void fixAndExtractZip(File zipFile, File outputDir) throws IOException {
    if (!outputDir.exists() && !outputDir.mkdirs()) {
      throw new IOException("Cannot create ZIP extraction directory: " + outputDir);
    }

    try (ZipFile zip = new ZipFile(zipFile)) {
      List<FileHeader> headers = zip.getFileHeaders();
      for (FileHeader header : headers) {
        byte[] rawBytes = header.getFileName().getBytes(StandardCharsets.ISO_8859_1);
        String fixedName = pickBestEncoding(rawBytes);
        extractZipEntry(zip, header, outputDir, fixedName);
      }
    }

    ensureNoSymlinks(outputDir);
  }

  private static void safeExtractZip(File zipFile, File outputDir, Charset charset) throws IOException {
    if (!outputDir.exists() && !outputDir.mkdirs()) {
      throw new IOException("Cannot create ZIP extraction directory: " + outputDir);
    }

    try (ZipFile zip = new ZipFile(zipFile)) {
      zip.setCharset(charset);
      List<FileHeader> headers = zip.getFileHeaders();
      for (FileHeader header : headers) {
        extractZipEntry(zip, header, outputDir, header.getFileName());
      }
    }

    ensureNoSymlinks(outputDir);
  }

  private static void extractZipEntry(ZipFile zip, FileHeader header, File outputDir, String entryName) throws IOException {
    if (isSymlink(header)) {
      throw new SecurityException("ZIP archive contains symbolic links: " + entryName);
    }

    File outFile = validateZipEntry(outputDir, entryName);
    Path outPath = outFile.toPath();

    if (header.isDirectory()) {
      Files.createDirectories(outPath);
      ensureNoSymlinks(outFile);
      return;
    }

    File parent = outFile.getParentFile();
    if (parent != null) {
      Files.createDirectories(parent.toPath());
      ensureNoSymlinks(parent);
    }

    if (Files.exists(outPath, LinkOption.NOFOLLOW_LINKS)) {
      throw new SecurityException("Duplicate or unsafe ZIP entry: " + entryName);
    }

    try (InputStream is = zip.getInputStream(header);
         OutputStream os = Files.newOutputStream(outPath, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
      byte[] buffer = new byte[BUFFER_SIZE];
      int len;
      while ((len = is.read(buffer)) != -1) {
        os.write(buffer, 0, len);
      }
    }
  }

  private static File validateZipEntry(File destinationDir, String entryName) throws IOException {
    if (StringUtils.isBlank(entryName) || entryName.indexOf('\0') >= 0) {
      throw new SecurityException("ZIP entry has an invalid name");
    }

    String normalizedName = entryName.replace('\\', '/');
    if (normalizedName.startsWith("/") || normalizedName.startsWith("../") || normalizedName.contains("/../")) {
      throw new SecurityException("ZIP entry outside target directory: " + entryName);
    }

    Path destinationPath = destinationDir.toPath().toAbsolutePath().normalize();
    Path targetPath = destinationPath.resolve(normalizedName).normalize();
    if (!targetPath.startsWith(destinationPath)) {
      throw new SecurityException("ZIP entry outside target directory: " + entryName);
    }

    File targetFile = targetPath.toFile();
    String destDirPath = destinationDir.getCanonicalPath();
    String targetCanonicalPath = targetFile.getCanonicalPath();
    if (!targetCanonicalPath.equals(destDirPath) && !targetCanonicalPath.startsWith(destDirPath + File.separator)) {
      throw new SecurityException("ZIP entry outside target directory: " + entryName);
    }

    return targetFile;
  }

  private static boolean isSymlink(FileHeader header) {
    byte[] attributes = header.getExternalFileAttributes();
    if (attributes == null || attributes.length < 4) {
      return false;
    }

    int littleEndianAttributes = ((attributes[0] & 0xff))
        | ((attributes[1] & 0xff) << 8)
        | ((attributes[2] & 0xff) << 16)
        | ((attributes[3] & 0xff) << 24);
    int bigEndianAttributes = ((attributes[3] & 0xff))
        | ((attributes[2] & 0xff) << 8)
        | ((attributes[1] & 0xff) << 16)
        | ((attributes[0] & 0xff) << 24);

    return isUnixSymlink(littleEndianAttributes) || isUnixSymlink(bigEndianAttributes);
  }

  private static boolean isUnixSymlink(int externalAttributes) {
    int unixMode = (externalAttributes >>> 16) & 0xffff;
    return (unixMode & UNIX_FILE_TYPE_MASK) == UNIX_SYMLINK_FLAG;
  }

  private static void ensureNoSymlinks(File root) throws IOException {
    if (root == null || !root.exists()) {
      return;
    }

    Path rootPath = root.toPath();
    if (Files.isSymbolicLink(rootPath)) {
      throw new SecurityException("ZIP archive contains symbolic links");
    }

    File[] files = root.listFiles();
    if (files == null) {
      return;
    }

    for (File file : files) {
      ensureNoSymlinks(file);
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

  private static String pickBestEncoding(byte[] rawBytes) {
    String best = null;
    int bestScore = -1;
    for (Charset cs : CHARSETS) {
      String decoded = new String(rawBytes, cs);
      int score = readabilityScore(decoded);
      if (score > bestScore) {
        bestScore = score;
        best = decoded;
      }
    }
    return best;
  }

  private static int readabilityScore(String s) {
    int score = 0;
    for (char c : s.toCharArray()) {
      if (Character.isLetterOrDigit(c)) score++;
      else if (c >= 0x0600 && c <= 0x06FF) score += 2;
      else if (c == ' ' || c == '\'' || c == '-' || c == '_' || c == '.') score++;
      else if (c == '�') score -= 5;
    }
    return score;
  }

  private static void listFiles(File dir, List<String> files) {
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

  private void downloadItems() {
    List<javax.jcr.Node> nodes = items.stream()
                                      .map(document -> JCRDocumentsUtil.getNodeByIdentifier(session, document.getId()))
                                      .toList();

    try {
      tempFolderPath = Files.createTempDirectory(BulkStorageActionService.TEMP_DOWNLOAD_FOLDER_PREFIX).toString();

    boolean hasFolders = items.stream().anyMatch(AbstractNode::isFolder);
    brodcastEvent();
    try {
      for (Node node : nodes) {
        if (checkCanceled()) {
          File folder = new File(tempFolderPath);
          cleanFiles(folder);
          break;
        }
        parentNode = node.getParent();
        if (hasFolders) {
          JCRDocumentsUtil.createTempFilesAndFolders(node, "", "", tempFolderPath, parentNode);
        } else {
          JCRDocumentsUtil.createFile(node, "", "", tempFolderPath, parentNode);
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
      cleanFiles(folder);
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

  public void importFromZip() {
    String tempFolder = actionData.getTempFolderPath();
    String fixedTempFolder = "";
    try {
      startTime = System.currentTimeMillis();
      actionData.setStatus(ActionStatus.UNZIPPING.name());
      brodcastEvent();
      String zipFilePath = uploadService.getUploadResource(actionData.getActionId()).getStoreLocation();
      List<String> files = new ArrayList<>();
      safeExtractZip(new File(zipFilePath), new File(tempFolder), StandardCharsets.UTF_8);
      listFiles(new File(tempFolder), files);
      if (files.isEmpty() || files.stream().anyMatch(s -> s.indexOf('�') >= 0)) {
        fixedTempFolder = tempFolder + "_fixed";
        fixAndExtractZip(new File(zipFilePath), new File(fixedTempFolder));
        files = new ArrayList<>();
        listFiles(new File(fixedTempFolder), files);
      }
      actionData.setFiles(files);
      uploadService.removeUploadResource(actionData.getActionId());
      actionData.setStatus(ActionStatus.CREATING_DOCUMENTS.name());
      brodcastEvent();
      createItems(fixedTempFolder.isEmpty() ? tempFolder : fixedTempFolder);
    } catch (SecurityException e) {
      actionData.setStatus(ActionStatus.CANNOT_UNZIP_FILE.name());
      log.error("Security: malicious ZIP archive import attempt detected", e);
      brodcastEvent();
    } catch (Exception e) {
      actionData.setStatus(ActionStatus.CANNOT_UNZIP_FILE.name());
      log.error("Cannot unzip the zip file", e);
      brodcastEvent();
    } finally {
      if (StringUtils.isNotBlank(tempFolder)) {
        cleanFiles(new File(tempFolder));
      }
      if (StringUtils.isNotBlank(fixedTempFolder)) {
        cleanFiles(new File(fixedTempFolder));
      }
      bulkStorageActionService.removeActionData(actionData);
    }
  }

  public void createItems(String tempFolderPath) throws RepositoryException {
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
          handleImportConflict(file, folderNode, name, title, filePath, tempFolderPath);
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
    } else {
      actionData.setStatus(ActionStatus.DONE_SUCCESSFULLY.name());
    }
    actionData.setDuration(System.currentTimeMillis() - startTime);
    brodcastEvent();
  }

  public void handleImportConflict(File file,
                                   Node folderNode,
                                   String name,
                                   String title,
                                   String filePath,
                                   String tempFolderPath) throws Exception {
    if (actionData.getConflict().equals("updateAll")) {
      Node existingNode = folderNode.getNode(name);
      createNewVersion(existingNode, file);
      actionData.addUpdatedFile(filePath.replace(tempFolderPath, ""));
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
      actionData.addDuplicatedFile(filePath.replace(tempFolderPath, ""));
    } else {
      actionData.addIgnoredFile(filePath.replace(tempFolderPath, ""));
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

  private void createFile(Node folderNode, File file, String name, String title) throws Exception {
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
    VersionHistoryUtils.createVersion(fileNode);
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
