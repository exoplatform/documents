package org.exoplatform.documents.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.exoplatform.container.PortalContainer;
import org.exoplatform.documents.model.AbstractNode;
import org.exoplatform.documents.service.DocumentFileService;
import org.exoplatform.portal.config.UserACL;
import org.exoplatform.services.security.Identity;

import io.meeds.social.category.service.CategoryLinkService;
import io.meeds.social.category.service.CategoryPluginService;

import jakarta.annotation.PostConstruct;

@ExtendWith(MockitoExtension.class)
class DocumentCategoryPluginTest {

  private static final String    USERNAME    = "john";

  private static final String    DOCUMENT_ID = "doc-1";

  @Mock
  private PortalContainer        container;

  @Mock
  private CategoryPluginService  categoryPluginService;

  @Mock
  private DocumentFileService    documentFileService;

  @Mock
  private UserACL                userAcl;

  @Mock
  private Identity               userIdentity;

  @InjectMocks
  private DocumentCategoryPlugin documentCategoryPlugin;

  @Test
  void initRegistersPluginIntoCategoryPluginService() {
    when(container.getComponentInstanceOfType(CategoryPluginService.class)).thenReturn(categoryPluginService);

    documentCategoryPlugin.init();

    verify(categoryPluginService).addPlugin(documentCategoryPlugin);
  }

  @Test
  void initIsAnnotatedPostConstruct() throws NoSuchMethodException {
    assertTrue(DocumentCategoryPlugin.class.getMethod("init").isAnnotationPresent(PostConstruct.class));
  }

  @Test
  void typeIsDocument() {
    assertEquals("document", documentCategoryPlugin.getType());
  }

  @Test
  void getCategoryIdsDelegatesToSpaceScopedDocumentQuery() {
    when(documentFileService.getDocumentCategoryIds(12L, USERNAME)).thenReturn(List.of(3L, 5L));

    assertEquals(List.of(3L, 5L), documentCategoryPlugin.getCategoryIds(12L, USERNAME));
  }

  @Test
  void getCategoryIdsWithoutSpaceFallsBackToPlatformWideLinkedIds() {
    CategoryLinkService categoryLinkService = mock(CategoryLinkService.class);
    when(container.getComponentInstanceOfType(CategoryLinkService.class)).thenReturn(categoryLinkService);
    when(categoryLinkService.getLinkedIds("document")).thenReturn(List.of(9L));

    assertEquals(List.of(9L), documentCategoryPlugin.getCategoryIds(0L, USERNAME));
    assertEquals(List.of(9L), documentCategoryPlugin.getCategoryIds(-1L, USERNAME));
    verifyNoInteractions(documentFileService);
  }

  @Test
  void canAccessReturnsFalseForNonexistentDocument() {
    when(documentFileService.getDocumentById(DOCUMENT_ID)).thenReturn(null);

    assertFalse(documentCategoryPlugin.canAccess(DOCUMENT_ID, USERNAME));
    verifyNoInteractions(userAcl);
  }

  @Test
  void canAccessReturnsTrueForAccessibleDocument() {
    when(documentFileService.getDocumentById(DOCUMENT_ID)).thenReturn(mock(AbstractNode.class));
    when(userAcl.getUserIdentity(USERNAME)).thenReturn(userIdentity);
    when(userAcl.hasAccessPermission("document", DOCUMENT_ID, userIdentity)).thenReturn(true);

    assertTrue(documentCategoryPlugin.canAccess(DOCUMENT_ID, USERNAME));
  }

  @Test
  void canAccessReturnsTrueForEditorWithoutAccessPermission() {
    when(documentFileService.getDocumentById(DOCUMENT_ID)).thenReturn(mock(AbstractNode.class));
    when(userAcl.getUserIdentity(USERNAME)).thenReturn(userIdentity);
    when(userAcl.hasAccessPermission("document", DOCUMENT_ID, userIdentity)).thenReturn(false);
    when(userAcl.hasEditPermission("document", DOCUMENT_ID, userIdentity)).thenReturn(true);

    assertTrue(documentCategoryPlugin.canAccess(DOCUMENT_ID, USERNAME));
  }

  @Test
  void canAccessReturnsFalseForInaccessibleDocument() {
    when(documentFileService.getDocumentById(DOCUMENT_ID)).thenReturn(mock(AbstractNode.class));
    when(userAcl.getUserIdentity(USERNAME)).thenReturn(userIdentity);
    when(userAcl.hasAccessPermission("document", DOCUMENT_ID, userIdentity)).thenReturn(false);
    when(userAcl.hasEditPermission("document", DOCUMENT_ID, userIdentity)).thenReturn(false);

    assertFalse(documentCategoryPlugin.canAccess(DOCUMENT_ID, USERNAME));
  }

  @Test
  void canEditDelegatesToUserAclEditPermission() {
    when(userAcl.getUserIdentity(USERNAME)).thenReturn(userIdentity);
    when(userAcl.hasEditPermission("document", DOCUMENT_ID, userIdentity)).thenReturn(true);

    assertTrue(documentCategoryPlugin.canEdit(DOCUMENT_ID, USERNAME));
  }
}
