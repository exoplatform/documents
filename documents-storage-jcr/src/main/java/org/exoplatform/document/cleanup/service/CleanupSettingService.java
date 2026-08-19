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
package org.exoplatform.document.cleanup.service;

import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import org.exoplatform.commons.api.settings.SettingService;
import org.exoplatform.commons.api.settings.SettingValue;
import org.exoplatform.commons.api.settings.data.Context;
import org.exoplatform.commons.api.settings.data.Scope;
import org.exoplatform.document.cleanup.model.CleanupParams;

import io.meeds.social.util.JsonUtils;

/**
 * Cleanup campaign settings: platform defaults overridable via exo.properties
 * and persisted overrides in {@link SettingService} (GLOBAL context). A
 * campaign snapshots the effective values at launch, so later edits never
 * affect a running campaign.
 */
@Service
public class CleanupSettingService {

  private static final Context CLEANUP_CONTEXT    = Context.GLOBAL.id("DocumentsCleanup");

  private static final Scope   CLEANUP_SCOPE      = Scope.GLOBAL;

  private static final String  PERIOD_MONTHS_KEY  = "cleanup.period.months";

  private static final String  MIN_FILE_SIZE_KEY  = "cleanup.minFileSize.bytes";

  private static final String  GRACE_DAYS_KEY     = "cleanup.grace.days";

  private static final String  MAX_VERSIONS_KEY   = "cleanup.maxVersionsPerFile";

  private static final String  EXCLUDED_PATHS_KEY = "cleanup.excludedPaths";

  @Autowired
  private SettingService       settingService;

  @Value("${cleanup.period.months:24}")
  private int                  defaultPeriodMonths;

  @Value("${cleanup.minFileSize.bytes:5242880}")
  private long                 defaultMinFileSizeBytes;

  @Value("${cleanup.grace.days:14}")
  private int                  defaultGraceDays;

  @Value("${cleanup.maxVersionsPerFile:25}")
  private int                  defaultMaxVersionsPerFile;

  @Value("${cleanup.excludedPaths:}")
  private String               defaultExcludedPaths;

  @Value("${cleanup.batch.size:200}")
  private int                  batchSize;

  @Value("${cleanup.report.retention.campaigns:3}")
  private int                  reportRetentionCampaigns;

  /**
   * @return the current platform default parameters (persisted overrides,
   *         falling back to exo.properties values)
   */
  public CleanupParams getDefaultParams() {
    return new CleanupParams(getInt(PERIOD_MONTHS_KEY, defaultPeriodMonths),
                             getLong(MIN_FILE_SIZE_KEY, defaultMinFileSizeBytes),
                             getInt(GRACE_DAYS_KEY, defaultGraceDays),
                             getInt(MAX_VERSIONS_KEY, defaultMaxVersionsPerFile),
                             getExcludedPaths(),
                             batchSize);
  }

  /**
   * @param overrides partial parameters, null fields meaning 'use default'
   * @return fully populated parameters, to snapshot at campaign launch
   */
  public CleanupParams getEffectiveParams(CleanupParams overrides) {
    CleanupParams defaults = getDefaultParams();
    if (overrides == null) {
      return defaults;
    }
    return new CleanupParams(overrides.getPeriodMonths() == null ? defaults.getPeriodMonths() : overrides.getPeriodMonths(),
                             overrides.getMinFileSizeBytes() == null ? defaults.getMinFileSizeBytes() :
                                                                     overrides.getMinFileSizeBytes(),
                             overrides.getGraceDays() == null ? defaults.getGraceDays() : overrides.getGraceDays(),
                             overrides.getMaxVersionsPerFile() == null ? defaults.getMaxVersionsPerFile() :
                                                                       overrides.getMaxVersionsPerFile(),
                             overrides.getExcludedPaths() == null ? defaults.getExcludedPaths() : overrides.getExcludedPaths(),
                             overrides.getBatchSize() == null ? defaults.getBatchSize() : overrides.getBatchSize());
  }

  /**
   * Persists new platform defaults (overriding exo.properties values).
   *
   * @param params defaults to persist, null fields left unchanged
   */
  public void updateDefaultParams(CleanupParams params) {
    if (params.getPeriodMonths() != null) {
      set(PERIOD_MONTHS_KEY, String.valueOf(params.getPeriodMonths()));
    }
    if (params.getMinFileSizeBytes() != null) {
      set(MIN_FILE_SIZE_KEY, String.valueOf(params.getMinFileSizeBytes()));
    }
    if (params.getGraceDays() != null) {
      set(GRACE_DAYS_KEY, String.valueOf(params.getGraceDays()));
    }
    if (params.getMaxVersionsPerFile() != null) {
      set(MAX_VERSIONS_KEY, String.valueOf(params.getMaxVersionsPerFile()));
    }
    if (params.getExcludedPaths() != null) {
      set(EXCLUDED_PATHS_KEY, JsonUtils.toJsonString(params.getExcludedPaths()));
    }
  }

  public int getBatchSize() {
    return batchSize;
  }

  public int getReportRetentionCampaigns() {
    return reportRetentionCampaigns;
  }

  private List<String> getExcludedPaths() {
    String value = get(EXCLUDED_PATHS_KEY);
    if (value != null) {
      return Arrays.asList(JsonUtils.fromJsonString(value, String[].class));
    } else if (StringUtils.isBlank(defaultExcludedPaths)) {
      return List.of();
    } else {
      return Arrays.stream(defaultExcludedPaths.split(",")).map(String::trim).filter(StringUtils::isNotBlank).toList();
    }
  }

  private int getInt(String key, int defaultValue) {
    String value = get(key);
    return value == null ? defaultValue : Integer.parseInt(value);
  }

  private long getLong(String key, long defaultValue) {
    String value = get(key);
    return value == null ? defaultValue : Long.parseLong(value);
  }

  private String get(String key) {
    SettingValue<?> settingValue = settingService.get(CLEANUP_CONTEXT, CLEANUP_SCOPE, key);
    return settingValue == null || settingValue.getValue() == null ? null : settingValue.getValue().toString();
  }

  private void set(String key, String value) {
    settingService.set(CLEANUP_CONTEXT, CLEANUP_SCOPE, key, SettingValue.create(value));
  }

}
