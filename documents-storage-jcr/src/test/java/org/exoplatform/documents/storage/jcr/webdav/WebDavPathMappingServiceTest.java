/**
 * Copyright (C) 2026 eXo Platform SAS
 *
 * This program is free software: you can redistribute it and/or modify
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
package org.exoplatform.documents.storage.jcr.webdav;

import static org.exoplatform.documents.storage.jcr.util.NodeTypeConstants.EXO_NAME;
import static org.exoplatform.documents.storage.jcr.util.NodeTypeConstants.EXO_TITLE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import javax.jcr.Property;
import javax.jcr.Session;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import org.exoplatform.documents.storage.jcr.webdav.dao.WebDavPathMappingRepository;
import org.exoplatform.documents.storage.jcr.webdav.entity.WebDavPathMappingEntity;
import org.exoplatform.services.jcr.impl.core.NodeImpl;

import lombok.SneakyThrows;

@RunWith(MockitoJUnitRunner.Silent.class)
public class WebDavPathMappingServiceTest {

  private static final String         NODE_TITLE_WITH_ACCENT    = "Rapport Équipe.docx";

  private static final String         NODE_TITLE_OTHER          = "Rapport.docx";

  private static final String         NODE_TITLE                = "Rapport Equipe.docx";

  private static final String         NODE_NAME                 = "rapport_equipe.docx";

  private static final String         IDENTITY_ID               = "1";

  private static final String         IDENTITY_BASE_JCR_PATH    = "/Users/john/Private";                   // NOSONAR

  private static final String         IDENTITY_ROOT_WEBDAV_PATH = "/John%20Doe%20%281%29";                     // NOSONAR

  private static final String         PARENT_JCR_PATH           = IDENTITY_BASE_JCR_PATH + "/Documents";

  private static final String         PARENT_WEBDAV_PATH        = IDENTITY_ROOT_WEBDAV_PATH + "/Documents";

  private static final String         JCR_PATH                  = PARENT_JCR_PATH + "/rapport_equipe.docx";

  private static final String         NODE_ID                   = "node-123";

  @Mock
  private Session                     session;

  @Mock
  private NodeImpl                    node;

  @Mock
  private NodeImpl                    parentNode;

  @Mock
  private NodeImpl                    identityRootNode;

  @Mock
  private Property                    property;

  @Mock
  private WebDavPathMappingRepository repository;

  @InjectMocks
  private WebDavPathMappingService    service;

  @Before
  @SneakyThrows
  public void setUp() {
    when(repository.save(any(WebDavPathMappingEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

    when(node.getPath()).thenReturn(JCR_PATH);
    when(node.getName()).thenReturn(NODE_NAME);
    when(node.getIdentifier()).thenReturn(NODE_ID);
    when(node.getParent()).thenReturn(parentNode);

    when(parentNode.getPath()).thenReturn(PARENT_JCR_PATH);
    when(parentNode.getName()).thenReturn("Documents");
    when(parentNode.getIdentifier()).thenReturn("parent-node-1");
    when(parentNode.getParent()).thenReturn(identityRootNode);

    when(identityRootNode.getPath()).thenReturn(IDENTITY_BASE_JCR_PATH);
    when(identityRootNode.getName()).thenReturn("Private");
    when(identityRootNode.getIdentifier()).thenReturn("identity-root-node-1");
  }

  @Test
  public void testFindJcrPathReturnsMappedPath() {
    WebDavPathMappingEntity entity = new WebDavPathMappingEntity();
    entity.setJcrPath(JCR_PATH);

    when(repository.findByParentJcrPathAndNormalizedVisibleName(PARENT_JCR_PATH,
                                                                "rapport equipe.docx")).thenReturn(Optional.of(entity));

    String result = service.findJcrPath(PARENT_JCR_PATH, NODE_TITLE);

    assertEquals(JCR_PATH, result);
  }

  @Test
  public void testFindJcrPathReturnsNullWhenNoMappingExists() {
    when(repository.findByParentJcrPathAndNormalizedVisibleName(PARENT_JCR_PATH, "missing.docx"))
                                                                                                 .thenReturn(Optional.empty());

    String result = service.findJcrPath(PARENT_JCR_PATH, "missing.docx");

    assertNull(result);
  }

  @Test
  @SneakyThrows
  public void testAllocateTechnicalNameReturnsFirstAvailableEncodedName() {
    when(session.itemExists(anyString())).thenReturn(false);

    String result = service.allocateTechnicalName(session, PARENT_JCR_PATH, NODE_TITLE);

    assertEquals(NODE_TITLE, result);
    verify(session).itemExists(PARENT_JCR_PATH + "/Rapport Equipe.docx");
  }

  @Test
  @SneakyThrows
  public void testAllocateTechnicalNameAddsSuffixWhenTechnicalNameAlreadyExists() {
    when(session.itemExists(PARENT_JCR_PATH + "/Rapport.docx")).thenReturn(true);
    when(session.itemExists(PARENT_JCR_PATH + "/Rapport (1).docx")).thenReturn(false);

    String result = service.allocateTechnicalName(session, PARENT_JCR_PATH, NODE_TITLE_OTHER);

    assertEquals("Rapport (1).docx", result);
  }

  @Test
  @SneakyThrows
  public void testSaveMappingCreatesPersistentEntityAndUpdatesTitlePropertiesWhenAvailable() {
    when(node.hasProperty(EXO_TITLE)).thenReturn(true);
    when(node.hasProperty(EXO_NAME)).thenReturn(true);
    when(repository.findByJcrPath(JCR_PATH)).thenReturn(Optional.empty());
    when(repository.findByParentJcrPathAndNormalizedVisibleName(PARENT_JCR_PATH, "rapport équipe.docx"))
                                                                                                        .thenReturn(Optional.empty());

    WebDavPathMappingEntity result = service.saveMapping(session,
                                                         PARENT_WEBDAV_PATH + "/Rapport%20%C3%89quipe.docx",
                                                         NODE_TITLE_WITH_ACCENT,
                                                         node);

    assertNotNull(result);
    assertEquals(IDENTITY_ID, result.getIdentityId());
    assertEquals(PARENT_JCR_PATH, result.getParentJcrPath());
    assertEquals(NODE_TITLE_WITH_ACCENT, result.getVisibleName());
    assertEquals("rapport équipe.docx", result.getNormalizedVisibleName());
    assertEquals(PARENT_WEBDAV_PATH + "/Rapport%20%C3%89quipe.docx", result.getWebDavPath());
    assertEquals(PARENT_WEBDAV_PATH, result.getParentWebDavPath());
    assertEquals(JCR_PATH, result.getJcrPath());
    assertEquals(NODE_ID, result.getNodeIdentifier());
    assertEquals(NODE_NAME, result.getTechnicalName());
    assertFalse(result.isFallbackName());
    assertFalse(result.isCollisionResolved());
    assertNotNull(result.getId());
    assertNotNull(result.getCreatedDate());
    assertNotNull(result.getUpdatedDate());

    verify(node).setProperty(EXO_TITLE, NODE_TITLE_WITH_ACCENT);
    verify(node).setProperty(EXO_NAME, NODE_NAME);
    verify(repository).save(any(WebDavPathMappingEntity.class));
  }

  @Test
  @SneakyThrows
  public void testSaveMappingReusesExistingEntityByJcrPath() {
    WebDavPathMappingEntity existing = new WebDavPathMappingEntity();
    existing.setCreatedDate("2025-01-01T00:00:00Z");
    existing.setJcrPath(JCR_PATH);

    when(repository.findByJcrPath(JCR_PATH)).thenReturn(Optional.of(existing));
    when(repository.findByParentJcrPathAndNormalizedVisibleName(PARENT_JCR_PATH, "rapport.docx"))
                                                                                                 .thenReturn(Optional.empty());

    WebDavPathMappingEntity result = service.saveMapping(session,
                                                         PARENT_WEBDAV_PATH + "/Rapport.docx",
                                                         NODE_TITLE_OTHER,
                                                         node);

    assertEquals(existing, result);
    assertEquals("2025-01-01T00:00:00Z", result.getCreatedDate());
    assertEquals(NODE_TITLE_OTHER, result.getVisibleName());
  }

  @Test
  public void testDeleteMappingDeletesExistingMapping() {
    WebDavPathMappingEntity entity = new WebDavPathMappingEntity();
    when(repository.findByJcrPath(JCR_PATH)).thenReturn(Optional.of(entity));

    service.deleteMapping(JCR_PATH);

    verify(repository).delete(entity);
  }

  @Test
  public void testDeleteMappingIgnoresMissingMapping() {
    when(repository.findByJcrPath(JCR_PATH)).thenReturn(Optional.empty());

    service.deleteMapping(JCR_PATH);

    verify(repository, never()).delete(any(WebDavPathMappingEntity.class));
  }

  @Test
  @SneakyThrows
  public void testGetOrCreateWebDavPathReturnsIdentityRootForIdentityBaseNode() {
    String result = service.getOrCreateWebDavPath(IDENTITY_ID,
                                                  IDENTITY_BASE_JCR_PATH,
                                                  IDENTITY_ROOT_WEBDAV_PATH,
                                                  identityRootNode,
                                                  "Private");

    assertEquals(IDENTITY_ROOT_WEBDAV_PATH, result);
    verify(repository, never()).save(any(WebDavPathMappingEntity.class));
  }

  @Test
  @SneakyThrows
  public void testGetOrCreateWebDavPathReturnsExistingMappingByNodeIdentifier() {
    WebDavPathMappingEntity existing = new WebDavPathMappingEntity();
    existing.setWebDavPath(PARENT_WEBDAV_PATH + "/Existing.docx");

    when(repository.findByNodeIdentifier(NODE_ID)).thenReturn(Optional.of(existing));

    String result = service.getOrCreateWebDavPath(IDENTITY_ID,
                                                  IDENTITY_BASE_JCR_PATH,
                                                  IDENTITY_ROOT_WEBDAV_PATH,
                                                  node,
                                                  "Ignored.docx");

    assertEquals(PARENT_WEBDAV_PATH + "/Existing.docx", result);
    verify(repository, never()).save(any(WebDavPathMappingEntity.class));
  }

  @Test
  @SneakyThrows
  public void testGetOrCreateWebDavPathReturnsExistingMappingByJcrPathWhenNoIdentifierMappingExists() {
    WebDavPathMappingEntity existing = new WebDavPathMappingEntity();
    existing.setWebDavPath(PARENT_WEBDAV_PATH + "/ByJcr.docx");

    when(repository.findByNodeIdentifier(NODE_ID)).thenReturn(Optional.empty());
    when(repository.findByJcrPath(JCR_PATH)).thenReturn(Optional.of(existing));

    String result = service.getOrCreateWebDavPath(IDENTITY_ID,
                                                  IDENTITY_BASE_JCR_PATH,
                                                  IDENTITY_ROOT_WEBDAV_PATH,
                                                  node,
                                                  "Ignored.docx");

    assertEquals(PARENT_WEBDAV_PATH + "/ByJcr.docx", result);
    verify(repository, never()).save(any(WebDavPathMappingEntity.class));
  }

  @Test
  @SneakyThrows
  public void testGetOrCreateWebDavPathCreatesParentAndChildMappingsWithVisibleNames() {
    when(repository.findByNodeIdentifier(anyString())).thenReturn(Optional.empty());
    when(repository.findByJcrPath(anyString())).thenReturn(Optional.empty());
    when(repository.findByParentJcrPathAndNormalizedVisibleName(anyString(), anyString())).thenReturn(Optional.empty());

    when(parentNode.hasProperty(EXO_TITLE)).thenReturn(true);
    when(parentNode.getProperty(EXO_TITLE)).thenReturn(property);
    when(property.getString()).thenReturn("Documents Partagés");

    String result = service.getOrCreateWebDavPath(IDENTITY_ID,
                                                  IDENTITY_BASE_JCR_PATH,
                                                  IDENTITY_ROOT_WEBDAV_PATH,
                                                  node,
                                                  NODE_TITLE_WITH_ACCENT);

    assertEquals(IDENTITY_ROOT_WEBDAV_PATH + "/Documents%20Partag%C3%A9s/Rapport%20%C3%89quipe.docx", result);

    ArgumentCaptor<WebDavPathMappingEntity> captor = ArgumentCaptor.forClass(WebDavPathMappingEntity.class);
    verify(repository, org.mockito.Mockito.times(2)).save(captor.capture());

    WebDavPathMappingEntity parentMapping = captor.getAllValues().get(0);
    WebDavPathMappingEntity childMapping = captor.getAllValues().get(1);

    assertEquals("Documents Partagés", parentMapping.getVisibleName());
    assertEquals(IDENTITY_ROOT_WEBDAV_PATH + "/Documents%20Partag%C3%A9s", parentMapping.getWebDavPath());

    assertEquals(NODE_TITLE_WITH_ACCENT, childMapping.getVisibleName());
    assertEquals(result, childMapping.getWebDavPath());
    assertEquals(PARENT_JCR_PATH, childMapping.getParentJcrPath());
    assertEquals(JCR_PATH, childMapping.getJcrPath());
  }

  @Test
  @SneakyThrows
  public void testGetOrCreateWebDavPathResolvesVisibleNameCollision() {
    WebDavPathMappingEntity collision = new WebDavPathMappingEntity();
    collision.setJcrPath(PARENT_JCR_PATH + "/other.docx");

    when(repository.findByNodeIdentifier(anyString())).thenReturn(Optional.empty());
    when(repository.findByJcrPath(anyString())).thenReturn(Optional.empty());
    when(repository.findByParentJcrPathAndNormalizedVisibleName(eq(IDENTITY_BASE_JCR_PATH), anyString()))
                                                                                                         .thenReturn(Optional.empty());
    when(repository.findByParentJcrPathAndNormalizedVisibleName(PARENT_JCR_PATH, "rapport.docx"))
                                                                                                 .thenReturn(Optional.of(collision));
    when(repository.findByParentJcrPathAndNormalizedVisibleName(PARENT_JCR_PATH, "rapport (1).docx"))
                                                                                                     .thenReturn(Optional.empty());

    String result = service.getOrCreateWebDavPath(IDENTITY_ID,
                                                  IDENTITY_BASE_JCR_PATH,
                                                  IDENTITY_ROOT_WEBDAV_PATH,
                                                  node,
                                                  NODE_TITLE_OTHER);

    assertEquals(PARENT_WEBDAV_PATH + "/Rapport%20%281%29.docx", result);

    ArgumentCaptor<WebDavPathMappingEntity> captor = ArgumentCaptor.forClass(WebDavPathMappingEntity.class);
    verify(repository, org.mockito.Mockito.times(2)).save(captor.capture());

    WebDavPathMappingEntity childMapping = captor.getAllValues().get(1);
    assertEquals("Rapport (1).docx", childMapping.getVisibleName());
    assertTrue(childMapping.isCollisionResolved());
  }
}
