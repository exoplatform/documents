package org.exoplatform.documents.notification.plugin;

import org.exoplatform.commons.api.notification.NotificationContext;
import org.exoplatform.commons.api.notification.model.NotificationInfo;
import org.exoplatform.commons.api.notification.model.PluginKey;
import org.exoplatform.commons.notification.impl.NotificationContextImpl;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.documents.notification.utils.NotificationConstants;
import org.exoplatform.services.idgenerator.IDGeneratorService;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class AddDocumentCollaboratorPluginTest {

  private static final MockedStatic<ExoContainerContext> EXOCONTAINER_CONTEXT = mockStatic(ExoContainerContext.class);

  private static final MockedStatic<CommonsUtils>        COMMONS_UTILS        = mockStatic(CommonsUtils.class);

  private static final MockedStatic<PluginKey>           PLUGIN_KEY           = mockStatic(PluginKey.class);

  @Mock
  private InitParams                                     initParams;

  @Mock
  private SpaceService                                   spaceService;

  private AddDocumentCollaboratorPlugin                  addDocumentCollaboratorPlugin;

  @AfterClass
  public static void afterRunBare() throws Exception { // NOSONAR
    EXOCONTAINER_CONTEXT.close();
    COMMONS_UTILS.close();
    PLUGIN_KEY.close();
  }

  @Before
  public void setUp() throws Exception {
    this.addDocumentCollaboratorPlugin = new AddDocumentCollaboratorPlugin(initParams, spaceService);
    EXOCONTAINER_CONTEXT.when(() -> ExoContainerContext.getService(IDGeneratorService.class)).thenReturn(null);
  }

  @Test
  public void isValid() {
    NotificationContext ctx = NotificationContextImpl.cloneInstance();
    assertTrue(addDocumentCollaboratorPlugin.isValid(ctx));
  }

  @Test
  public void makeNotification() {
    NotificationContext ctx = NotificationContextImpl.cloneInstance();
    ctx.append(NotificationConstants.FROM_USER, "root");
    ctx.append(NotificationConstants.DOCUMENT_NAME, "document");
    ctx.append(NotificationConstants.DOCUMENT_URL, "document url");
    ctx.append(NotificationConstants.RECEIVERS, "receiver");

    String[] members = {
        "user1", "user2"
    };

    Space space = mock(Space.class);

    when(spaceService.getSpaceByPrettyName("receiver")).thenReturn(space);
    when(space.getMembers()).thenReturn(members);
    NotificationInfo notificationInfo = addDocumentCollaboratorPlugin.makeNotification(ctx);
    assertEquals("root", notificationInfo.getValueOwnerParameter(NotificationConstants.FROM_USER.getKey()));
    assertEquals("document", notificationInfo.getValueOwnerParameter(NotificationConstants.DOCUMENT_NAME.getKey()));
    assertEquals("document url", notificationInfo.getValueOwnerParameter(NotificationConstants.DOCUMENT_URL.getKey()));
    assertEquals("root", notificationInfo.getFrom());
    assertEquals(Arrays.asList(members), notificationInfo.getSendToUserIds());
    when(spaceService.getSpaceByPrettyName("receiver")).thenReturn(null);
    NotificationInfo notificationInfo1 = addDocumentCollaboratorPlugin.makeNotification(ctx);
    assertEquals(List.of("receiver"), notificationInfo1.getSendToUserIds());

  }
}
