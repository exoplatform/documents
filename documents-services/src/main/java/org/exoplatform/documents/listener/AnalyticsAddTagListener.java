package org.exoplatform.documents.listener;

import java.util.Date;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.portal.config.UserACL;
import org.exoplatform.services.listener.Event;
import org.exoplatform.services.listener.Listener;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.services.security.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.metadata.tag.model.TagName;
import org.exoplatform.social.metadata.tag.model.TagObject;

import io.meeds.analytics.model.StatisticData;

public class AnalyticsAddTagListener extends Listener<TagObject, Set<TagName>> {

  private IdentityManager identityManager;

  private UserACL         userAcl;

  public AnalyticsAddTagListener(UserACL userAcl,
                                 IdentityManager identityManager) {
    this.identityManager = identityManager;
    this.userAcl = userAcl;
  }

  @Override
  public void onEvent(Event<TagObject, Set<TagName>> event) throws Exception {
    TagObject tagObject = event.getSource();
    Set<TagName> tagNames = event.getData();
    long userId = 0;
    if (ConversationState.getCurrent() != null
        && !userAcl.isAnonymousUser(ConversationState.getCurrent().getIdentity())) {
      Identity identity = ConversationState.getCurrent().getIdentity();
      String currentUser = identity.getUserId();
      userId = Long.parseLong(identityManager.getOrCreateUserIdentity(currentUser).getId());
    }
    StatisticData statisticData = new StatisticData();
    statisticData.setModule("portal");
    statisticData.setSubModule("ui");
    statisticData.setOperation("Add tag");
    statisticData.setUserId(userId);
    statisticData.setSpaceId(tagObject.getSpaceId());
    statisticData.setTimestamp(new Date().getTime());
    statisticData.addKeyword("dataType", StringUtils.lowerCase(tagObject.getType()));
    statisticData.addKeywords("tagName", tagNames);
  }
}
