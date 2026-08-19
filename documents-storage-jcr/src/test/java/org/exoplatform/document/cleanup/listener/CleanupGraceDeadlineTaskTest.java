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
package org.exoplatform.document.cleanup.listener;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.exoplatform.document.cleanup.service.CleanupCampaignService;

/**
 * Pins the scheduled-glue contract: each tick only delegates to the Service
 * layer (grace-deadline locking, report retention, stalled-worker watchdog),
 * with no business logic of its own.
 */
@ExtendWith(MockitoExtension.class)
class CleanupGraceDeadlineTaskTest {

  @Mock
  private CleanupCampaignService   campaignService;

  @InjectMocks
  private CleanupGraceDeadlineTask task;

  @Test
  void runDelegatesOnlyToTheService() {
    task.run();

    verify(campaignService).lockExpiredPublishedCampaign();
    verify(campaignService).applyRetention();
    verify(campaignService).resumeStalledWorkers();
    verifyNoMoreInteractions(campaignService);
  }

}
