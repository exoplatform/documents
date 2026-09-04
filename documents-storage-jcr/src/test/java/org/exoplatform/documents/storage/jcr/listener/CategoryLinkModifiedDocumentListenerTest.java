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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import javax.jcr.Node;
import javax.jcr.Session;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import org.exoplatform.documents.service.DocumentFileService;
import org.exoplatform.documents.storage.jcr.util.JCRDocumentsUtil;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.config.RepositoryEntry;
import org.exoplatform.services.jcr.core.ManageableRepository;
import org.exoplatform.services.jcr.ext.app.SessionProviderService;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.exoplatform.services.listener.Event;
import org.exoplatform.services.listener.ListenerService;

import io.meeds.social.category.model.CategoryObject;

import lombok.SneakyThrows;

@SpringBootTest(classes = { CategoryLinkModifiedDocumentListener.class, })
class CategoryLinkModifiedDocumentListenerTest {

  @MockitoBean
  private DocumentFileService                  documentFileService;

  @MockitoBean
  private RepositoryService                    repositoryService;

  @MockitoBean
  private SessionProviderService               sessionProviderService;

  @MockitoBean
  private ListenerService                      listenerService;

  @MockitoBean
  private Event<Long, CategoryObject>          event;

  @Autowired
  private CategoryLinkModifiedDocumentListener listener;

  @SneakyThrows
  @Test
  void onEvent() {
    try (MockedStatic<JCRDocumentsUtil> mockedStatic = mockStatic(JCRDocumentsUtil.class)) {
      Node node = mock(Node.class);
      mockedStatic.when(() -> JCRDocumentsUtil.getNodeByIdentifier(any(Session.class), anyString())).thenReturn(node);

      // When
      CategoryObject object = mock(CategoryObject.class);
      when(object.getType()).thenReturn("document");
      when(object.getId()).thenReturn("documentId");
      when(event.getData()).thenReturn(object);
      ManageableRepository repository = mock(ManageableRepository.class);
      RepositoryEntry repositoryEntry = mock(RepositoryEntry.class);
      when(repositoryService.getCurrentRepository()).thenReturn(repository);
      when(repository.getConfiguration()).thenReturn(repositoryEntry);
      SessionProvider sessionProvider = mock(SessionProvider.class);

      Session session = mock(Session.class);
      when(sessionProvider.getSession(anyString(), any(ManageableRepository.class))).thenReturn(session);

      when(sessionProviderService.getSystemSessionProvider(null)).thenReturn(sessionProvider);

      listener.onEvent(event);
      verify(sessionProviderService, times(1)).getSystemSessionProvider(null);
    }

  }
}
