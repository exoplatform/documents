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
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore({ "javax.management.*" })
@PrepareForTest({ CommonsUtils.class, PluginKey.class, CommonsUtils.class, ExoContainerContext.class })
public class AddDocumentCollaboratorPluginTest {

  @Mock
  private InitParams                    initParams;

  @Mock
  private SpaceService                  spaceService;

  private AddDocumentCollaboratorPlugin addDocumentCollaboratorPlugin;

  @Before
  public void setUp() throws Exception {
    this.addDocumentCollaboratorPlugin = new AddDocumentCollaboratorPlugin(initParams, spaceService);
    PowerMockito.mockStatic(CommonsUtils.class);
    PowerMockito.mockStatic(ExoContainerContext.class);
    when(ExoContainerContext.getService(IDGeneratorService.class)).thenReturn(null);
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

      String[] members = {"user1", "user2"};

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
