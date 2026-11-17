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
package org.exoplatform.documents.service;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.component.RequestLifeCycle;

/** Runs each purge of the Recycle Bins on one thread of its own, inside the portal container. */
final class RecycleBinPurgeExecutor implements Executor, AutoCloseable {

  private static final String   PURGE_THREAD = "documents-recycle-bin-purge";

  private final ExecutorService executorService;

  private RecycleBinPurgeExecutor(ExecutorService executorService) {
    this.executorService = executorService;
  }

  static RecycleBinPurgeExecutor create() {
    return new RecycleBinPurgeExecutor(Executors.newSingleThreadExecutor(task -> {
      Thread thread = new Thread(task, PURGE_THREAD);
      thread.setDaemon(true);
      return thread;
    }));
  }

  /** Runs a step in a request of its own, of the container of the thread. */
  static void inRequest(Runnable step) {
    RequestLifeCycle.begin(ExoContainerContext.getCurrentContainer());
    try {
      step.run();
    } finally {
      RequestLifeCycle.end();
    }
  }

  @Override
  public void execute(Runnable purge) {
    PortalContainer container = PortalContainer.getInstance();
    executorService.execute(() -> {
      ExoContainerContext.setCurrentContainer(container);
      purge.run();
    });
  }

  @Override
  public void close() {
    executorService.shutdownNow();
  }

}
