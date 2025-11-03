/**
 * Copyright (C) 2025 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.exoplatform.documents.plugin;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.exoplatform.container.PortalContainer;
import org.exoplatform.documents.model.AbstractNode;
import org.exoplatform.documents.model.FileNode;
import org.exoplatform.documents.service.DocumentFileService;
import org.exoplatform.services.security.Identity;

import io.meeds.social.cms.model.ContentLinkExtension;
import io.meeds.social.cms.model.ContentLinkSearchResult;
import io.meeds.social.cms.plugin.ContentLinkPlugin;
import io.meeds.social.cms.service.ContentLinkPluginService;
import jakarta.annotation.PostConstruct;
import lombok.SneakyThrows;

@Component
public class DocumentContentLinkPlugin implements ContentLinkPlugin {

  public static final String                OBJECT_TYPE = DocumentAclPlugin.OBJECT_TYPE;

  private static final String               TITLE_KEY   = "contentLink.document";

  private static final String               ICON        = "far fa-file-alt";

  private static final String               COMMAND     = "document";

  private static final ContentLinkExtension EXTENSION   = new ContentLinkExtension(OBJECT_TYPE,
                                                                                   TITLE_KEY,
                                                                                   ICON,
                                                                                   COMMAND);

  @Autowired
  private PortalContainer                   container;

  @Autowired
  private DocumentFileService               documentFileService;

  @PostConstruct
  public void init() {
    container.getComponentInstanceOfType(ContentLinkPluginService.class).addPlugin(this);
  }

  @Override
  public ContentLinkExtension getExtension() {
    return EXTENSION;
  }

  @Override
  @SneakyThrows
  public List<ContentLinkSearchResult> search(String keyword, Identity identity, Locale locale, int offset, int limit) {
    List<FileNode> documents = documentFileService.search(keyword,
                                                          identity,
                                                          offset,
                                                          limit);
    return CollectionUtils.isEmpty(documents) ? Collections.emptyList() :
                                              documents.stream()
                                                      .filter(doc -> !doc.getPath().startsWith("/Users"))
                                                      .map(this::toContentLink)
                                                       .toList();
  }

  @Override
  public String getContentTitle(String objectId, Locale locale) {
    AbstractNode document = documentFileService.getDocumentById(objectId);
    return document instanceof FileNode ? document.getName() : null;
  }

  @Override
  public boolean isId(String keyword) {
    return StringUtils.isNotBlank(keyword) && keyword.matches("^[0-9a-fA-F]{32}$");
  }

  private ContentLinkSearchResult toContentLink(AbstractNode document) {
    if (document == null) {
      return null;
    } else {
      return new ContentLinkSearchResult(OBJECT_TYPE,
                                         String.valueOf(document.getId()),
                                         document.getName(),
                                         EXTENSION.getIcon());
    }
  }

}
