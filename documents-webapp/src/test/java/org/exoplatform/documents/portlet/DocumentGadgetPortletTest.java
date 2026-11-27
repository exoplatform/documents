/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.exoplatform.documents.portlet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.portlet.ActionRequest;
import javax.portlet.ActionResponse;
import javax.portlet.PortletException;
import javax.portlet.PortletPreferences;

import org.junit.jupiter.api.Test;

import org.exoplatform.services.security.Identity;

class DocumentGadgetPortletTest {

  private static final String SETTING_NAME = "documentGadget-1";

  private final ActionRequest      request     = mock(ActionRequest.class);

  private final ActionResponse     response    = mock(ActionResponse.class);

  private final PortletPreferences preferences = mock(PortletPreferences.class);

  private boolean                  canEdit;

  private final DocumentGadgetPortlet portlet = new DocumentGadgetPortlet() {
                                                @Override
                                                protected boolean canEdit(String name, Identity identity) {
                                                  return canEdit;
                                                }
                                              };

  private void post(Map<String, String> parameters) {
    when(request.getPreferences()).thenReturn(preferences);
    when(preferences.getValue("name", null)).thenReturn(SETTING_NAME);
    when(request.getParameterNames()).thenReturn(Collections.enumeration(parameters.keySet()));
    parameters.forEach((name, value) -> when(request.getParameter(name)).thenReturn(value));
  }

  private static Map<String, String> validSettings() {
    Map<String, String> settings = new LinkedHashMap<>();
    settings.put("viewOptions", "cards");
    settings.put("documentType", "sharedWithMe");
    settings.put("maxDocumentsToList", "10");
    settings.put("customHeader", "true");
    settings.put("displaySeeMore", "false");
    settings.put("categoryIds", "[\"12\",3]");
    settings.put("excludeCategoryIds", "[]");
    settings.put("selectedFoldersId", "4f2a9c10-aa11-4b7e-9d3c-0123456789ab");
    settings.put("spaceIdentityId", "25");
    settings.put("displayAccessDrive", "true");
    settings.put("driveUrl", "/portal/dw/drive");
    settings.put("opensInSameTab", "true");
    return settings;
  }

  @Test
  void settingsAreNotStoredWithoutTheEditRight() throws Exception {
    canEdit = false;
    post(validSettings());

    assertThrows(PortletException.class, () -> portlet.processAction(request, response));

    verify(preferences, never()).setValue(anyString(), any());
    verify(preferences, never()).store();
  }

  @Test
  void settingsAreNotStoredWhenTheGadgetHasNoName() throws Exception {
    canEdit = true;
    Map<String, String> settings = validSettings();
    post(settings);
    for (String name : new String[] { null, "", "  " }) {
      when(preferences.getValue("name", null)).thenReturn(name);

      assertThrows(PortletException.class, () -> portlet.processAction(request, response), "name=" + name);
    }

    verify(preferences, never()).setValue(anyString(), any());
    verify(preferences, never()).store();
  }

  @Test
  void validSettingsAreStoredByAnEditor() throws Exception {
    canEdit = true;
    Map<String, String> settings = validSettings();
    post(settings);

    portlet.processAction(request, response);

    for (Map.Entry<String, String> setting : settings.entrySet()) {
      verify(preferences).setValue(setting.getKey(), setting.getValue());
    }
    verify(preferences).store();
  }

  @Test
  void unknownParametersAreNotStored() throws Exception {
    canEdit = true;
    Map<String, String> settings = validSettings();
    settings.put("canEdit", "true");
    settings.put("headerTitle", "x");
    settings.put("name", "other");
    post(settings);

    portlet.processAction(request, response);

    verify(preferences, never()).setValue(org.mockito.ArgumentMatchers.eq("canEdit"), any());
    verify(preferences, never()).setValue(org.mockito.ArgumentMatchers.eq("headerTitle"), any());
    verify(preferences, never()).setValue(org.mockito.ArgumentMatchers.eq("name"), any());
    verify(preferences).store();
  }

  @Test
  void aMalformedValueRefusesTheWholeSaveAndStoresNothing() throws Exception {
    canEdit = true;
    String[][] malformed = {
      { "viewOptions", "grid" },
      { "documentType", "recent'Document" },
      { "maxDocumentsToList", "abc" },
      { "maxDocumentsToList", "101" },
      { "maxDocumentsToList", "-1" },
      { "customHeader", "yes" },
      { "displaySeeMore", "1" },
      { "displayAccessDrive", "" },
      { "opensInSameTab", "true;" },
      { "categoryIds", "not json" },
      { "categoryIds", "{\"a\":1}" },
      { "categoryIds", "[\"1\",\"a`b\"]" },
      { "categoryIds", "[1] trailing" },
      { "categoryIds", "[[1]]" },
      { "excludeCategoryIds", "[\"a\"]" },
      { "excludeCategoryIds", "\"[]\"" },
      { "selectedFoldersId", "ab'cd" },
      { "spaceIdentityId", "12a" },
      { "driveUrl", "ftp://example.com/drive" },
      { "driveUrl", "Mailto:someone@example.com" },
      { "driveUrl", "data:text/plain,x" },
      { "driveUrl", " ftp:x" },
      { "driveUrl", "f\ttp:x" },
      { "driveUrl", "\u0001data:text/plain,x" },
      { "driveUrl", "httpsx:example" },
    };
    for (String[] entry : malformed) {
      Map<String, String> settings = validSettings();
      settings.put(entry[0], entry[1]);
      post(settings);

      assertThrows(PortletException.class, () -> portlet.processAction(request, response), entry[0] + "=" + entry[1]);
    }
    verify(preferences, never()).setValue(anyString(), any());
    verify(preferences, never()).store();
  }

  @Test
  void aStoredValueOfTheWrongShapeFallsBackToItsDefault() {
    assertEquals("list", DocumentGadgetSettings.valueOrDefault("viewOptions", "x'", "list"));
    assertEquals("[]", DocumentGadgetSettings.valueOrDefault("categoryIds", "[1", "[]"));
    assertEquals("4", DocumentGadgetSettings.valueOrDefault("maxDocumentsToList", "abc", "4"));
    assertEquals("cards", DocumentGadgetSettings.valueOrDefault("viewOptions", "cards", "list"));
    assertFalse(DocumentGadgetSettings.isValid("viewOptions", null));
    assertTrue(DocumentGadgetSettings.isValid("driveUrl", ""));
    // the drive link field is free text: a path with a query and an external web link stay accepted
    assertTrue(DocumentGadgetSettings.isValid("driveUrl", "/portal/s/12/documents?folderId=ab-12"));
    assertTrue(DocumentGadgetSettings.isValid("driveUrl", "https://intranet.example.com/drive"));
    assertTrue(DocumentGadgetSettings.isValid("driveUrl", "HTTP://intranet.example.com/a b|c"));
    assertTrue(DocumentGadgetSettings.isValid("driveUrl", "/portal/dw/drive?folder=My Folder"));
    assertTrue(DocumentGadgetSettings.isValid("driveUrl", "intranet.example.com/drive"));
    assertTrue(DocumentGadgetSettings.isValid("driveUrl", "10.0.0.5:8080/drive"));
    assertTrue(DocumentGadgetSettings.isValid("driveUrl", "My Folder:Reports"));
    assertTrue(DocumentGadgetSettings.isValid("driveUrl", " https://intranet.example.com/drive"));
  }
}
