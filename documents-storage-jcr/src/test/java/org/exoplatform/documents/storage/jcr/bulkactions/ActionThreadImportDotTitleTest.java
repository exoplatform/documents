/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see<http://www.gnu.org/licenses/>.
 */
package org.exoplatform.documents.storage.jcr.bulkactions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.jcr.Node;
import javax.jcr.Session;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.exoplatform.documents.model.ActionData;
import org.exoplatform.services.listener.ListenerService;

class ActionThreadImportDotTitleTest {

  @TempDir
  Path tempDir;

  @Test
  void createItemsLeavesOutEntriesTitledWithDots() throws Exception {
    List<String> entries = new ArrayList<>();
    entries.add(write(" .. /first.txt"));
    entries.add(write("docs/ . /second.txt"));
    entries.add(write("docs/ .. "));
    ActionData actionData = new ActionData();
    actionData.setConflict("duplicate");
    actionData.setFiles(entries);
    Node parent = mock(Node.class);
    ActionThread thread = new ActionThread(null,
                                           null,
                                           null,
                                           mock(ListenerService.class),
                                           null,
                                           actionData,
                                           parent,
                                           null,
                                           mock(Session.class),
                                           null,
                                           1L);

    thread.createItems(tempDir.toString());

    assertEquals(3, actionData.getIgnoredFiles().size());
    assertEquals(3, actionData.getImportedFilesCount());
    assertEquals(0, actionData.getFailedFiles().size());
    verify(parent, never()).addNode(anyString(), anyString());
  }

  /**
   * A blank that {@code StringUtils.strip} removes and the repository accepts
   * in a node name (here U+2003) lets a dot title through to the folder and
   * file title sites: only the entry check stops it. An entry padded with an
   * ASCII space is left out as well; it would otherwise fail on its node name.
   */
  @Test
  void createItemsLeavesOutEntriesTitledWithDotsPaddedWithAcceptedBlanks() throws Exception {
    List<String> entries = new ArrayList<>();
    entries.add(write("\u2003..\u2003/first.txt"));
    entries.add(write("docs/\u2003.\u2003/second.txt"));
    entries.add(write("docs/\u2003..\u2003"));
    entries.add(write("docs/\u000B.\u000B"));
    ActionData actionData = new ActionData();
    actionData.setConflict("duplicate");
    actionData.setFiles(entries);
    Node parent = mock(Node.class);
    ActionThread thread = new ActionThread(null,
                                           null,
                                           null,
                                           mock(ListenerService.class),
                                           null,
                                           actionData,
                                           parent,
                                           null,
                                           mock(Session.class),
                                           null,
                                           1L);

    thread.createItems(tempDir.toString());

    assertEquals(4, actionData.getIgnoredFiles().size());
    assertEquals(4, actionData.getImportedFilesCount());
    assertEquals(0, actionData.getFailedFiles().size());
    verify(parent, never()).addNode(anyString(), anyString());
  }

  private String write(String relativePath) throws Exception {
    Path file = tempDir.resolve(relativePath);
    Files.createDirectories(file.getParent());
    Files.writeString(file, "data");
    return file.toString();
  }
}
