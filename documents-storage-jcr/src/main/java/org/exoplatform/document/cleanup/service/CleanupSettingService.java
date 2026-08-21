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
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

import io.meeds.social.util.JsonUtils;

/**
 * Cleanup campaign settings: platform defaults overridable via exo.properties
 * and persisted overrides in {@link SettingService} (GLOBAL context). A
 * campaign snapshots the effective values at launch, so later edits never
 * affect a running campaign.
 */
@Service
public class CleanupSettingService {

  /** Lower bound of the dry-run scan reader-thread count: never zero reader. */
  public static final int      MIN_SCAN_THREADS   = 1;

  /**
   * Upper bound of the dry-run scan reader-thread count. A configured value
   * outside [{@link #MIN_SCAN_THREADS}, {@link #MAX_SCAN_THREADS}] is CLAMPED,
   * never honoured: a typo in exo.properties must not be able to fan a
   * background simulation out over the repository.
   */
  public static final int      MAX_SCAN_THREADS   = 20;

  private static final Log     LOG                = ExoLogger.getLogger(CleanupSettingService.class);

  private static final Context CLEANUP_CONTEXT    = Context.GLOBAL.id("DocumentsCleanup");

  private static final Scope   CLEANUP_SCOPE      = Scope.GLOBAL;

  private static final String  PERIOD_MONTHS_KEY  = "cleanup.period.months";

  private static final String  MIN_FILE_SIZE_KEY  = "cleanup.minFileSize.bytes";

  private static final String  GRACE_DAYS_KEY     = "cleanup.grace.days";

  private static final String  MAX_VERSIONS_KEY   = "cleanup.maxVersionsPerFile";

  private static final String  EXCLUDED_PATHS_KEY = "cleanup.excludedPaths";

  @Autowired
  private SettingService       settingService;

  @Value("${documents.cleanup.period.months:24}")
  private int                  defaultPeriodMonths;

  @Value("${documents.cleanup.minFileSize.bytes:5242880}")
  private long                 defaultMinFileSizeBytes;

  @Value("${documents.cleanup.grace.days:14}")
  private int                  defaultGraceDays;

  @Value("${documents.cleanup.maxVersionsPerFile:25}")
  private int                  defaultMaxVersionsPerFile;

  @Value("${documents.cleanup.excludedPaths:}")
  private String               defaultExcludedPaths;

  @Value("${documents.cleanup.batch.size:200}")
  private int                  batchSize;

  /**
   * Items a PURGE processes between two progress checkpoints, and deliberately
   * an order of magnitude below {@link #batchSize}.
   * <p>
   * The two batches are not the same kind of thing. The scan's is a QUEUE
   * ENVELOPE, sized for throughput: it decides how many nodes a reader walks
   * before paying for a hand-off, and cutting it costs real time. The purge's is
   * a CHECKPOINT boundary whose cost — one indexed keyset query, one row update,
   * one commit — is invisible next to what dominates a purge item: deleting a
   * multi-gigabyte node and its version history from JCR, measured in seconds.
   * <p>
   * At 200 the bar advanced in 200-item jumps, so a purge that was merely SLOW
   * was indistinguishable from one that was STUCK — it reported '0% (0 / 5,083)'
   * for as long as the first two hundred deletions took, while the reclaimed
   * total beside it climbed into the gigabytes. Small also means a JVM death
   * loses less: an item's outcome is saved per item, but only a checkpoint makes
   * the progress it belongs to durable.
   */
  @Value("${documents.cleanup.purge.batch.size:5}")
  private int                  purgeBatchSize;

  @Value("${documents.cleanup.report.retention.campaigns:3}")
  private int                  reportRetentionCampaigns;

  /**
   * Reader threads of the parallel dry-run scan. Kept MODEST and configurable,
   * and the reason is repository load — NOT connection exhaustion: this
   * deployment's {@code exo-jcr_portal} pool is provisioned at 300 connections,
   * so ~10 concurrent reader sessions are a small fraction of it. What actually
   * costs is the JCR workspace CACHE: parallel deep traversals stream millions
   * of nodes through it and evict the live working set interactive users depend
   * on, so the platform gets slower while a background simulation runs. Ten is
   * the architect's call for PROD; a deployment with a smaller pool or a busier
   * repository lowers it through the property.
   */
  @Value("${documents.cleanup.scan.threads:10}")
  private int                  scanThreads;

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
                             batchSize,
                             getScanThreads());
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
                             overrides.getBatchSize() == null ? defaults.getBatchSize() : overrides.getBatchSize(),
                             overrides.getScanThreads() == null ? defaults.getScanThreads() : overrides.getScanThreads());
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

  /**
   * @return items a purge processes between two progress checkpoints, at least 1
   */
  public int getPurgeBatchSize() {
    return Math.max(1, purgeBatchSize);
  }

  public int getReportRetentionCampaigns() {
    return reportRetentionCampaigns;
  }

  /**
   * Upper bound a campaign may ask for, which is the platform ceiling and NOT the
   * configured default: an administrator tuning one run may go above what
   * exo.properties sets, and no further.
   * <p>
   * WHY IT IS NOT DERIVED FROM THE CONNECTION POOLS, which was the first proposal:
   * half of {@code min(JCR pool, JPA pool)} lands near 150 readers on this
   * deployment (the {@code exo-jcr_portal} pool is provisioned at 300), an order
   * of magnitude above what the repository survives. The binding constraint is
   * JCR workspace-cache eviction at the million-entry cap and raw repository
   * load, not connection exhaustion — and readers deliberately hold NO pooled
   * connection across a walk, {@code CleanupScanService#commitThenPost}
   * committing before every blocking post precisely so that ten readers cannot
   * pin ten connections. A pool-derived cap would license the load it looks like
   * it is protecting.
   *
   * @return the highest reader-thread count a campaign may request
   */
  public int getMaxScanThreads() {
    return MAX_SCAN_THREADS;
  }

  /**
   * @return the configured dry-run scan reader-thread count, CLAMPED to
   *         [{@link #MIN_SCAN_THREADS}, {@link #MAX_SCAN_THREADS}] — an
   *         out-of-range value is logged and corrected, never applied
   */
  public int getScanThreads() {
    if (scanThreads < MIN_SCAN_THREADS || scanThreads > MAX_SCAN_THREADS) {
      int clamped = Math.min(Math.max(scanThreads, MIN_SCAN_THREADS), MAX_SCAN_THREADS);
      LOG.warn("documents.cleanup.scan.threads is configured to {}, outside the supported [{}, {}] range: using {} instead",
               scanThreads,
               MIN_SCAN_THREADS,
               MAX_SCAN_THREADS,
               clamped);
      return clamped;
    }
    return scanThreads;
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
