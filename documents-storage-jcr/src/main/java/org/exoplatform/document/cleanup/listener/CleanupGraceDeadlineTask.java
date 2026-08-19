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
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <gnu.org/licenses>.
 */
package org.exoplatform.document.cleanup.listener;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import org.exoplatform.document.cleanup.service.CleanupCampaignService;

import lombok.Synchronized;

/**
 * Scheduled glue, no business logic: flips the PUBLISHED campaign to LOCKED
 * once its grace deadline elapsed, enforces report retention, and watchdogs the
 * scan/purge workers (a campaign whose worker thread died mid-run is resumed on
 * the next tick, without a JVM restart).
 */
@Configuration
@EnableScheduling
public class CleanupGraceDeadlineTask {

  @Autowired
  private CleanupCampaignService campaignService;

  @Scheduled(cron = "${documents.cleanup.lockCheck.expression:0 0/10 * ? * *}")
  @Synchronized
  public void run() {
    campaignService.lockExpiredPublishedCampaign();
    campaignService.applyRetention();
    campaignService.resumeStalledWorkers();
  }

}
