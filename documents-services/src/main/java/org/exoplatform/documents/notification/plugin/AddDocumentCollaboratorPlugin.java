package org.exoplatform.documents.notification.plugin;

import org.exoplatform.commons.api.notification.NotificationContext;
import org.exoplatform.commons.api.notification.model.NotificationInfo;
import org.exoplatform.commons.api.notification.plugin.BaseNotificationPlugin;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.documents.notification.utils.NotificationConstants;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class AddDocumentCollaboratorPlugin extends BaseNotificationPlugin {

  public static final String ID = "AddDocumentCollaboratorPlugin";

  private final SpaceService spaceService;

  public AddDocumentCollaboratorPlugin(InitParams initParams, SpaceService spaceService) {
    super(initParams);
    this.spaceService = spaceService;
  }

  @Override
  public String getId() {
    return ID;
  }

  @Override
  public boolean isValid(NotificationContext notificationContext) {
    return true;
  }

  @Override
  protected NotificationInfo makeNotification(NotificationContext notificationContext) {
    String fromUser = notificationContext.value(NotificationConstants.FROM_USER);
    String target = notificationContext.value(NotificationConstants.RECEIVERS);
    List<String> receivers;
    Space space = spaceService.getSpaceByPrettyName(target);
    if (space != null) {
      receivers = new LinkedList<>(Arrays.asList(space.getMembers()));
      receivers.remove(fromUser);
    } else {
      receivers = new LinkedList<>(List.of(target));
    }
    String documentUrl = notificationContext.value(NotificationConstants.DOCUMENT_URL);
    String documentName = notificationContext.value(NotificationConstants.DOCUMENT_NAME);
    return NotificationInfo.instance()
                           .setFrom(fromUser)
                           .to(receivers)
                           .with(NotificationConstants.FROM_USER.getKey(), fromUser)
                           .with(NotificationConstants.DOCUMENT_URL.getKey(), documentUrl)
                           .with(NotificationConstants.DOCUMENT_NAME.getKey(), documentName)
                           .key(getKey())
                           .end();
  }
}
