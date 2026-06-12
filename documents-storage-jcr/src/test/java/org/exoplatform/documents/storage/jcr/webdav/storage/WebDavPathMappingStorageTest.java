/**
 * Copyright (C) 2026 eXo Platform SAS
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
package org.exoplatform.documents.storage.jcr.webdav.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import org.exoplatform.documents.storage.jcr.webdav.dao.WebDavPathMappingDao;
import org.exoplatform.documents.storage.jcr.webdav.entity.WebDavPathMappingEntity;

@RunWith(MockitoJUnitRunner.class)
public class WebDavPathMappingStorageTest {

  private static final String      FILE_NAME       = "Rapport.docx";

  private static final String      PARENT_JCR_PATH = "/Users/john/Private/Documents";  // NOSONAR

  private static final String      JCR_PATH        = PARENT_JCR_PATH + "/rapport.docx";

  private static final String      NODE_ID         = "node-123";

  @Mock
  private WebDavPathMappingDao     dao;

  @InjectMocks
  private WebDavPathMappingStorage storage;

  private WebDavPathMappingEntity  entity;

  @Before
  public void setUp() {
    entity = new WebDavPathMappingEntity();
    entity.setJcrPath(JCR_PATH);
    entity.setNodeIdentifier(NODE_ID);
    entity.setVisibleName(FILE_NAME);
  }

  @Test
  public void testFindByJcrPathDelegatesToDao() {
    when(dao.findByJcrPath(JCR_PATH)).thenReturn(Optional.of(entity));

    Optional<WebDavPathMappingEntity> result = storage.findByJcrPath(JCR_PATH);

    assertTrue(result.isPresent());
    assertEquals(entity, result.get());
  }

  @Test
  public void testFindByJcrPathReturnsEmptyForBlankPath() {
    Optional<WebDavPathMappingEntity> result = storage.findByJcrPath(" ");

    assertFalse(result.isPresent());
    verify(dao, never()).findByJcrPath(any());
  }

  @Test
  public void testFindByNodeIdentifierDelegatesToDao() {
    when(dao.findByNodeIdentifier(NODE_ID)).thenReturn(Optional.of(entity));

    Optional<WebDavPathMappingEntity> result = storage.findByNodeIdentifier(NODE_ID);

    assertTrue(result.isPresent());
    assertEquals(entity, result.get());
  }

  @Test
  public void testFindJcrPathReturnsMappedPath() {
    when(dao.findByParentJcrPathAndNormalizedVisibleName(PARENT_JCR_PATH, FILE_NAME))
                                                                                     .thenReturn(Optional.of(entity));

    String result = storage.findJcrPath(PARENT_JCR_PATH, FILE_NAME);

    assertEquals(JCR_PATH, result);
  }

  @Test
  public void testFindJcrPathReturnsNullWhenNoMappingExists() {
    when(dao.findByParentJcrPathAndNormalizedVisibleName(PARENT_JCR_PATH, "missing.docx"))
                                                                                          .thenReturn(Optional.empty());

    String result = storage.findJcrPath(PARENT_JCR_PATH, "missing.docx");

    assertEquals(null, result);
  }

  @Test
  public void testFindByParentJcrPathReturnsEmptyListForBlankPath() {
    List<WebDavPathMappingEntity> result = storage.findByParentJcrPath(null);

    assertTrue(result.isEmpty());
    verify(dao, never()).findByParentJcrPath(any());
  }

  @Test
  public void testSaveDelegatesToDao() {
    when(dao.save(entity)).thenReturn(entity);

    WebDavPathMappingEntity result = storage.save(entity);

    assertEquals(entity, result);
    verify(dao).save(entity);
  }

  @Test
  public void testDeleteMappingDeletesExistingMapping() {
    when(dao.findByJcrPath(JCR_PATH)).thenReturn(Optional.of(entity));

    storage.deleteMapping(JCR_PATH);

    verify(dao).delete(entity);
  }

  @Test
  public void testDeleteMappingIgnoresMissingMapping() {
    when(dao.findByJcrPath(JCR_PATH)).thenReturn(Optional.empty());

    storage.deleteMapping(JCR_PATH);

    verify(dao, never()).delete(any(WebDavPathMappingEntity.class));
  }

  @Test
  public void testDeleteByIdDelegatesWhenIdNotBlank() {
    storage.deleteById("mapping-id");

    verify(dao).deleteById("mapping-id");
  }

  @Test
  public void testDeleteByIdIgnoresBlankId() {
    storage.deleteById(" ");

    verify(dao, never()).deleteById(any());
  }
}
