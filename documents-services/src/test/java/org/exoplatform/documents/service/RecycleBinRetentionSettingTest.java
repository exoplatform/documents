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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import org.exoplatform.commons.api.settings.SettingService;
import org.exoplatform.commons.api.settings.SettingValue;
import org.exoplatform.commons.api.settings.data.Context;
import org.exoplatform.commons.api.settings.data.Scope;

/**
 * The number of days an item stays in a Recycle Bin: where it is stored, what
 * applies while an administrator has set none, and the bounds no source gets
 * past.
 */
class RecycleBinRetentionSettingTest {

  private SettingService settingService;

  @BeforeEach
  void setUp() {
    settingService = mock(SettingService.class);
  }

  @Test
  void thirtyDaysApplyWhileNoPeriodIsSetNorConfigured() {
    assertEquals(30, new RecycleBinRetentionSetting(settingService, null).get());
    assertEquals(30, new RecycleBinRetentionSetting(settingService, "").get());
    assertEquals(30, new RecycleBinRetentionSetting(settingService, "  ").get());
    stored(null);
    assertEquals(30, new RecycleBinRetentionSetting(settingService, null).get());
  }

  @Test
  void theLifetimeOfTheFormerCleanerAppliesWhileNoPeriodIsSetWhenTheBoundsAcceptIt() {
    assertEquals(45, new RecycleBinRetentionSetting(settingService, "45").get());
    assertEquals(45, new RecycleBinRetentionSetting(settingService, " 45 ").get());
    assertEquals(1, new RecycleBinRetentionSetting(settingService, "1").get());
    assertEquals(365, new RecycleBinRetentionSetting(settingService, "365").get());
    // Longer than a bin keeps an item: the longest period.
    assertEquals(365, new RecycleBinRetentionSetting(settingService, "366").get());
    assertEquals(365, new RecycleBinRetentionSetting(settingService, "4000").get());
    // No number of days: ignored.
    assertEquals(30, new RecycleBinRetentionSetting(settingService, "0").get());
    assertEquals(30, new RecycleBinRetentionSetting(settingService, "-3").get());
    assertEquals(30, new RecycleBinRetentionSetting(settingService, "a month").get());
    assertEquals(30, new RecycleBinRetentionSetting(settingService, "1.5").get());
  }

  @Test
  void thePeriodAnAdministratorSetAppliesWhateverIsConfigured() {
    RecycleBinRetentionSetting setting = new RecycleBinRetentionSetting(settingService, "45");

    stored(7L);
    assertEquals(7, setting.get());
    stored(1L);
    assertEquals(1, setting.get());
    stored(365L);
    assertEquals(365, setting.get());
    stored(" 12 ");
    assertEquals(12, setting.get());
  }

  @Test
  void aStoredPeriodOutsideTheBoundsIsNeverHandedOut() {
    RecycleBinRetentionSetting setting = new RecycleBinRetentionSetting(settingService, "45");

    for (Object value : new Object[] { 0L, -1L, 366L, 100000L, "never" }) {
      stored(value);
      assertEquals(30, setting.get(), String.valueOf(value));
    }
  }

  @Test
  void aPeriodIsStoredForTheWholePlatformWhenTheBoundsAcceptIt() {
    RecycleBinRetentionSetting setting = new RecycleBinRetentionSetting(settingService, null);

    setting.set(1);
    setting.set(365);

    ArgumentCaptor<Scope> scope = ArgumentCaptor.forClass(Scope.class);
    @SuppressWarnings("rawtypes")
    ArgumentCaptor<SettingValue> value = ArgumentCaptor.forClass(SettingValue.class);
    verify(settingService, org.mockito.Mockito.times(2)).set(eq(Context.GLOBAL), scope.capture(), eq("retentionDays"), value.capture());
    assertEquals("APPLICATION", scope.getValue().getName());
    assertEquals("DocumentsRecycleBin", scope.getValue().getId());
    assertEquals(1L, value.getAllValues().get(0).getValue());
    assertEquals(365L, value.getAllValues().get(1).getValue());
  }

  @Test
  void aPeriodOutsideTheBoundsIsRefusedAndNothingIsStored() {
    RecycleBinRetentionSetting setting = new RecycleBinRetentionSetting(settingService, null);

    for (long days : new long[] { 0, -5, 366, Long.MAX_VALUE }) {
      IllegalArgumentException refusal = assertThrows(IllegalArgumentException.class, () -> setting.set(days));
      assertEquals("recycleBin.retention.invalid", refusal.getMessage());
    }
    verify(settingService, never()).set(any(), any(), anyString(), any());
  }

  @Test
  void thePeriodIsReadWhereItIsStored() {
    RecycleBinRetentionSetting setting = new RecycleBinRetentionSetting(settingService, null);
    setting.get();

    ArgumentCaptor<Scope> scope = ArgumentCaptor.forClass(Scope.class);
    ArgumentCaptor<Context> context = ArgumentCaptor.forClass(Context.class);
    verify(settingService).get(context.capture(), scope.capture(), eq("retentionDays"));
    assertSame(Context.GLOBAL, context.getValue());
    assertEquals("APPLICATION", scope.getValue().getName());
    assertEquals("DocumentsRecycleBin", scope.getValue().getId());
  }

  @SuppressWarnings({ "rawtypes", "unchecked" })
  private void stored(Object value) {
    SettingValue settingValue = mock(SettingValue.class);
    when(settingValue.getValue()).thenReturn(value);
    when(settingService.get(any(), any(), anyString())).thenReturn(settingValue);
  }

}
