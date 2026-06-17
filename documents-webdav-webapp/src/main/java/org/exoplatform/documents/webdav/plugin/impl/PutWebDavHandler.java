/**
 * Copyright (C) 2025 eXo Platform SAS.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see<http://www.gnu.org/licenses/>.
*/
package org.exoplatform.documents.webdav.plugin.impl;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.fileupload2.core.FileUploadException;
import org.apache.http.HttpHeaders;
import org.springframework.stereotype.Component;

import org.exoplatform.container.PortalContainer;
import org.exoplatform.documents.webdav.model.WebDavException;
import org.exoplatform.documents.webdav.plugin.WebDavHttpMethodPlugin;
import org.exoplatform.services.rest.ExtHttpHeaders;
import org.exoplatform.upload.UploadFileValidator;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.SneakyThrows;

@Component
public class PutWebDavHandler extends WebDavHttpMethodPlugin {

  private PortalContainer           portalContainer;

  private List<UploadFileValidator> uploadFileValidators;

  public PutWebDavHandler(PortalContainer portalContainer) {
    super("PUT");
    this.portalContainer = portalContainer;
  }

  @Override
  @SneakyThrows
  public void handle(HttpServletRequest httpRequest, HttpServletResponse httpResponse) throws WebDavException {
    String resourcePath = getResourcePath(httpRequest);
    List<String> lockTokens = getLockTokens(httpRequest);
    String fileType = httpRequest.getHeader(ExtHttpHeaders.FILE_NODETYPE);
    String contentNodeType = httpRequest.getHeader(ExtHttpHeaders.CONTENT_NODETYPE);
    String mixinTypes = httpRequest.getHeader(ExtHttpHeaders.CONTENT_MIXINTYPES);
    String mediaType = httpRequest.getHeader(HttpHeaders.CONTENT_TYPE);
    InputStream inputStream = httpRequest.getInputStream();

    String fileName = resourcePath.substring(resourcePath.lastIndexOf("/") + 1);
    validateUploadedFile(inputStream, fileName, mediaType);
    documentWebDavService.saveFile(resourcePath,
                                   fileType,
                                   contentNodeType,
                                   mediaType,
                                   mixinTypes,
                                   inputStream,
                                   lockTokens,
                                   httpRequest.getRemoteUser());
    httpResponse.setStatus(HttpServletResponse.SC_CREATED);
  }

  private void validateUploadedFile(InputStream inputStream,
                                    String fileName,
                                    String mimeType) throws FileUploadException {
    for (UploadFileValidator uploadFileValidator : getUploadFileValidators()) {
      if (uploadFileValidator.supports(fileName, mimeType)) {
        uploadFileValidator.validate(fileName, mimeType, inputStream);
      }
    }
  }

  private List<UploadFileValidator> getUploadFileValidators() {
    if (uploadFileValidators == null) {
      List<UploadFileValidator> services = portalContainer.getComponentInstancesOfType(UploadFileValidator.class);
      if (services == null) {
        uploadFileValidators = new ArrayList<>();
      } else {
        uploadFileValidators = new ArrayList<>(services);
      }
    }
    return uploadFileValidators;
  }

}
