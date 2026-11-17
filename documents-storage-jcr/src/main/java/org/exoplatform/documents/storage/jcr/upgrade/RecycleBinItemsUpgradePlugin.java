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
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.container.monitor.jvm.ServerStartupWaiter;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.documents.storage.TrashStorage;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

/**
 * Gives the documents already in the trash when the Recycle Bin is installed
 * the facts it lists them with, so that each one shows in the bin of its
 * drive. It is declared to run asynchronously, once the server has started:
 * the trash may hold many items and the upgrade must not hold the startup. A
 * failure is thrown, so that the upgrade is not recorded as done and runs
 * again at the next start; what it had already handled is skipped then.
 */
public class RecycleBinItemsUpgradePlugin extends UpgradeProductPlugin {

  private static final Log      LOG = ExoLogger.getLogger(RecycleBinItemsUpgradePlugin.class);

  private final TrashStorage    trashStorage;

  private final PortalContainer container;

  public RecycleBinItemsUpgradePlugin(TrashStorage trashStorage, PortalContainer container, InitParams initParams) {
    super(initParams);
    this.trashStorage = trashStorage;
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
    long start = System.currentTimeMillis();
    RequestLifeCycle.begin(container);
    try {
      int normalized = trashStorage.normalizeRecycleBinItems();
      LOG.info("{} item(s) of the trash were given their Recycle Bin facts in {} ms", normalized, System.currentTimeMillis() - start);
    } catch (Exception e) {
      throw new UpgradePluginException("The items of the trash could not be given their Recycle Bin facts", e);
    } finally {
      RequestLifeCycle.end();
    }
  }

  void awaitServerStartup() throws InterruptedException {
    ServerStartupWaiter.awaitServerStartup(container, "Recycle Bin items upgrade");
  }

}
