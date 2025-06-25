/*
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
package org.exoplatform.documents.storage.jcr.listener;

import io.meeds.social.category.model.CategoryObject;
import jakarta.annotation.PostConstruct;
import org.apache.commons.collections4.CollectionUtils;
import org.exoplatform.documents.service.DocumentFileService;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.core.ManageableRepository;
import org.exoplatform.services.jcr.ext.app.SessionProviderService;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.exoplatform.services.listener.Event;
import org.exoplatform.services.listener.ListenerBase;
import org.exoplatform.services.listener.ListenerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static io.meeds.social.category.service.CategoryLinkService.EVENT_CATEGORY_LINK_ADDED;
import static io.meeds.social.category.service.CategoryLinkService.EVENT_CATEGORY_LINK_REMOVED;
import static org.exoplatform.documents.storage.jcr.util.JCRDocumentsUtil.getNodeByIdentifier;
import static org.exoplatform.documents.storage.jcr.util.NodeTypeConstants.DOCUMENT_CATEGORY_IDS;
import static org.exoplatform.documents.storage.jcr.util.NodeTypeConstants.MIX_DOCUMENT_CATEGORY;

@Component
public class CategoryLinkModifiedDocumentListener implements ListenerBase<Long, CategoryObject> {

  public static final String     OBJECT_TYPE = "document";

  @Autowired
  private DocumentFileService    documentFileService;

  @Autowired
  private RepositoryService      repositoryService;

  @Autowired
  private SessionProviderService sessionProviderService;

  @Autowired
  private ListenerService        listenerService;

  @PostConstruct
  public void init() {
    listenerService.addListener(EVENT_CATEGORY_LINK_ADDED, this);
    listenerService.addListener(EVENT_CATEGORY_LINK_REMOVED, this);
  }

  @Override
  public void onEvent(Event<Long, CategoryObject> event) throws Exception {
    CategoryObject object = event.getData();
    String type = object.getType();
    if (OBJECT_TYPE.equals(type)) {
      SessionProvider sessionProvider = sessionProviderService.getSystemSessionProvider(null);
      ManageableRepository repository = repositoryService.getCurrentRepository();

      Session systemSession = sessionProvider.getSession(repository.getConfiguration().getDefaultWorkspaceName(), repository);
      Node node = getNodeByIdentifier(systemSession, object.getId());
      if (node == null) {
        return;
      }
      List<Long> categoryIds = documentFileService.getDocumentCategoryIds(object.getId());
      String[] newCategoryIds = categoryIds.stream().map(String::valueOf).toArray(String[]::new);
      if (node.canAddMixin(MIX_DOCUMENT_CATEGORY)) {
        node.addMixin(MIX_DOCUMENT_CATEGORY);
      }

      boolean modified = true;
      if (node.hasProperty(DOCUMENT_CATEGORY_IDS)) {
        Value[] existingValues = node.getProperty(DOCUMENT_CATEGORY_IDS).getValues();
        List<String> existingIdsList = Arrays.stream(existingValues).map(v -> {
          try {
            return v.getString();
          } catch (RepositoryException e) {
            return null;
          }
        }).filter(Objects::nonNull).collect(Collectors.toList());

        modified = CollectionUtils.size(existingIdsList) != CollectionUtils.size(categoryIds) || (!categoryIds.isEmpty() && !CollectionUtils.isEqualCollection(Collections.singleton(categoryIds), existingIdsList));
      }
      if (modified) {
        node.setProperty(DOCUMENT_CATEGORY_IDS, newCategoryIds);
        node.save();
        systemSession.save();
      }
    }
  }
}
