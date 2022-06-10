package org.exoplatform.documents.listener;

import org.exoplatform.services.listener.Event;
import org.exoplatform.services.listener.Listener;
import org.exoplatform.social.core.storage.api.ActivityStorage;
import org.exoplatform.social.core.storage.cache.CachedActivityStorage;

public class AttachmentsActivityCacheUpdater extends Listener<String, String> {

  private CachedActivityStorage cachedActivityStorage;

  public AttachmentsActivityCacheUpdater(ActivityStorage activityStorage) {
    if (activityStorage instanceof CachedActivityStorage) {
      this.cachedActivityStorage = (CachedActivityStorage) activityStorage;
    }
  }

  @Override
  public void onEvent(Event<String, String> event) throws Exception {
    if (cachedActivityStorage != null && event.getData() != null) {
      cachedActivityStorage.clearActivityCachedByAttachmentId(event.getData());
    }
  }
}
