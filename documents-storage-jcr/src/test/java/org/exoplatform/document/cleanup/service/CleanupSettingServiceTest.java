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
package org.exoplatform.document.cleanup.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.exoplatform.commons.api.settings.SettingService;
import org.exoplatform.commons.api.settings.SettingValue;
import org.exoplatform.commons.api.settings.data.Context;
import org.exoplatform.commons.api.settings.data.Scope;
import org.exoplatform.document.cleanup.model.CleanupParams;

/**
 * Setting-service tests pinning the parameter precedence contract: persisted
 * {@link SettingService} overrides win over the exo.properties defaults, and
 * campaign-level overrides win over both when snapshotting effective
 * parameters. Null override fields always fall back to the default.
 */
@ExtendWith(MockitoExtension.class)
class CleanupSettingServiceTest {

  private static final String   PERIOD_MONTHS_KEY  = "cleanup.period.months";

  private static final String   MIN_FILE_SIZE_KEY  = "cleanup.minFileSize.bytes";

  private static final String   GRACE_DAYS_KEY     = "cleanup.grace.days";

  private static final String   MAX_VERSIONS_KEY   = "cleanup.maxVersionsPerFile";

  private static final String   EXCLUDED_PATHS_KEY = "cleanup.excludedPaths";

  @Mock
  private SettingService        settingService;

  @InjectMocks
  private CleanupSettingService cleanupSettingService;

  @BeforeEach
  void setUp() throws ReflectiveOperationException {
    // Property values normally injected by Spring through @Value fallbacks
    setField("defaultPeriodMonths", 24);
    setField("defaultMinFileSizeBytes", 5242880L);
    setField("defaultGraceDays", 14);
    setField("defaultMaxVersionsPerFile", 25);
    setField("defaultExcludedPaths", "");
    setField("batchSize", 200);
    setField("reportRetentionCampaigns", 3);
  }

  @Test
  void getDefaultParamsFallsBackToPropertiesWithoutPersistedOverrides() {
    CleanupParams defaults = cleanupSettingService.getDefaultParams();

    assertEquals(24, defaults.getPeriodMonths());
    assertEquals(5242880L, defaults.getMinFileSizeBytes());
    assertEquals(14, defaults.getGraceDays());
    assertEquals(25, defaults.getMaxVersionsPerFile());
    assertTrue(defaults.getExcludedPaths().isEmpty(), "A blank excluded-paths property must yield an empty list");
    assertEquals(200, defaults.getBatchSize());
  }

  @Test
  void getDefaultParamsParsesExcludedPathsProperty() throws ReflectiveOperationException {
    setField("defaultExcludedPaths", " /Users/root , ,/Groups/spaces/admin ");

    assertEquals(List.of("/Users/root", "/Groups/spaces/admin"),
                 cleanupSettingService.getDefaultParams().getExcludedPaths(),
                 "The CSV property must be trimmed and blank entries dropped");
  }

  @Test
  void persistedOverridesWinOverProperties() {
    lenient().when(settingService.get(any(Context.class), any(Scope.class), eq(PERIOD_MONTHS_KEY)))
             .thenReturn((SettingValue) SettingValue.create("12"));
    lenient().when(settingService.get(any(Context.class), any(Scope.class), eq(MIN_FILE_SIZE_KEY)))
             .thenReturn((SettingValue) SettingValue.create("2048"));
    lenient().when(settingService.get(any(Context.class), any(Scope.class), eq(EXCLUDED_PATHS_KEY)))
             .thenReturn((SettingValue) SettingValue.create("[\"/Trash\"]"));

    CleanupParams defaults = cleanupSettingService.getDefaultParams();

    assertEquals(12, defaults.getPeriodMonths());
    assertEquals(2048L, defaults.getMinFileSizeBytes());
    assertEquals(List.of("/Trash"), defaults.getExcludedPaths());
    assertEquals(14, defaults.getGraceDays(), "Keys without a persisted override keep the property fallback");
    assertEquals(25, defaults.getMaxVersionsPerFile());
  }

  @Test
  void getEffectiveParamsReturnsDefaultsWithoutOverrides() {
    CleanupParams effective = cleanupSettingService.getEffectiveParams(null);

    assertEquals(cleanupSettingService.getDefaultParams(), effective);
  }

  @Test
  void getEffectiveParamsMergesPartialOverridesOverDefaults() {
    CleanupParams overrides = new CleanupParams(12, null, null, 2, List.of("/Trash"), null);

    CleanupParams effective = cleanupSettingService.getEffectiveParams(overrides);

    assertEquals(12, effective.getPeriodMonths());
    assertEquals(5242880L, effective.getMinFileSizeBytes(), "Null override fields must fall back to the default");
    assertEquals(14, effective.getGraceDays());
    assertEquals(2, effective.getMaxVersionsPerFile());
    assertEquals(List.of("/Trash"), effective.getExcludedPaths());
    assertEquals(200, effective.getBatchSize(), "The effective snapshot must always carry a batch size");
  }

  @Test
  void updateDefaultParamsPersistsOnlyProvidedFields() {
    cleanupSettingService.updateDefaultParams(new CleanupParams(12, null, 10, null, List.of("/Trash"), null));

    ArgumentCaptor<SettingValue<?>> valueCaptor = ArgumentCaptor.forClass(SettingValue.class);
    verify(settingService).set(any(Context.class), any(Scope.class), eq(PERIOD_MONTHS_KEY), valueCaptor.capture());
    assertEquals("12", valueCaptor.getValue().getValue());
    verify(settingService).set(any(Context.class), any(Scope.class), eq(GRACE_DAYS_KEY), valueCaptor.capture());
    assertEquals("10", valueCaptor.getValue().getValue());
    verify(settingService).set(any(Context.class), any(Scope.class), eq(EXCLUDED_PATHS_KEY), valueCaptor.capture());
    assertEquals("[\"/Trash\"]", valueCaptor.getValue().getValue());
    verify(settingService, never()).set(any(Context.class), any(Scope.class), eq(MIN_FILE_SIZE_KEY), any());
    verify(settingService, never()).set(any(Context.class), any(Scope.class), eq(MAX_VERSIONS_KEY), any());
  }

  @Test
  void updatedDefaultsFeedBackIntoEffectiveParams() {
    lenient().when(settingService.get(any(Context.class), any(Scope.class), eq(GRACE_DAYS_KEY)))
             .thenReturn((SettingValue) SettingValue.create("15"));

    CleanupParams effective = cleanupSettingService.getEffectiveParams(new CleanupParams(null, null, null, null, null, null));

    assertEquals(15, effective.getGraceDays(), "An all-null override object must still pick up persisted defaults");
  }

  @Test
  void staticSettingsComeFromProperties() {
    assertEquals(200, cleanupSettingService.getBatchSize());
    assertEquals(3, cleanupSettingService.getReportRetentionCampaigns());
  }

  private void setField(String name, Object value) throws ReflectiveOperationException {
    Field field = CleanupSettingService.class.getDeclaredField(name);
    field.setAccessible(true); // NOSONAR test wiring of @Value fields
    field.set(cleanupSettingService, value); // NOSONAR
  }

}
