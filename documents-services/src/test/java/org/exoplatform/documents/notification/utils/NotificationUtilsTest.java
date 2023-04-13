package org.exoplatform.documents.notification.utils;


import static org.exoplatform.documents.notification.utils.NotificationUtils.EXO_SYMLINK_UUID;
import static org.exoplatform.documents.notification.utils.NotificationUtils.NT_FILE;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


import org.exoplatform.services.jcr.impl.core.SessionImpl;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;

import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.documents.rest.util.EntityBuilder;
import org.exoplatform.services.jcr.core.ExtendedNode;
import org.exoplatform.services.jcr.impl.core.NodeImpl;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.service.LinkProvider;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Value;


@RunWith(PowerMockRunner.class)
@PowerMockIgnore({ "javax.management.*" })
@PrepareForTest({ CommonsUtils.class, LinkProvider.class, EntityBuilder.class })
public class NotificationUtilsTest {

  @Mock
  private IdentityManager identityManager;

  @Mock
  private SpaceService    spaceService;

  @Before
  public void setUp() throws Exception {
    PowerMockito.mockStatic(CommonsUtils.class);
    PowerMockito.mockStatic(LinkProvider.class);

    when(CommonsUtils.getCurrentPortalOwner()).thenReturn("dw");
    when(CommonsUtils.getCurrentDomain()).thenReturn("http://domain");
    when(LinkProvider.getPortalName(null)).thenReturn("portal");
  }

  @Test
  public void getDocumentLink() throws RepositoryException {
    Identity identity = mock(Identity.class);
    Space space = new Space();
    space.setGroupId("/spaces/spacex");
    space.setPrettyName("spacex");
    when(identity.getRemoteId()).thenReturn("spacex");
    Node node = Mockito.mock(ExtendedNode.class);
    when(((ExtendedNode)node).getIdentifier()).thenReturn("123");
    when(node.hasNode("jcr:content")).thenReturn(true);
    when(node.getPath()).thenReturn("/Groups/spaces/spacex/Documents/new folder 32");
    when(spaceService.getSpaceByGroupId("/spaces/spacex")).thenReturn(space);
    when(spaceService.getSpaceByPrettyName("spacex")).thenReturn(space);
    when( identityManager.getOrCreateSpaceIdentity("spacex")).thenReturn(identity);

    String link = NotificationUtils.getDocumentLink(node, spaceService,identityManager);
    assertEquals("http://domain/portal/g/:spaces:spacex/spacex/documents?documentPreviewId=123", link);
  }
  @Test
  public void getSharedDocumentLink() throws RepositoryException {
    Space space = new Space();
    space.setGroupId("/spaces/spacename");
    when(spaceService.getSpaceByPrettyName("space_name")).thenReturn(space);
    SessionImpl session = Mockito.mock(SessionImpl.class);
    Node node = Mockito.mock(NodeImpl.class);
    Node targetNode = Mockito.mock(NodeImpl.class);
    Property property = Mockito.mock(Property.class);
    when(((NodeImpl) node).getIdentifier()).thenReturn("123");
    when(node.getSession()).thenReturn(session);
    when(node.getProperty(EXO_SYMLINK_UUID)).thenReturn(property);
    when(property.getString()).thenReturn("id123");
    when(session.getNodeByUUID(anyString())).thenReturn(targetNode);
    when(targetNode.isNodeType(NT_FILE)).thenReturn(true);
    String link = NotificationUtils.getSharedDocumentLink(node, null,null);
    assertEquals("http://domain/portal/dw/documents?documentPreviewId=123", link);
    link = NotificationUtils.getSharedDocumentLink(node, spaceService,"space_name");
    assertEquals("http://domain/portal/g/:spaces:spacename/space_name/documents?documentPreviewId=123", link);
    when(targetNode.isNodeType(NT_FILE)).thenReturn(false);
    link = NotificationUtils.getSharedDocumentLink(node, null,null);
    assertEquals("http://domain/portal/dw/documents?folderId=123", link);
    link = NotificationUtils.getSharedDocumentLink(node, spaceService,"space_name");
    assertEquals("http://domain/portal/g/:spaces:spacename/space_name/documents?folderId=123", link);
  }

  @Test
  public void getDocumentTitle() throws RepositoryException {
    Node node = mock(Node.class);
    Node contentNode = mock(Node.class);
    Property exoTitleProperty = mock(Property.class);
    Property dcTitleProperty = mock(Property.class);
    Value exoTitle = mock(Value.class);
    Value dcTitle = mock(Value.class);
    when(node.getName()).thenReturn("nodeName");
    when(node.getProperty("exo:title")).thenReturn(exoTitleProperty);
    when(exoTitleProperty.getValue()).thenReturn(exoTitle);
    when(exoTitle.getString()).thenReturn("exoTitle");
    when(contentNode.getProperty("dc:title")).thenReturn(dcTitleProperty);
    when(dcTitleProperty.getValue()).thenReturn(dcTitle);
    when(dcTitle.getString()).thenReturn("dcTitle");
    when(node.hasProperty("exo:title")).thenReturn(true);
    when(contentNode.hasProperty("dc:title")).thenReturn(true);
    when(node.hasNode("jcr:content")).thenReturn(true);
    when(node.getNode("jcr:content")).thenReturn(contentNode);
    String title = NotificationUtils.getDocumentTitle(node);
    assertEquals("exoTitle", title);
    when(exoTitle.getString()).thenReturn(null);
    String title1 = NotificationUtils.getDocumentTitle(node);
    assertEquals("dcTitle", title1);
    when(dcTitle.getString()).thenReturn(null);
    String title2 = NotificationUtils.getDocumentTitle(node);
    assertEquals("nodeName", title2);
  }

  @Test
  public void getUserProfile() {
    Identity identity = mock(Identity.class);
    Profile profile = mock(Profile.class);
    when(identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, "user")).thenReturn(identity);
    when(identity.getProfile()).thenReturn(profile);
    Profile userProfile = NotificationUtils.getUserProfile(identityManager, "user");
    assertEquals(profile, userProfile);
  }
}
