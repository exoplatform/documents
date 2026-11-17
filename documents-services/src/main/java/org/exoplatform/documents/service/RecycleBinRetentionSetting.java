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
import org.springframework.stereotype.Component;

import org.exoplatform.commons.api.settings.SettingService;
import org.exoplatform.commons.api.settings.SettingValue;
import org.exoplatform.commons.api.settings.data.Context;
import org.exoplatform.commons.api.settings.data.Scope;
import org.exoplatform.documents.constant.RecycleBinError;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

/**
 * The number of days an item stays in a Recycle Bin, one value for the whole
 * platform. Whatever its source, a value outside the bounds is never handed
 * out: the purge removes items for good with it.
 */
@Component
public class RecycleBinRetentionSetting {

  static final long            DEFAULT_DAYS = 30;

  static final long            MIN_DAYS     = 1;

  static final long            MAX_DAYS     = 365;

  static final Scope           SCOPE        = Scope.APPLICATION.id("DocumentsRecycleBin");

  static final String          KEY          = "retentionDays";

  private static final Log     LOG          = ExoLogger.getLogger(RecycleBinRetentionSetting.class);

  private final SettingService settingService;

  private final long           defaultDays;

  public RecycleBinRetentionSetting(SettingService settingService,
                                    @Value("${exo.trashcleaner.lifetime:}")
                                    String formerLifetime) {
    this.settingService = settingService;
    this.defaultDays = readDefault(formerLifetime);
  }

  /**
   * @return the period in force: the one an administrator set, else the one
   *         the platform is configured with
   */
  public long get() {
    SettingValue<?> stored = settingService.get(Context.GLOBAL, SCOPE, KEY);
    if (stored == null || stored.getValue() == null) {
      return defaultDays;
    }
    long days = parse(stored.getValue().toString());
    if (days < MIN_DAYS || days > MAX_DAYS) {
      LOG.warn("The retention period stored for the Recycle Bins, '{}', is not a number of days between {} and {}: {} days apply",
               stored.getValue(),
               MIN_DAYS,
               MAX_DAYS,
               DEFAULT_DAYS);
      return DEFAULT_DAYS;
    }
    return days;
  }

  /**
   * @param days the period an administrator sets
   * @throws IllegalArgumentException with the code
   *           {@link RecycleBinError#RETENTION_INVALID} when it is outside
   *           the bounds; nothing is stored then
   */
  public void set(long days) {
    if (days < MIN_DAYS || days > MAX_DAYS) {
      throw new IllegalArgumentException(RecycleBinError.RETENTION_INVALID);
    }
    settingService.set(Context.GLOBAL, SCOPE, KEY, SettingValue.create(days));
  }

  /**
   * The period the platform starts with is the lifetime the former trash
   * cleaner was configured with, when it is one the bounds accept.
   */
  private static long readDefault(String formerLifetime) {
    if (StringUtils.isBlank(formerLifetime)) {
      return DEFAULT_DAYS;
    }
    long days = parse(formerLifetime);
    if (days < MIN_DAYS) {
      LOG.warn("The property exo.trashcleaner.lifetime, '{}', is not a number of days of at least {}: it is ignored and {} days apply",
               formerLifetime,
               MIN_DAYS,
               DEFAULT_DAYS);
      return DEFAULT_DAYS;
    }
    if (days > MAX_DAYS) {
      LOG.warn("The property exo.trashcleaner.lifetime, '{}', is longer than the {} days a Recycle Bin keeps an item at most: {} days apply",
               formerLifetime,
               MAX_DAYS,
               MAX_DAYS);
      return MAX_DAYS;
    }
    return days;
  }

  /** The number a text holds, 0 when it holds none: no period is that short. */
  private static long parse(String text) {
    try {
      return Long.parseLong(text.trim());
    } catch (NumberFormatException e) {
      return 0;
    }
  }

}
