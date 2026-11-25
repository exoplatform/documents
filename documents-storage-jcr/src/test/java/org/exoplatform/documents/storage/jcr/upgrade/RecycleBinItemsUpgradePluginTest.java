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
package org.exoplatform.documents.storage.jcr.upgrade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import org.exoplatform.commons.upgrade.UpgradePluginException;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;
import org.exoplatform.documents.service.RecycleBinService;

class RecycleBinItemsUpgradePluginTest {

  private RecycleBinService recycleBinService;

  private List<String>      calls;

  @BeforeEach
  void setUp() {
    calls = new ArrayList<>();
    recycleBinService = mock(RecycleBinService.class);
    when(recycleBinService.normalizeItems()).thenAnswer(invocation -> {
      calls.add("normalize");
      return 3;
    });
    doAnswer(invocation -> calls.add("recount")).when(recycleBinService).recountDrives();
  }

  @Test
  void onceTheServerHasStartedTheItemsAreGivenTheirFactsThenTheBinsAreCountedAgain() {
    plugin(null, recycleBinService).processUpgrade("7.2.0", "7.3.0");

    assertEquals(List.of("await", "lookup", "normalize", "recount"), calls);
  }

  @Test
  void aFailedNormalizationIsThrownAndCountsNothing() {
    IllegalStateException failure = new IllegalStateException("The trash cannot be read");
    when(recycleBinService.normalizeItems()).thenThrow(failure);
    RecycleBinItemsUpgradePlugin plugin = plugin(null, recycleBinService);

    UpgradePluginException thrown = assertThrows(UpgradePluginException.class, () -> plugin.processUpgrade("7.2.0", "7.3.0"));

    assertSame(failure, thrown.getCause());
    verify(recycleBinService, never()).recountDrives();
  }

  /**
   * Returning instead would record the upgrade as done with bins never
   * counted: the Bin Overview would miss the drives of the former items of
   * the trash until the first daily run.
   */
  @Test
  void aFailedCountIsThrownSoThatTheUpgradeIsNotRecordedAsDone() {
    IllegalStateException failure = new IllegalStateException("The database is not available");
    doThrow(failure).when(recycleBinService).recountDrives();
    RecycleBinItemsUpgradePlugin plugin = plugin(null, recycleBinService);

    UpgradePluginException thrown = assertThrows(UpgradePluginException.class, () -> plugin.processUpgrade("7.2.0", "7.3.0"));

    assertSame(failure, thrown.getCause());
  }

  @Test
  void anUnavailableServiceHandlesNothingAndIsThrownWithItsOwnMessage() {
    RecycleBinItemsUpgradePlugin plugin = plugin(null, null);

    UpgradePluginException thrown = assertThrows(UpgradePluginException.class, () -> plugin.processUpgrade("7.2.0", "7.3.0"));

    assertNull(thrown.getCause());
    assertTrue(thrown.getMessage().startsWith("The Recycle Bin service is not available"), thrown.getMessage());
    assertEquals(List.of("await", "lookup"), calls);
  }

  @Test
  void anInterruptedWaitHandlesNothingAndKeepsTheInterruption() {
    InterruptedException interruption = new InterruptedException("The server is stopping");
    RecycleBinItemsUpgradePlugin plugin = plugin(interruption, recycleBinService);

    try {
      UpgradePluginException thrown = assertThrows(UpgradePluginException.class, () -> plugin.processUpgrade("7.2.0", "7.3.0"));

      assertSame(interruption, thrown.getCause());
      assertTrue(Thread.currentThread().isInterrupted());
      assertEquals(List.of(), calls);
      verifyNoInteractions(recycleBinService);
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  void thePluginIsDeclaredToRunOnceWithoutHoldingTheStartup() throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    Element configuration = factory.newDocumentBuilder()
                                   .parse(new File("src/main/resources/conf/portal/configuration.xml"))
                                   .getDocumentElement();
    Element declaration = null;
    NodeList plugins = configuration.getElementsByTagName("component-plugin");
    for (int i = 0; i < plugins.getLength(); i++) {
      Element plugin = (Element) plugins.item(i);
      if (RecycleBinItemsUpgradePlugin.class.getName().equals(text(plugin, "type"))) {
        declaration = plugin;
      }
    }

    assertTrue(declaration != null, "The upgrade plugin is not declared");
    assertEquals("org.exoplatform.commons.upgrade.UpgradeProductService",
                 text((Element) declaration.getParentNode(), "target-component"));
    assertEquals("addUpgradePlugin", text(declaration, "set-method"));
    Map<String, String> params = new HashMap<>();
    NodeList valueParams = declaration.getElementsByTagName("value-param");
    for (int i = 0; i < valueParams.getLength(); i++) {
      Element param = (Element) valueParams.item(i);
      params.put(text(param, "name"), text(param, "value"));
    }
    assertEquals("true", params.get("plugin.upgrade.async.execution"));
    assertEquals("true", params.get("plugin.upgrade.execute.once"));
    assertEquals("org.exoplatform.social", params.get("product.group.id"));
  }

  private RecycleBinItemsUpgradePlugin plugin(InterruptedException interruption, RecycleBinService service) {
    InitParams params = new InitParams();
    ValueParam groupId = new ValueParam();
    groupId.setName("product.group.id");
    groupId.setValue("org.exoplatform.social");
    params.addParameter(groupId);
    return new RecycleBinItemsUpgradePlugin(mock(PortalContainer.class), params) {
      @Override
      void awaitServerStartup() throws InterruptedException {
        if (interruption != null) {
          throw interruption;
        }
        calls.add("await");
      }

      @Override
      RecycleBinService getRecycleBinService() {
        calls.add("lookup");
        return service;
      }
    };
  }

  private static String text(Element parent, String tag) {
    NodeList elements = parent.getElementsByTagName(tag);
    return elements.getLength() == 0 ? null : elements.item(0).getTextContent().trim();
  }

}
