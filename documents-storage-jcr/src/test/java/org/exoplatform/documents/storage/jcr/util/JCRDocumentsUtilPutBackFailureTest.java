/**
 * Copyright (C) 2026 eXo Platform SAS
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
package org.exoplatform.documents.storage.jcr.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.jcr.ItemNotFoundException;
import javax.jcr.RepositoryException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import org.exoplatform.services.jcr.access.AccessControlList;
import org.exoplatform.services.jcr.access.PermissionType;
import org.exoplatform.services.jcr.core.ExtendedNode;
import org.exoplatform.services.jcr.core.ExtendedSession;
import org.exoplatform.services.jcr.core.ManageableRepository;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/**
 * Putting the access lists back after a restore takes several saves: when one
 * fails, the log names the nodes whose list is not put back yet, with the
 * error, and the error reaches the caller.
 */
class JCRDocumentsUtilPutBackFailureTest {

  private static final String         PRIVILEGEABLE = "exo:privilegeable";

  private static final String         WORKSPACE     = "collaboration";

  private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

  private Logger                      logger;

  private ManageableRepository        repository;

  private ExtendedSession             systemSession;

  private Map<String, Map<String, String[]>> descendantsPermissions;

  @BeforeEach
  void setUp() throws Exception {
    logger = (Logger) LoggerFactory.getLogger(JCRDocumentsUtil.class);
    appender.start();
    logger.addAppender(appender);

    repository = mock(ManageableRepository.class);
    systemSession = mock(ExtendedSession.class);
    when(repository.getSystemSession(WORKSPACE)).thenReturn(systemSession);
    ExtendedNode document = mock(ExtendedNode.class);
    when(systemSession.getNodeByUUID("document-id")).thenReturn(document);
    when(document.isNodeType(PRIVILEGEABLE)).thenReturn(true);
    AccessControlList documentAcl = mock(AccessControlList.class);
    when(documentAcl.getPermissionEntries()).thenReturn(List.of());
    when(document.getACL()).thenReturn(documentAcl);
    // Below the document: one node gets its list back, two lose the list of their own
    ExtendedNode keptListNode = mock(ExtendedNode.class);
    ExtendedNode firstRemovedListNode = mock(ExtendedNode.class);
    ExtendedNode secondRemovedListNode = mock(ExtendedNode.class);
    when(systemSession.getNodeByIdentifier("kept-list-id")).thenReturn(keptListNode);
    when(systemSession.getNodeByIdentifier("first-removed-list-id")).thenReturn(firstRemovedListNode);
    when(systemSession.getNodeByIdentifier("second-removed-list-id")).thenReturn(secondRemovedListNode);
    when(keptListNode.isNodeType(PRIVILEGEABLE)).thenReturn(true);
    when(firstRemovedListNode.isNodeType(PRIVILEGEABLE)).thenReturn(true);
    when(secondRemovedListNode.isNodeType(PRIVILEGEABLE)).thenReturn(true);
    when(firstRemovedListNode.getParent()).thenReturn(document);
    when(secondRemovedListNode.getParent()).thenReturn(document);
    // and one the restore did not bring back
    when(systemSession.getNodeByIdentifier("gone-id")).thenThrow(new ItemNotFoundException("gone-id"));
    descendantsPermissions = new LinkedHashMap<>();
    descendantsPermissions.put("gone-id", Map.of("*:/spaces/a", new String[] { PermissionType.READ }));
    descendantsPermissions.put("kept-list-id", Map.of("*:/spaces/a", new String[] { PermissionType.READ }));
    descendantsPermissions.put("first-removed-list-id", null);
    descendantsPermissions.put("second-removed-list-id", null);
  }

  @AfterEach
  void tearDown() {
    logger.detachAppender(appender);
    appender.stop();
  }

  @Test
  void aFailedRemovalLogsTheNodesWhoseListIsNotPutBackYetWithTheError() throws Exception {
    RepositoryException failure = new RepositoryException("refused");
    // the document's list, the lists below it and the first removal (two saves) pass, the second removal fails
    doNothing().doNothing().doNothing().doNothing().doThrow(failure).when(systemSession).save();

    RepositoryException thrown = assertThrows(RepositoryException.class, () -> putBack());

    assertSame(failure, thrown);
    ILoggingEvent event = theOnlyError();
    String message = event.getFormattedMessage();
    assertTrue(message.contains("document document-id"), message);
    assertTrue(message.contains("the nodes [second-removed-list-id]"), message);
    assertEquals("refused", event.getThrowableProxy().getMessage());
  }

  @Test
  void aFailedSaveOfTheListsBelowLogsEveryNodeBelowWithTheError() throws Exception {
    RepositoryException failure = new RepositoryException("refused");
    // the document's list, then the lists below it fail
    doNothing().doThrow(failure).when(systemSession).save();

    RepositoryException thrown = assertThrows(RepositoryException.class, () -> putBack());

    assertSame(failure, thrown);
    ILoggingEvent event = theOnlyError();
    String message = event.getFormattedMessage();
    // not the document, whose list is saved, nor the node the restore did not bring back
    assertTrue(message.contains("the nodes [kept-list-id, first-removed-list-id, second-removed-list-id]"), message);
    assertEquals("refused", event.getThrowableProxy().getMessage());
  }

  private void putBack() throws RepositoryException {
    JCRDocumentsUtil.putBackOwnPermissions(repository,
                                           WORKSPACE,
                                           "document-id",
                                           Map.of("*:/spaces/a", new String[] { PermissionType.READ }),
                                           descendantsPermissions);
  }

  private ILoggingEvent theOnlyError() {
    List<ILoggingEvent> errors = appender.list.stream().filter(event -> event.getLevel() == Level.ERROR).toList();
    assertEquals(1, errors.size(), "one error logged: " + appender.list);
    return errors.get(0);
  }
}
