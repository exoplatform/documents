package org.exoplatform.documents.notification.plugin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.List;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;

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

@RunWith(MockitoJUnitRunner.class)
public class ImportDocumentsPluginTest {

  private static final MockedStatic<ExoContainerContext> EXOCONTAINER_CONTEXT = mockStatic(ExoContainerContext.class);

  private static final MockedStatic<CommonsUtils>        COMMONS_UTILS        = mockStatic(CommonsUtils.class);

  private static final MockedStatic<PluginKey>           PLUGIN_KEY           = mockStatic(PluginKey.class);

  @Mock
  private InitParams                                     initParams;

  @Mock
  private SpaceService                                   spaceService;

  private ImportDocumentsPlugin                  importDocumentsPlugin;

  @AfterClass
  public static void afterRunBare() throws Exception { // NOSONAR
    EXOCONTAINER_CONTEXT.close();
    COMMONS_UTILS.close();
    PLUGIN_KEY.close();
  }

  @Before
  public void setUp() throws Exception {
    this.importDocumentsPlugin = new ImportDocumentsPlugin(initParams, spaceService);
    EXOCONTAINER_CONTEXT.when(() -> ExoContainerContext.getService(IDGeneratorService.class)).thenReturn(null);
  }

  @Test
  public void isValid() {
    NotificationContext ctx = NotificationContextImpl.cloneInstance();
    assertTrue(importDocumentsPlugin.isValid(ctx));
  }

  @Test
  public void makeNotification() {
    NotificationContext ctx = NotificationContextImpl.cloneInstance();
    ctx.append(NotificationConstants.FROM_USER, "root");
    ctx.append(NotificationConstants.RECEIVERS, "receiver");
    ctx.append(NotificationConstants.FOLDER_URL, "folder url");
    ctx.append(NotificationConstants.FOLDER_NAME, "folder name");
    ctx.append(NotificationConstants.DURATION, "1211");
    ctx.append(NotificationConstants.TOTAL_NUMBER, "10");
    ctx.append(NotificationConstants.STATUS, "DONE_SUCCESSFULLY");
    ctx.append(NotificationConstants.FILES_CREATED, "7");
    ctx.append(NotificationConstants.FILES_DUPLICATED, "1");
    ctx.append(NotificationConstants.FILES_UPDATED, "1");
    ctx.append(NotificationConstants.FILES_IGNORED, "1");
    ctx.append(NotificationConstants.FILES_FAILED, "0");


    String[] members = {
        "user1", "user2"
    };

    Space space = mock(Space.class);

    when(spaceService.getSpaceByPrettyName("receiver")).thenReturn(space);
    when(space.getMembers()).thenReturn(members);
    NotificationInfo notificationInfo = importDocumentsPlugin.makeNotification(ctx);
    assertEquals("root", notificationInfo.getValueOwnerParameter(NotificationConstants.FROM_USER.getKey()));
    assertEquals("root", notificationInfo.getFrom());


    assertEquals("folder url", notificationInfo.getValueOwnerParameter(NotificationConstants.FOLDER_URL.getKey()));
    assertEquals("folder name", notificationInfo.getValueOwnerParameter(NotificationConstants.FOLDER_NAME.getKey()));
    assertEquals("1211", notificationInfo.getValueOwnerParameter(NotificationConstants.DURATION.getKey()));
    assertEquals("10", notificationInfo.getValueOwnerParameter(NotificationConstants.TOTAL_NUMBER.getKey()));
    assertEquals("10", notificationInfo.getValueOwnerParameter(NotificationConstants.TOTAL_NUMBER.getKey()));
    assertEquals("DONE_SUCCESSFULLY", notificationInfo.getValueOwnerParameter(NotificationConstants.STATUS.getKey()));
    assertEquals("7", notificationInfo.getValueOwnerParameter(NotificationConstants.FILES_CREATED.getKey()));
    assertEquals("1", notificationInfo.getValueOwnerParameter(NotificationConstants.FILES_DUPLICATED.getKey()));
    assertEquals("1", notificationInfo.getValueOwnerParameter(NotificationConstants.FILES_UPDATED.getKey()));
    assertEquals("1", notificationInfo.getValueOwnerParameter(NotificationConstants.FILES_IGNORED.getKey()));
    assertEquals("0", notificationInfo.getValueOwnerParameter(NotificationConstants.FILES_FAILED.getKey()));

    assertEquals(Arrays.asList(members), notificationInfo.getSendToUserIds());
    when(spaceService.getSpaceByPrettyName("receiver")).thenReturn(null);
    NotificationInfo notificationInfo1 = importDocumentsPlugin.makeNotification(ctx);
    assertEquals(List.of("receiver"), notificationInfo1.getSendToUserIds());

  }
}
