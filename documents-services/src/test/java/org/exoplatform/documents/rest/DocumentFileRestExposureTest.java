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
 *
*/
package org.exoplatform.documents.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import javax.ws.rs.core.Response;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.exoplatform.commons.api.settings.SettingService;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.documents.model.ExposedDocument;
import org.exoplatform.documents.model.ExposedDocumentPage;
import org.exoplatform.documents.model.SpaceDocumentsExposure;
import org.exoplatform.documents.rest.model.ExposedDocumentEntity;
import org.exoplatform.documents.rest.model.ExposedDocumentsEntity;
import org.exoplatform.documents.rest.model.SpaceDocumentsExposureEntity;
import org.exoplatform.documents.service.DocumentFileService;
import org.exoplatform.documents.service.ExternalDownloadService;
import org.exoplatform.documents.service.PublicDocumentAccessService;
import org.exoplatform.documents.service.SpaceDocumentsExposureService;
import org.exoplatform.documents.webdav.service.DocumentWebDavService;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.services.security.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.social.metadata.MetadataService;

class DocumentFileRestExposureTest {

  private static final long             SPACE_ID = 3L;

  private static final String           MANAGER  = "manager";

  private SpaceDocumentsExposureService exposureService;

  private DocumentFileRest              documentFileRest;

  @BeforeEach
  void setUp() {
    exposureService = mock(SpaceDocumentsExposureService.class);
    documentFileRest = new DocumentFileRest(mock(DocumentFileService.class),
                                            mock(SpaceService.class),
                                            mock(IdentityManager.class),
                                            mock(MetadataService.class),
                                            mock(SettingService.class),
                                            mock(PublicDocumentAccessService.class),
                                            mock(ExternalDownloadService.class),
                                            mock(DocumentWebDavService.class)) {
      @Override
      protected SpaceDocumentsExposureService getSpaceDocumentsExposureService() {
        return exposureService;
      }
    };
    ConversationState.setCurrent(new ConversationState(new Identity(MANAGER)));
  }

  @AfterEach
  void tearDown() {
    ConversationState.setCurrent(null);
  }

  @Test
  void theExposureCountsAreAnsweredNoStore() throws Exception {
    when(exposureService.getExposure(SPACE_ID, MANAGER)).thenReturn(new SpaceDocumentsExposure(2, 5));

    Response response = documentFileRest.getSpaceDocumentsExposure(SPACE_ID);

    assertEquals(200, response.getStatus());
    assertEquals(new SpaceDocumentsExposureEntity(2, 5), response.getEntity());
    assertNoStore(response);
  }

  @Test
  void theExposureCountsAreRefusedNoStore() throws Exception {
    when(exposureService.getExposure(SPACE_ID, MANAGER)).thenThrow(new IllegalAccessException("documents.space.exposure.notAllowed"));
    when(exposureService.getExposure(9L, MANAGER)).thenThrow(new ObjectNotFoundException("space"));
    when(exposureService.getExposure(5L, MANAGER)).thenThrow(new IllegalStateException("unreadable"));

    Response forbidden = documentFileRest.getSpaceDocumentsExposure(SPACE_ID);
    Response notFound = documentFileRest.getSpaceDocumentsExposure(9L);
    Response failed = documentFileRest.getSpaceDocumentsExposure(5L);

    assertEquals(403, forbidden.getStatus());
    assertEquals("documents.space.exposure.notAllowed", forbidden.getEntity());
    assertNoStore(forbidden);
    assertEquals(404, notFound.getStatus());
    assertNoStore(notFound);
    assertEquals(500, failed.getStatus());
    assertNoStore(failed);
  }

  @Test
  void thePublicLinkDocumentsAreAnsweredPagedNoStore() throws Exception {
    when(exposureService.getPublicLinkDocuments(SPACE_ID, 2, 2, MANAGER))
                                                                         .thenReturn(new ExposedDocumentPage(List.of(new ExposedDocument("doc",
                                                                                                                                         "Report",
                                                                                                                                         "application/pdf",
                                                                                                                                         false,
                                                                                                                                         "owner"),
                                                                                                                     new ExposedDocument("folder",
                                                                                                                                         "Shared",
                                                                                                                                         null,
                                                                                                                                         true,
                                                                                                                                         "owner")),
                                                                                                             7));

    Response response = documentFileRest.getSpacePublicLinkDocuments(SPACE_ID, 2, 2);

    assertEquals(200, response.getStatus());
    assertEquals(new ExposedDocumentsEntity(List.of(new ExposedDocumentEntity("doc", "Report", "application/pdf"),
                                                    new ExposedDocumentEntity("folder", "Shared", ExposedDocumentEntity.FOLDER_TYPE)),
                                            7,
                                            2,
                                            2),
                 response.getEntity());
    assertNoStore(response);
  }

  @Test
  void thePublicLinkDocumentsAreRefusedNoStore() throws Exception {
    when(exposureService.getPublicLinkDocuments(SPACE_ID, 0, 20, MANAGER))
                                                                          .thenThrow(new IllegalAccessException("documents.space.exposure.notAllowed"));
    when(exposureService.getPublicLinkDocuments(9L, 0, 20, MANAGER)).thenThrow(new ObjectNotFoundException("space"));
    when(exposureService.getPublicLinkDocuments(SPACE_ID, -1, 20, MANAGER))
                                                                           .thenThrow(new IllegalArgumentException("documents.space.exposure.invalidPage"));

    Response forbidden = documentFileRest.getSpacePublicLinkDocuments(SPACE_ID, 0, 20);
    Response notFound = documentFileRest.getSpacePublicLinkDocuments(9L, 0, 20);
    Response invalid = documentFileRest.getSpacePublicLinkDocuments(SPACE_ID, -1, 20);

    assertEquals(403, forbidden.getStatus());
    assertNoStore(forbidden);
    assertEquals(404, notFound.getStatus());
    assertNoStore(notFound);
    assertEquals(400, invalid.getStatus());
    assertEquals("documents.space.exposure.invalidPage", invalid.getEntity());
    assertNoStore(invalid);
  }

  private static void assertNoStore(Response response) {
    Object cacheControl = response.getMetadata().getFirst("Cache-Control");
    String value = String.valueOf(cacheControl);
    assertTrue(value.contains("no-store"), value);
    assertTrue(value.contains("private"), value);
  }

}
