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

package org.exoplatform.documents.plugin;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.documents.service.DocumentFileService;

import io.meeds.portal.thumbnail.model.FileContent;
import io.meeds.portal.thumbnail.plugin.ImageThumbnailPlugin;

public class DocumentsImageThumbnailPlugin extends ImageThumbnailPlugin {

  public static final String DOCUMENTS_IMAGE = "imageThumbnail";

  @Override
  public String getFileType() {
    return DOCUMENTS_IMAGE;
  }

  @Override
  public FileContent getImage(String fileId, String userName) throws ObjectNotFoundException {
    DocumentFileService documentFileService = CommonsUtils.getService(DocumentFileService.class);
    return documentFileService.getDocumentContent(fileId, userName);
  }
}
