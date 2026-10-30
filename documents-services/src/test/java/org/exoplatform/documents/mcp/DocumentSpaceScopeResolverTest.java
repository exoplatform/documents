/**
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
package org.exoplatform.documents.mcp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import org.exoplatform.documents.service.DocumentFileService;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;

/**
 * Pins what the document resolver tells a space scope: the space whose drive
 * holds the node, and nothing for any node it can't place there.
 */
public class DocumentSpaceScopeResolverTest {

  private static final String       NODE_ID = "0123456789abcdef0123456789abcdef";

  private DocumentFileService       documentFileService;

  private SpaceService              spaceService;

  private DocumentSpaceScopeResolver resolver;

  /**
   * Builds the resolver over mocked services.
   */
  @Before
  public void setUp() {
    documentFileService = mock(DocumentFileService.class);
    spaceService = mock(SpaceService.class);
    resolver = new DocumentSpaceScopeResolver(documentFileService, spaceService);
  }

  /**
   * The resolver is named as the tool definitions call it, and is a bean of
   * the profile the document tools live in, which is how the space scope finds
   * it. Mutants: the {@code @Service} annotation removed; the
   * {@code @Profile} annotation removed.
   */
  @Test
  public void nameIsTheOneTheToolDefinitionsGiveAndTheBeanIsWired() {
    assertEquals("document", resolver.getName());
    assertTrue(DocumentSpaceScopeResolver.class.isAnnotationPresent(Service.class));
    assertEquals("mcp-server", DocumentSpaceScopeResolver.class.getAnnotation(Profile.class).value()[0]);
    assertEquals(DocumentMcpTool.class.getAnnotation(Profile.class).value()[0],
                 DocumentSpaceScopeResolver.class.getAnnotation(Profile.class).value()[0]);
  }

  /**
   * A node held by a space's drive gives that space's numeric id. Mutants:
   * the group id read from another node; the space id not converted.
   */
  @Test
  public void aNodeOfASpaceDriveGivesTheSpace() {
    when(documentFileService.getDocumentSpaceGroupId(NODE_ID)).thenReturn("/spaces/team");
    Space space = new Space();
    space.setId("12");
    when(spaceService.getSpaceByGroupId("/spaces/team")).thenReturn(space);

    assertEquals(Long.valueOf(12L), resolver.spaceOf(NODE_ID));
  }

  /**
   * A node the storage places in no space drive (a shortcut, a share link, a
   * personal drive, the trash, a missing node: the storage answers null for
   * each), a group with no space and a space with no id or no numeric id give none,
   * and a null group id never reaches the space service. Mutant: the null
   * group id handed to the space service.
   */
  @Test
  public void aNodeOfNoSpaceGivesNone() {
    when(documentFileService.getDocumentSpaceGroupId(NODE_ID)).thenReturn(null);
    assertNull(resolver.spaceOf(NODE_ID));
    verify(spaceService, never()).getSpaceByGroupId(any());

    when(documentFileService.getDocumentSpaceGroupId(NODE_ID)).thenReturn("/spaces/gone");
    when(spaceService.getSpaceByGroupId("/spaces/gone")).thenReturn(null);
    assertNull(resolver.spaceOf(NODE_ID));

    Space noId = new Space();
    when(documentFileService.getDocumentSpaceGroupId(NODE_ID)).thenReturn("/spaces/noid");
    when(spaceService.getSpaceByGroupId("/spaces/noid")).thenReturn(noId);
    assertNull(resolver.spaceOf(NODE_ID));
    Space notNumeric = mock(Space.class);
    when(notNumeric.getId()).thenReturn("abc");
    when(documentFileService.getDocumentSpaceGroupId(NODE_ID)).thenReturn("/spaces/abc");
    when(spaceService.getSpaceByGroupId("/spaces/abc")).thenReturn(notNumeric);
    assertNull(resolver.spaceOf(NODE_ID));
  }

  /**
   * A failing lookup, of the node or of the space, gives none rather than an
   * error. Mutant: the exception let through.
   */
  @Test
  public void aFailingLookupGivesNone() {
    when(documentFileService.getDocumentSpaceGroupId(NODE_ID)).thenThrow(new IllegalStateException("repository down"));
    assertNull(resolver.spaceOf(NODE_ID));

    doReturn("/spaces/team").when(documentFileService).getDocumentSpaceGroupId(NODE_ID);
    when(spaceService.getSpaceByGroupId("/spaces/team")).thenThrow(new IllegalStateException("database down"));
    assertNull(resolver.spaceOf(NODE_ID));
  }

  /**
   * An id that isn't 32 lowercase hexadecimal digits is never looked up, even
   * when the storage would place it in a space. Mutant: the id check removed.
   */
  @Test
  public void anIdThatIsNotANodeIdIsNotLookedUp() {
    when(documentFileService.getDocumentSpaceGroupId(anyString())).thenReturn("/spaces/team");
    Space space = new Space();
    space.setId("12");
    when(spaceService.getSpaceByGroupId("/spaces/team")).thenReturn(space);

    assertNull(resolver.spaceOf(null));
    assertNull(resolver.spaceOf(""));
    assertNull(resolver.spaceOf("7"));
    assertNull(resolver.spaceOf(NODE_ID.toUpperCase()));
    assertNull(resolver.spaceOf(NODE_ID + "0"));
    assertNull(resolver.spaceOf(" " + NODE_ID));
    assertNull(resolver.spaceOf(NODE_ID.substring(1)));
    assertNull(resolver.spaceOf(NODE_ID.substring(1) + "g"));
    verify(documentFileService, never()).getDocumentSpaceGroupId(anyString());
  }

}
