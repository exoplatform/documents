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
 *
*/
package org.exoplatform.documents.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Modifier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.stereotype.Service;

import org.exoplatform.documents.service.SpaceDocumentsExposureService;
import org.exoplatform.social.core.space.model.Space;

import io.meeds.social.space.security.plugin.SpaceSecuritySettingsPlugin;

@ExtendWith(MockitoExtension.class)
class SpaceDocumentsSecuritySettingsPluginTest {

  @Mock
  private SpaceDocumentsExposureService        spaceDocumentsExposureService;

  @InjectMocks
  private SpaceDocumentsSecuritySettingsPlugin plugin;

  @Test
  void aChangeOfTheSettingRemovesTheSpacePublicLinksFirst() throws Exception {
    Space space = new Space();

    plugin.beforeDocumentsKeptInsideChange(space, true, "manager");
    plugin.beforeDocumentsKeptInsideChange(space, false, "manager");

    verify(spaceDocumentsExposureService).removePublicLinks(space, true, "manager");
    verify(spaceDocumentsExposureService).removePublicLinks(space, false, "manager");
  }

  @Test
  void aFailedRemovalRefusesTheChange() {
    Space space = new Space();
    doThrow(new IllegalStateException("unreadable")).when(spaceDocumentsExposureService).removePublicLinks(space, true, "manager");

    assertThrows(IllegalStateException.class, () -> plugin.beforeDocumentsKeptInsideChange(space, true, "manager"));
  }

  /**
   * Social collects the plugin among every addon's beans only when it carries
   * {@code @Service} under a name of its own and can be proxied: kills the
   * annotation-removed mutant, which a test calling the method cannot
   */
  @Test
  void thePluginIsAServiceOtherAddonsCanCollect() {
    Service service = SpaceDocumentsSecuritySettingsPlugin.class.getAnnotation(Service.class);
    assertNotNull(service);
    assertEquals(SpaceDocumentsSecuritySettingsPlugin.BEAN_NAME, service.value());
    assertFalse(Modifier.isFinal(SpaceDocumentsSecuritySettingsPlugin.class.getModifiers()));
    assertEquals(SpaceSecuritySettingsPlugin.class, SpaceDocumentsSecuritySettingsPlugin.class.getInterfaces()[0]);
  }

}
