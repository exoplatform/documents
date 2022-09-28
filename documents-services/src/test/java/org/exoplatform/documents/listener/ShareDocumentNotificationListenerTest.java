package org.exoplatform.documents.listener;

import org.exoplatform.commons.api.notification.channel.ChannelManager;
import org.exoplatform.commons.api.notification.model.PluginKey;
import org.exoplatform.commons.api.notification.service.NotificationCompletionService;
import org.exoplatform.commons.api.notification.service.setting.PluginSettingService;
import org.exoplatform.commons.api.notification.service.storage.NotificationService;
import org.exoplatform.commons.notification.impl.NotificationContextImpl;
import org.exoplatform.commons.notification.impl.setting.NotificationPluginContainer;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.documents.notification.plugin.AddDocumentCollaboratorPlugin;
import org.exoplatform.services.listener.Event;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.provider.SpaceIdentityProvider;
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
import javax.jcr.Value;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.verifyStatic;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore({ "javax.management.*" })
@PrepareForTest({CommonsUtils.class, ConversationState.class, NotificationContextImpl.class, PluginKey.class, LinkProvider.class})
public class ShareDocumentNotificationListenerTest {


  @Mock
  private NotificationService               notificationService;

  @Mock
  private NotificationCompletionService     notificationCompletionService;

  @Mock
  private NotificationPluginContainer       notificationPluginContainer;

  @Mock
  private PluginSettingService              pluginSettingService;

  @Mock
  private ChannelManager                    channelManager;
  @Mock
  private SpaceService                      spaceService;
  
  @Mock
  private IdentityManager                   identityManager;

  private ShareDocumentNotificationListener shareDocumentNotificationListener;

  @Before
  public void setUp() throws Exception {
    this.shareDocumentNotificationListener = new ShareDocumentNotificationListener(spaceService, identityManager);
    PowerMockito.mockStatic(ConversationState.class);
    PowerMockito.mockStatic(CommonsUtils.class);
    PowerMockito.mockStatic(LinkProvider.class);
    PowerMockito.mockStatic(PluginKey.class);
    ConversationState conversationState = mock(ConversationState.class);
    when(ConversationState.getCurrent()).thenReturn(conversationState);
    org.exoplatform.services.security.Identity identity = Mockito.mock(org.exoplatform.services.security.Identity.class);
    when(conversationState.getIdentity()).thenReturn(identity);
    when(identity.getUserId()).thenReturn("username");
    when(CommonsUtils.getService(NotificationService.class)).thenReturn(notificationService);
    when(CommonsUtils.getService(NotificationCompletionService.class)).thenReturn(notificationCompletionService);
    when(CommonsUtils.getService(NotificationPluginContainer.class)).thenReturn(notificationPluginContainer);
    when(CommonsUtils.getService(PluginSettingService.class)).thenReturn(pluginSettingService);
    when(CommonsUtils.getService(ChannelManager.class)).thenReturn(channelManager);
    when(CommonsUtils.getCurrentPortalOwner()).thenReturn("dw");
    when(CommonsUtils.getCurrentDomain()).thenReturn("http://domain/");
    when(LinkProvider.getPortalName(null)).thenReturn("portal");
    PluginKey pluginKey = mock(PluginKey.class);
    when(PluginKey.key(AddDocumentCollaboratorPlugin.ID)).thenReturn(pluginKey);
  }

  @Test
  public void onEvent() throws Exception {
    Space space = new Space();
    space.setGroupId("/spaces/spacename");
    when(spaceService.getSpaceByPrettyName("space_name")).thenReturn(space);
    Node targetNode = mock(Node.class);
    Identity targetIdentity = mock(Identity.class);
    Event<Identity, Node> event = new Event<>("share_document_event", targetIdentity, targetNode);
    when(targetIdentity.getProviderId()).thenReturn("USER");
    Property property = mock(Property.class);
    when(targetNode.getProperty("exo:uuid")).thenReturn(property);
    when(property.getString()).thenReturn("313445hegefezd");
    Property propertyTitle = mock(Property.class);
    Value value = mock(Value.class);
    when(propertyTitle.getValue()).thenReturn(value);
    when(value.getString()).thenReturn("document");
    when(targetNode.hasProperty("exo:title")).thenReturn(true);
    when(targetNode.getProperty("exo:title")).thenReturn(propertyTitle);
    when(targetIdentity.getRemoteId()).thenReturn("user");
    when(targetNode.hasProperty("exo:uuid")).thenReturn(true);
    shareDocumentNotificationListener.onEvent(event);
    verifyStatic(PluginKey.class, times(1));
    PluginKey.key(AddDocumentCollaboratorPlugin.ID);
    when(targetIdentity.getRemoteId()).thenReturn("space_name");
    when(targetIdentity.getProviderId()).thenReturn(SpaceIdentityProvider.NAME);
    shareDocumentNotificationListener.onEvent(event);
    verifyStatic(PluginKey.class, times(2));
    PluginKey.key(AddDocumentCollaboratorPlugin.ID);
  }
}
