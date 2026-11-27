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
package org.exoplatform.documents.model;

import java.util.ArrayList;
import java.util.List;

import org.exoplatform.services.security.Identity;

import lombok.Data;

@Data
public class ActionData {

  private String       actionId;

  private String       status;

  private String       actionType;

  private String       message;

  private int          numberOfItems;

  private List<String> treatedItemsIds;

  private String       downloadZipPath;

  private Identity     identity;

  private String       tempFolderPath;

  private String       userName;

  private String       folderPath;

  private String       conflict;

  private List<String> files;

  private String       documentInProgress;

  private String       parentFolder;

  private String       parentFolderName;

  private int          importedFilesCount = 0;

  private long         duration;

  private double       size;

  private List<String> createdFiles       = new ArrayList<>();

  private List<String> ignoredFiles       = new ArrayList<>();

  private List<String> duplicatedFiles    = new ArrayList<>();

  private List<String> updatedFiles       = new ArrayList<>();

  private List<String> failedFiles        = new ArrayList<>();

  public void addCreatedFile(String fileName) {
    createdFiles.add(fileName);
  }

  public void addIgnoredFile(String fileName) {
    ignoredFiles.add(fileName);
  }

  public void addDuplicatedFile(String fileName) {
    duplicatedFiles.add(fileName);
  }

  public void addFailedFile(String fileName) {
    failedFiles.add(fileName);
  }

  public void addUpdatedFile(String fileName) {
    updatedFiles.add(fileName);
  }

  public synchronized String getDownloadZipPath() {
    return downloadZipPath;
  }

  public synchronized void setDownloadZipPath(String downloadZipPath) {
    this.downloadZipPath = downloadZipPath;
  }

  /**
   * Hands the archive path to one caller only: it is read and cleared under the
   * monitor of this action, so the reader of the archive and a discard never both own it.
   *
   * @return the archive path, or null when there is none or another caller took it
   */
  public synchronized String takeDownloadZipPath() {
    String path = downloadZipPath;
    downloadZipPath = null;
    return path;
  }

  public void incrementImportCount() {
    importedFilesCount++;
  }

}
