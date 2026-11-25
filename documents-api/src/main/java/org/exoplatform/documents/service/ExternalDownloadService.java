/*
 * Copyright (C) 2023 eXo Platform SAS
 *
 *  This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <gnu.org/licenses>.
 */
package org.exoplatform.documents.service;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.documents.model.DownloadItem;
import org.exoplatform.documents.model.FolderArchive;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public interface ExternalDownloadService {

  /**
   * Gets a download item of a given document
   *
   * @param documentId document id
   * @return {@link DownloadItem}, or null when the document is in the trash
   *         (a public link of a trashed document is inactive until the document
   *         is restored)
   * @deprecated holds the whole content in memory, which fails above 2 GiB: use
   *             {@link #getDocumentDownloadInfo(String)} and
   *             {@link #writeDocumentContent(String, OutputStream)} instead.
   *             Since 7.3.x.
   */
  @Deprecated(since = "7.3.x")
  DownloadItem getDocumentDownloadItem(String documentId);

  /**
   * Gets the name and mime type of a given document, without its content
   *
   * @param documentId document id
   * @return {@link DownloadItem} with no content, its mime type null for a
   *         folder, or null when no document has this id or the document is in
   *         the trash
   */
  DownloadItem getDocumentDownloadInfo(String documentId);

  /**
   * Gets the size of the content of a given document, without reading it
   *
   * @param documentId document id
   * @return the content's size in bytes
   * @throws ObjectNotFoundException when no document has this id, it has no
   *           content or it is in the trash
   */
  long getDocumentContentLength(String documentId) throws ObjectNotFoundException;

  /**
   * Writes the content of a given document as it is read, never held whole in
   * memory, whatever its size
   *
   * @param documentId document id
   * @param outputStream where the content is written; it is not closed
   * @throws ObjectNotFoundException when no document has this id, it has no
   *           content or it is in the trash
   * @throws IOException when the content cannot be read or written
   */
  void writeDocumentContent(String documentId, OutputStream outputStream) throws ObjectNotFoundException, IOException;

  /**
   * Download a zipped folder shared by a public link, with the rights of the
   * user who created the link, evaluated when this method runs: what that user
   * cannot read, a sub-folder, a document or a shortcut target, is left out of
   * the zip.
   *
   * @param folderId id of the folder holding the public link
   * @return the zipped folder, read from a file of its own that closing the
   *         stream deletes: the caller closes it
   * @throws ObjectNotFoundException when the folder has no public link, the
   *           folder is in the trash, the link records no creator, the creator
   *           is unknown, deleted or disabled, or the creator cannot read the
   *           folder
   * @throws IOException when the zip cannot be built or read
   * @deprecated builds the whole zip in a file before its first byte can be
   *             sent: use {@link #getFolderArchive(String)} instead. Since
   *             7.3.x.
   */
  @Deprecated(since = "7.3.x")
  InputStream downloadZippedFolder(String folderId) throws IOException, ObjectNotFoundException;

  /**
   * Prepares a folder shared by a public link for its download, walked with the
   * rights of the user who created the link as
   * {@link #downloadZippedFolder(String)} walks it; its zip is then written as
   * it is sent.
   *
   * @param folderId id of the folder holding the public link
   * @return the prepared folder: the caller closes it, which deletes what it
   *         prepared
   * @throws ObjectNotFoundException as {@link #downloadZippedFolder(String)}
   * @throws IOException when the folder cannot be prepared
   */
  FolderArchive getFolderArchive(String folderId) throws IOException, ObjectNotFoundException;

  /**
   * Checks that the document of a public link can be downloaded as
   * {@link #getDocumentDownloadItem(String)} and
   * {@link #downloadZippedFolder(String)} would serve it, without reading the
   * document or building a folder's zip
   *
   * @param documentId the link's document id
   * @throws ObjectNotFoundException when no document has this id, when the
   *           document is in the trash, or, for a folder, when its link records
   *           no active creator or the creator cannot read it
   */
  void checkDocumentDownload(String documentId) throws ObjectNotFoundException;
}
