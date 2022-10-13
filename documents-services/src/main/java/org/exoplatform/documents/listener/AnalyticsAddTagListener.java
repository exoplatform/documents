package org.exoplatform.documents.listener;

import org.apache.commons.lang3.StringUtils;
import org.exoplatform.analytics.model.StatisticData;
import org.exoplatform.analytics.utils.AnalyticsUtils;
import org.exoplatform.services.listener.Event;
import org.exoplatform.services.listener.Listener;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.metadata.tag.model.TagName;
import org.exoplatform.social.metadata.tag.model.TagObject;

import java.util.Date;
import java.util.Set;

public class AnalyticsAddTagListener extends Listener<TagObject, Set<TagName>> {

    private IdentityManager identityManager;

    public AnalyticsAddTagListener(IdentityManager identityManager) {
        this.identityManager = identityManager;
    }



    @Override
    public void onEvent(Event<TagObject, Set<TagName>> event) throws Exception {
        TagObject tagObject = event.getSource();
        Set<TagName> tagNames = event.getData();
        int numberOfTags = tagNames.size();
        String currentUser = ConversationState.getCurrent().getIdentity().getUserId();
        long userId = Long.parseLong(identityManager.getOrCreateUserIdentity(currentUser).getId());
        StatisticData statisticData = new StatisticData();
        statisticData.setModule("portal");
        statisticData.setSubModule("ui");
        statisticData.setOperation("Add tag");
        statisticData.setTimestamp(new Date().getTime());
        statisticData.setUserId(userId);
        statisticData.addParameter("username", currentUser);
        statisticData.addParameter("dataType", StringUtils.lowerCase(tagObject.getType()));
        statisticData.addParameter("spaceId", tagObject.getSpaceId());

        for (int i = 0; i < numberOfTags; i++) {
            AnalyticsUtils.addStatisticData(statisticData);
        }
    }
}
