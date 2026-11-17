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

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronExpression;

import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

/**
 * Starts the daily run of the Recycle Bins when its hour comes. It decides
 * nothing: what the run does is {@link RecycleBinService#purgeExpired()}.
 */
@Configuration
@EnableScheduling
public class RecycleBinPurgeTask implements SchedulingConfigurer {

  static final String             DEFAULT_CRON = "0 0 2 * * *";

  private static final Log        LOG          = ExoLogger.getLogger(RecycleBinPurgeTask.class);

  private final RecycleBinService recycleBinService;

  private final String            cron;

  public RecycleBinPurgeTask(RecycleBinService recycleBinService,
                             @Value("${documents.recycleBin.purge.cron:}")
                             String configuredCron) {
    this.recycleBinService = recycleBinService;
    this.cron = readCron(configuredCron);
  }

  @Override
  public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
    taskRegistrar.addCronTask(recycleBinService::purgeExpired, cron);
  }

  String getCron() {
    return cron;
  }

  private static String readCron(String configuredCron) {
    if (StringUtils.isBlank(configuredCron)) {
      return DEFAULT_CRON;
    }
    if (!CronExpression.isValidExpression(configuredCron.trim())) {
      LOG.warn("The property documents.recycleBin.purge.cron, '{}', is not a schedule: the daily run of the Recycle Bins starts at '{}'",
               configuredCron,
               DEFAULT_CRON);
      return DEFAULT_CRON;
    }
    return configuredCron.trim();
  }

}
