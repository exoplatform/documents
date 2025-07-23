/**
 * Copyright (C) 2025 eXo Platform SAS
 *
 *  This program is free software: you can redistribute it and/or modify
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
package org.exoplatform.documents.storage.jcr.webdav.job;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import org.exoplatform.documents.storage.jcr.webdav.cache.CachedJcrWebDavService;

import lombok.Synchronized;

@Configuration
@EnableScheduling
public class WebDavUnlockJob {

  @Autowired
  private CachedJcrWebDavService webDavService;

  @Scheduled(cron = "${documents.WebDavUnlockJob.expression:0 0/5 * ? * *}")
  @Synchronized
  public void run() {
    webDavService.unlockTimedOutNodes();
  }

}
