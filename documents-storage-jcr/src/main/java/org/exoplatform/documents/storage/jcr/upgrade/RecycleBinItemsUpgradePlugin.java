/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.exoplatform.documents.storage.jcr.upgrade;

import org.exoplatform.commons.upgrade.UpgradePluginException;
import org.exoplatform.commons.upgrade.UpgradeProductPlugin;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.container.monitor.jvm.ServerStartupWaiter;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.documents.service.RecycleBinService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

/**
 * Gives the documents already in the trash when the Recycle Bin is installed
 * the facts it lists them with, so that each one shows in the bin of its
 * drive, then counts every bin again, so that the Bin Overview lists their
 * drives and sizes. It is declared to run asynchronously, once the server has
 * started: the trash may hold many items and the upgrade must not hold the
 * startup. The Recycle Bin service is looked for then, as it lives in a
 * container that starts after this plugin. An item that cannot be given its
 * facts is logged and skipped: the upgrade is recorded as done all the same,
 * and the daily run of the Recycle Bins tries that item again and counts it
 * once it has them. A failure to read the trash or to count the bins, or a
 * Recycle Bin service not found, is thrown, so that the upgrade is not
 * recorded as done: it runs again at the next start and skips what it had
 * already handled. Only a first run made at a version the server already had
 * waits for an upgrade to a newer one. Meanwhile, the daily run gives the
 * items their facts and counts the bins again.
 */
public class RecycleBinItemsUpgradePlugin extends UpgradeProductPlugin {

  private static final Log      LOG = ExoLogger.getLogger(RecycleBinItemsUpgradePlugin.class);

  private final PortalContainer container;

  public RecycleBinItemsUpgradePlugin(PortalContainer container, InitParams initParams) {
    super(initParams);
    this.container = container;
  }

  @Override
  public void processUpgrade(String oldVersion, String newVersion) {
    try {
      awaitServerStartup();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new UpgradePluginException("The upgrade of the items of the trash was interrupted before it started", e);
    }
    RecycleBinService recycleBinService = getRecycleBinService();
    if (recycleBinService == null) {
      throw new UpgradePluginException("The Recycle Bin service is not available: the upgrade of the items of the trash is not recorded as done"
          + " and is attempted again at the next start, unless this first run was made at a version the server already had");
    }
    long start = System.currentTimeMillis();
    RequestLifeCycle.begin(container);
    try {
      int normalized = recycleBinService.normalizeItems();
      recycleBinService.recountDrives();
      LOG.info("{} item(s) of the trash were given their Recycle Bin facts and the Recycle Bins were counted again in {} ms",
               normalized,
               System.currentTimeMillis() - start);
    } catch (RuntimeException e) {
      throw new UpgradePluginException("The items of the trash could not be given their Recycle Bin facts, or the Recycle Bins could not be counted again",
                                       e);
    } finally {
      RequestLifeCycle.end();
    }
  }

  void awaitServerStartup() throws InterruptedException {
    ServerStartupWaiter.awaitServerStartup(container, "Recycle Bin items upgrade");
  }

  RecycleBinService getRecycleBinService() {
    return ExoContainerContext.getService(RecycleBinService.class);
  }

}
