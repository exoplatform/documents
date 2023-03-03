package org.exoplatform.documents.listener;

import static org.mockito.Mockito.*;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.Value;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import org.exoplatform.commons.api.notification.NotificationContext;
import org.exoplatform.commons.api.notification.channel.ChannelManager;
import org.exoplatform.commons.api.notification.command.NotificationCommand;
import org.exoplatform.commons.api.notification.command.NotificationExecutor;
import org.exoplatform.commons.api.notification.model.PluginKey;
import org.exoplatform.commons.api.notification.service.NotificationCompletionService;
import org.exoplatform.commons.api.notification.service.setting.PluginSettingService;
import org.exoplatform.commons.api.notification.service.storage.NotificationService;
import org.exoplatform.commons.notification.impl.NotificationContextImpl;
import org.exoplatform.commons.notification.impl.setting.NotificationPluginContainer;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.documents.notification.plugin.AddDocumentCollaboratorPlugin;
import org.exoplatform.services.jcr.impl.core.NodeImpl;
import org.exoplatform.services.listener.Event;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.provider.SpaceIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.service.LinkProvider;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;

@RunWith(MockitoJUnitRunner.class)
public class ShareDocumentNotificationListenerTest {

  private static final MockedStatic<CommonsUtils>            COMMONS_UTILS             = mockStatic(CommonsUtils.class);

  private static final MockedStatic<ConversationState>       CONVERSATION_STATE        = mockStatic(ConversationState.class);

  private static final MockedStatic<NotificationContextImpl> NOTIFICATION_CONTEXT_IMPL =
                                                                                       mockStatic(NotificationContextImpl.class);

  private static final MockedStatic<PluginKey>               PLUGIN_KEY                = mockStatic(PluginKey.class);

  private static final MockedStatic<LinkProvider>            LINK_PROVIDER             = mockStatic(LinkProvider.class);

  @Mock
  private NotificationService                                notificationService;

  @Mock
  private NotificationCompletionService                      notificationCompletionService;

  @Mock
  private NotificationPluginContainer                        notificationPluginContainer;

  @Mock
  private PluginSettingService                               pluginSettingService;

  @Mock
  private ChannelManager                                     channelManager;

  @Mock
  private SpaceService                                       spaceService;

  @Mock
  private IdentityManager                                    identityManager;

  @Mock
  private NodeImpl                                           nodeImpl;

  private ShareDocumentNotificationListener                  shareDocumentNotificationListener;

  @AfterClass
  public static void afterRunBare() throws Exception { // NOSONAR
    COMMONS_UTILS.close();
    CONVERSATION_STATE.close();
    NOTIFICATION_CONTEXT_IMPL.close();
    PLUGIN_KEY.close();
    LINK_PROVIDER.close();
  }

  @Before
  public void setUp() throws Exception {
    this.shareDocumentNotificationListener = new ShareDocumentNotificationListener(spaceService, identityManager);
    ConversationState conversationState = mock(ConversationState.class);
    CONVERSATION_STATE.when(() -> ConversationState.getCurrent()).thenReturn(conversationState);
    org.exoplatform.services.security.Identity identity = Mockito.mock(org.exoplatform.services.security.Identity.class);
    when(conversationState.getIdentity()).thenReturn(identity);
    when(identity.getUserId()).thenReturn("username");
    COMMONS_UTILS.when(() -> CommonsUtils.getService(NotificationService.class)).thenReturn(notificationService);
    COMMONS_UTILS.when(() -> CommonsUtils.getService(NotificationCompletionService.class))
                 .thenReturn(notificationCompletionService);
    COMMONS_UTILS.when(() -> CommonsUtils.getService(NotificationPluginContainer.class)).thenReturn(notificationPluginContainer);
    COMMONS_UTILS.when(() -> CommonsUtils.getService(PluginSettingService.class)).thenReturn(pluginSettingService);
    COMMONS_UTILS.when(() -> CommonsUtils.getService(ChannelManager.class)).thenReturn(channelManager);
    COMMONS_UTILS.when(() -> CommonsUtils.getService(NodeImpl.class)).thenReturn(nodeImpl);
    COMMONS_UTILS.when(() -> CommonsUtils.getCurrentPortalOwner()).thenReturn("dw");
    COMMONS_UTILS.when(() -> CommonsUtils.getCurrentDomain()).thenReturn("http://domain/");
    LINK_PROVIDER.when(() -> LinkProvider.getPortalName(null)).thenReturn("portal");
    PluginKey pluginKey = mock(PluginKey.class);
    PLUGIN_KEY.when(() -> PluginKey.key(AddDocumentCollaboratorPlugin.ID)).thenReturn(pluginKey);
  }

  @Test
  public void onEvent() throws Exception {
    Space space = new Space();
    space.setGroupId("/spaces/spacename");
    when(spaceService.getSpaceByPrettyName("space_name")).thenReturn(space);
    Node targetNode = mock(NodeImpl.class);
    Identity targetIdentity = mock(Identity.class);
    Event<Identity, Node> event = new Event<>("share_document_event", targetIdentity, targetNode);
    when(targetIdentity.getProviderId()).thenReturn("USER");
    Property property = mock(Property.class);
    when(targetNode.getProperty("exo:uuid")).thenReturn(property);
    when(((NodeImpl) targetNode).getIdentifier()).thenReturn("313445hegefezd");
    Property propertyTitle = mock(Property.class);
    Value value = mock(Value.class);
    when(propertyTitle.getValue()).thenReturn(value);
    when(value.getString()).thenReturn("document");
    when(targetNode.hasProperty("exo:title")).thenReturn(true);
    when(targetNode.getProperty("exo:title")).thenReturn(propertyTitle);
    when(targetIdentity.getRemoteId()).thenReturn("user");
    when(targetNode.hasProperty("exo:uuid")).thenReturn(true);

    NotificationContext notificationContext = mock(NotificationContext.class);
    NotificationExecutor notificationExecutor = mock(NotificationExecutor.class);
    NotificationCommand notificationCommand = mock(NotificationCommand.class);
    when(notificationContext.getNotificationExecutor()).thenReturn(notificationExecutor);
    when(notificationContext.makeCommand(any())).thenReturn(notificationCommand);
    when(notificationExecutor.with(notificationCommand)).thenReturn(notificationExecutor);
    NOTIFICATION_CONTEXT_IMPL.when(() -> NotificationContextImpl.cloneInstance()).thenReturn(notificationContext);

    shareDocumentNotificationListener.onEvent(event);
    verify(notificationExecutor, times(1)).execute(notificationContext);
    when(targetIdentity.getRemoteId()).thenReturn("space_name");
    when(targetIdentity.getProviderId()).thenReturn(SpaceIdentityProvider.NAME);
    shareDocumentNotificationListener.onEvent(event);
    verify(notificationExecutor, times(2)).execute(notificationContext);
  }
}
