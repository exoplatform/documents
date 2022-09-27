package org.exoplatform.documents.notification.utils;


import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.documents.rest.util.EntityBuilder;
import org.exoplatform.services.jcr.core.ExtendedNode;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.service.LinkProvider;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Value;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
  public void getSharedDocumentLink() {
    Space space = new Space();
    space.setGroupId("/spaces/spacename");
    when(spaceService.getSpaceByPrettyName("space_name")).thenReturn(space);
    String link = NotificationUtils.getSharedDocumentLink("123", null,null);
    assertEquals("http://domain/portal/dw/documents/Private/Documents/Shared?documentPreviewId=123", link);
    String link1 = NotificationUtils.getSharedDocumentLink("123", spaceService,"space_name");
    assertEquals("http://domain/portal/g/:spaces:spacename/space_name/documents/Shared?documentPreviewId=123", link1);
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
