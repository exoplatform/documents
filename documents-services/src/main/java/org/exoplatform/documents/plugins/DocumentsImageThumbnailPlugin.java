/*
 * Copyright (C) 2025 eXo Platform SAS
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

package org.exoplatform.documents.plugins;

import java.io.ByteArrayInputStream;
import java.util.Date;

import javax.jcr.ItemNotFoundException;

import org.apache.commons.io.IOUtils;

import org.exoplatform.commons.file.model.FileItem;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.documents.model.FileContent;
import org.exoplatform.documents.service.DocumentFileService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.thumbnail.ImageThumbnailPlugin;

public class DocumentsImageThumbnailPlugin extends ImageThumbnailPlugin {

  public static final String DOCUMENTS_IMAGE = "documentsImage";

  private static final Log log = ExoLogger.getExoLogger(DocumentsImageThumbnailPlugin.class);

  @Override
  public String getFileType() {
    return DOCUMENTS_IMAGE;
  }

  @Override
  public FileItem getImage(String fileId, String userName) {
    DocumentFileService documentFileService = CommonsUtils.getService(DocumentFileService.class);
    try {
      FileContent fileContent = documentFileService.getDocumentContent(fileId, userName);
      return new FileItem(null,
              fileContent.getName(),
              fileContent.getMimeType(),
              "",
              0,
              new Date(),
              "",
              false,
              new ByteArrayInputStream(IOUtils.toByteArray(fileContent.getContent())));
    } catch (ItemNotFoundException e) {
      log.warn("Document with id {} not found", fileId);
    } catch (Exception e) {
      log.error("Cannot get content of document with id {}", fileId, e);
    }
    return null;
  }
}
