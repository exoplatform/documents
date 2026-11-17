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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/**
 * When the daily run of the Recycle Bins starts, and that it does nothing
 * else than start it.
 */
class RecycleBinPurgeTaskTest {

  private final RecycleBinService recycleBinService = mock(RecycleBinService.class);

  @Test
  void theDailyRunStartsAtTwoWhenNoScheduleIsConfigured() {
    assertEquals("0 0 2 * * *", new RecycleBinPurgeTask(recycleBinService, null).getCron());
    assertEquals("0 0 2 * * *", new RecycleBinPurgeTask(recycleBinService, " ").getCron());
  }

  @Test
  void aConfiguredScheduleAppliesWhenItIsOne() {
    assertEquals("0 30 3 * * *", new RecycleBinPurgeTask(recycleBinService, "0 30 3 * * *").getCron());
    assertEquals("0 30 3 * * *", new RecycleBinPurgeTask(recycleBinService, " 0 30 3 * * * ").getCron());
    assertEquals("0 0 2 * * *", new RecycleBinPurgeTask(recycleBinService, "every night").getCron());
    assertEquals("0 0 2 * * *", new RecycleBinPurgeTask(recycleBinService, "0 0 25 * * *").getCron());
  }

  @Test
  void theScheduleStartsTheDailyRunAndNothingElse() {
    ScheduledTaskRegistrar registrar = new ScheduledTaskRegistrar();

    new RecycleBinPurgeTask(recycleBinService, "0 30 3 * * *").configureTasks(registrar);

    assertEquals(1, registrar.getCronTaskList().size());
    assertEquals("0 30 3 * * *", registrar.getCronTaskList().get(0).getExpression());
    verifyNoInteractions(recycleBinService);
    registrar.getCronTaskList().get(0).getRunnable().run();
    verify(recycleBinService).purgeExpired();
  }

}
