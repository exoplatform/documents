package org.exoplatform.documents.storage;

import org.exoplatform.documents.dao.PublicDocumentAccessDAO;
import org.exoplatform.documents.entity.PublicDocumentAccessEntity;
import org.exoplatform.documents.model.PublicDocumentAccess;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Date;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.Silent.class)
public class PublicDocumentAccessStorageImplTest {

  private PublicDocumentAccessDAO publicDocumentAccessDAO;

  private IdentityManager      identityManager;

  private PublicDocumentAccessStorage publicDocumentAccessStorage;

  @Before
  public void setUp() throws Exception {
    publicDocumentAccessDAO = mock(PublicDocumentAccessDAO.class);
    identityManager = mock(IdentityManager.class);
    publicDocumentAccessStorage = new PublicDocumentAccessStorageImpl(publicDocumentAccessDAO, identityManager);
  }

  @Test
  public void getTokenById() {
    PublicDocumentAccessEntity publicDocumentAccessEntity = new PublicDocumentAccessEntity();
    publicDocumentAccessEntity.setId(1L);
    publicDocumentAccessEntity.setNodeId("123");
    publicDocumentAccessEntity.setToken("token");
    publicDocumentAccessEntity.setHasPassword(true);
    publicDocumentAccessEntity.setExpirationDate(new Date());
    when(publicDocumentAccessDAO.getPublicDocumentAccessByNodeId("123")).thenReturn(publicDocumentAccessEntity);
    assertNotNull(publicDocumentAccessStorage.getPublicDocumentAccessByNodeId("123"));
  }

  @Test
  public void removeToken() {
    PublicDocumentAccessEntity publicDocumentAccessEntity = new PublicDocumentAccessEntity();
    when(publicDocumentAccessDAO.getPublicDocumentAccessByNodeId("123")).thenReturn(publicDocumentAccessEntity);
    publicDocumentAccessStorage.removePublicDocumentAccess("123");
    verify(publicDocumentAccessDAO, times(1)).delete(publicDocumentAccessEntity);
  }

  @Test
  public void saveToken() {
    PublicDocumentAccess publicDocumentAccess = new PublicDocumentAccess();
    publicDocumentAccess.setId(0L);
    when(identityManager.getIdentity("1")).thenReturn(null, mock(Identity.class));
    Throwable exception = assertThrows(IllegalArgumentException.class, () -> publicDocumentAccessStorage.savePublicDocumentAccess(null, 1L));
    assertEquals("documentToken argument is null", exception.getMessage());
    exception = assertThrows(IllegalArgumentException.class, () -> publicDocumentAccessStorage.savePublicDocumentAccess(publicDocumentAccess, 1L));
    assertEquals("identity is not exist", exception.getMessage());
    publicDocumentAccessStorage.savePublicDocumentAccess(publicDocumentAccess, 1L);
    verify(publicDocumentAccessDAO, times(1)).create(any());
    publicDocumentAccess.setId(1L);
    publicDocumentAccessStorage.savePublicDocumentAccess(publicDocumentAccess, 1L);
    verify(publicDocumentAccessDAO, times(1)).update(any());
  }
}
