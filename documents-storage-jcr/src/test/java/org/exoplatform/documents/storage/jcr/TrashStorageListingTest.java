/*
 * Copyright (C) 2026 eXo Platform SAS.
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
package org.exoplatform.documents.storage.jcr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;
import org.exoplatform.documents.constant.DocumentSortField;
import org.exoplatform.documents.model.TrashElementNodeFilter;
import org.exoplatform.documents.storage.jcr.FakeJcr.Item;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.core.ManageableRepository;
import org.exoplatform.services.jcr.ext.app.SessionProviderService;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.exoplatform.services.jcr.ext.hierarchy.NodeHierarchyCreator;
import org.exoplatform.services.jcr.impl.core.LocationFactory;
import org.exoplatform.services.jcr.impl.core.NamespaceRegistryImpl;
import org.exoplatform.services.jcr.impl.core.SessionImpl;
import org.exoplatform.services.jcr.impl.core.query.DefaultQueryNodeFactory;
import org.exoplatform.services.jcr.impl.core.query.sql.JCRSQLQueryBuilder;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.spi.SpaceService;

/**
 * The statements the trash is listed and looked up with: each one is kept to
 * the trash home, since the restore location is also carried by nodes
 * outside it, and a trash id that is not one runs none. The statements are
 * given to the repository's own parser; what they select is not seen here.
 */
class TrashStorageListingTest {

  private static final String IN_TRASH = "SELECT * FROM nt:base WHERE jcr:path LIKE '/Trash/%' AND exo:restorePath IS NOT NULL";

  private static final String TRASH_ID = "11111111-1111-1111-1111-111111111111";

  private FakeJcr             jcr;

  private SessionProvider     systemProvider;

  private TrashStorageImpl    trashStorage;

  @BeforeEach
  void setUp() throws Exception {
    jcr = new FakeJcr("collaboration");
    jcr.add("/Trash", "nt:unstructured");
    jcr.commit();
    SessionImpl systemSession = jcr.session("__system");

    RepositoryService repositoryService = mock(RepositoryService.class);
    when(repositoryService.getCurrentRepository()).thenReturn(mock(ManageableRepository.class));
    systemProvider = mock(SessionProvider.class);
    lenient().when(systemProvider.getSession(anyString(), any())).thenReturn(systemSession);
    SessionProviderService sessionProviderService = mock(SessionProviderService.class);
    lenient().when(sessionProviderService.getSystemSessionProvider(any())).thenReturn(systemProvider);

    InitParams params = new InitParams();
    params.addParameter(valueParam("trashWorkspace", "collaboration"));
    params.addParameter(valueParam("trashHomeNodePath", "/Trash"));
    trashStorage = new TrashStorageImpl(repositoryService,
                                        sessionProviderService,
                                        mock(ListenerService.class),
                                        mock(NodeHierarchyCreator.class),
                                        mock(IdentityManager.class),
                                        mock(SpaceService.class),
                                        params);
  }

  @Test
  void theListingsOfTheTrashAreKeptToTheTrashHome() throws Exception {
    trashStorage.getTrashElements(new TrashElementNodeFilter(DocumentSortField.NAME, true, 10, 0));
    trashStorage.countDeletedDocuments();
    trashStorage.getAllNodeInTrash(systemProvider);
    trashStorage.getAllNodeInTrashByUser(systemProvider, "john");

    assertEquals(List.of(IN_TRASH + " ORDER BY exo:title ASC",
                         IN_TRASH,
                         IN_TRASH,
                         IN_TRASH + " AND exo:lastModifier='john'"),
                 jcr.getStatements());
    assertParsed();
  }

  @Test
  void aQuoteInAUsernameStaysInsideItsLiteral() throws Exception {
    trashStorage.getAllNodeInTrashByUser(systemProvider, "o'neil");

    assertEquals(List.of(IN_TRASH + " AND exo:lastModifier='o''neil'"), jcr.getStatements());
    assertParsed();
  }

  @Test
  void aNodeIsLookedUpByItsTrashIdInTheTrashOnly() throws Exception {
    Item file = jcr.addFile("/Trash/report.docx", 1);
    file.mixin("exo:restoreLocation").set("exo:trashId", TRASH_ID);
    jcr.commit();
    jcr.setQueryHandler(statement -> statement.contains("'" + TRASH_ID + "'") ? List.of(jcr.find(file.getId())) : List.of());

    assertEquals("/Trash/report.docx", trashStorage.getNodeByTrashId(TRASH_ID).getPath());

    assertEquals(List.of("SELECT * FROM exo:restoreLocation WHERE jcr:path LIKE '/Trash/%' AND exo:trashId = '" + TRASH_ID + "'"),
                 jcr.getStatements());
    assertParsed();
  }

  @Test
  void whatIsNotATrashIdNamesNoNodeAndRunsNoStatement() throws Exception {
    assertNull(trashStorage.getNodeByTrashId(null));
    assertNull(trashStorage.getNodeByTrashId("-1"));
    assertNull(trashStorage.getNodeByTrashId(TRASH_ID + "' OR exo:trashId LIKE '%"));
    assertNull(trashStorage.getNodeByTrashId(TRASH_ID + "\n"));

    assertTrue(jcr.getStatements().isEmpty());
  }

  private void assertParsed() throws Exception {
    LocationFactory locationFactory = new LocationFactory(new NamespaceRegistryImpl());
    for (String statement : jcr.getStatements()) {
      assertNotNull(JCRSQLQueryBuilder.createQuery(statement, locationFactory, new DefaultQueryNodeFactory()), statement);
    }
  }

  private static ValueParam valueParam(String name, String value) {
    ValueParam param = new ValueParam();
    param.setName(name);
    param.setValue(value);
    return param;
  }

}
