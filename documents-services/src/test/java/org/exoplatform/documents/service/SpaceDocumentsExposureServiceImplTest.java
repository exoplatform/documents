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
package org.exoplatform.documents.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.documents.model.ExposedDocument;
import org.exoplatform.documents.model.ExposedDocumentPage;
import org.exoplatform.documents.model.PublicDocumentLink;
import org.exoplatform.documents.model.SpaceDocumentsExposure;
import org.exoplatform.documents.storage.PublicDocumentLinkStorage;
import org.exoplatform.documents.storage.SpaceDocumentsExposureStorage;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;

import io.meeds.social.space.security.SpaceSecurityAudit;
import io.meeds.social.space.security.service.SpaceSecurityService;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SpaceDocumentsExposureServiceImplTest {

  private static final long                 SPACE_ID       = 1L;

  private static final long                 OTHER_SPACE_ID = 2L;

  private static final String               MANAGER        = "manager";

  @Mock
  private SpaceService                      spaceService;

  @Mock
  private SpaceSecurityService              spaceSecurityService;

  private Space                             space;

  private FakeLinkStorage                   linkStorage;

  private FakeExposureStorage               exposureStorage;

  private SpaceDocumentsExposureServiceImpl service;

  private MockedStatic<SpaceSecurityAudit>  audit;

  @BeforeEach
  void setUp() {
    space = space(SPACE_ID);
    when(spaceService.getSpaceById(SPACE_ID)).thenReturn(space);
    when(spaceSecurityService.canManageSpace(space, MANAGER)).thenReturn(true);
    linkStorage = new FakeLinkStorage();
    exposureStorage = new FakeExposureStorage();
    service = new SpaceDocumentsExposureServiceImpl();
    ReflectionTestUtils.setField(service, "spaceService", spaceService);
    ReflectionTestUtils.setField(service, "spaceSecurityService", spaceSecurityService);
    ReflectionTestUtils.setField(service, "exposureStorage", exposureStorage);
    ReflectionTestUtils.setField(service, "publicDocumentLinkStorage", linkStorage);
    service.setLinksPageSize(2);
    audit = mockStatic(SpaceSecurityAudit.class);
  }

  @AfterEach
  void tearDown() {
    audit.close();
  }

  @Test
  void aTurnOnRemovesTheSpaceLinksAndTheirEveryoneAndLogsEachOne() {
    Doc linked = exposureStorage.add("linked", "Linked", SPACE_ID, "owner1", true, false);
    Doc otherSpace = exposureStorage.add("other", "Other", OTHER_SPACE_ID, "owner2", true, false);
    Doc unlinked = exposureStorage.add("unlinked", "Unlinked", SPACE_ID, "owner3", true, false);
    long linkedRow = linkStorage.add("linked");
    long otherRow = linkStorage.add("other");

    service.removePublicLinks(space, true, MANAGER);

    assertFalse(linkStorage.has(linkedRow));
    assertFalse(linked.everyone);
    assertTrue(linkStorage.has(otherRow));
    assertTrue(otherSpace.everyone);
    assertTrue(unlinked.everyone);
    audit.verify(() -> SpaceSecurityAudit.publicLinkRemoved(SPACE_ID, "linked", "owner1", MANAGER));
    audit.verify(() -> SpaceSecurityAudit.publicLinkRemoved(anyLong(), anyString(), nullable(String.class), anyString()),
                 times(1));
  }

  @Test
  void aTurnOffRemovesTheLeftoverLinksAndKeepsEveryone() {
    Doc linked = exposureStorage.add("linked", "Linked", SPACE_ID, "owner1", true, false);
    long linkedRow = linkStorage.add("linked");

    service.removePublicLinks(space, false, MANAGER);

    assertFalse(linkStorage.has(linkedRow));
    assertTrue(linked.everyone);
    audit.verify(() -> SpaceSecurityAudit.publicLinkRemoved(SPACE_ID, "linked", "owner1", MANAGER));
  }

  @Test
  void aDocumentMovedOutMidRunKeepsItsLinkAndItsEveryone() {
    Doc moved = exposureStorage.add("moved", "Moved", SPACE_ID, "owner1", true, false);
    Doc staying = exposureStorage.add("staying", "Staying", SPACE_ID, "owner2", true, false);
    long movedRow = linkStorage.add("moved");
    long stayingRow = linkStorage.add("staying");
    exposureStorage.afterSelection = selected -> moved.spaceId = OTHER_SPACE_ID;

    service.removePublicLinks(space, true, MANAGER);

    assertTrue(linkStorage.has(movedRow));
    assertTrue(moved.everyone);
    assertFalse(linkStorage.has(stayingRow));
    assertFalse(staying.everyone);
    audit.verify(() -> SpaceSecurityAudit.publicLinkRemoved(anyLong(), eq("moved"), nullable(String.class), anyString()),
                 never());
  }

  @Test
  void aLinkDeletedByItsOwnerMidRunIsSkippedAndTheWalkReachesTheNextLink() {
    exposureStorage.add("a", "A", SPACE_ID, "ownerA", false, false);
    exposureStorage.add("b", "B", SPACE_ID, "ownerB", false, false);
    exposureStorage.add("c", "C", SPACE_ID, "ownerC", false, false);
    long rowA = linkStorage.add("a");
    long rowB = linkStorage.add("b");
    long rowC = linkStorage.add("c");
    exposureStorage.beforePrepare = documentId -> {
      if ("a".equals(documentId)) {
        linkStorage.rows.remove(rowB);
      }
    };

    service.removePublicLinks(space, true, MANAGER);

    assertTrue(linkStorage.rows.isEmpty());
    assertEquals(List.of(0L, rowB), linkStorage.reads);
    assertTrue(linkStorage.deleted.containsAll(List.of(rowA, rowC)));
    audit.verify(() -> SpaceSecurityAudit.publicLinkRemoved(SPACE_ID, "a", "ownerA", MANAGER));
    audit.verify(() -> SpaceSecurityAudit.publicLinkRemoved(SPACE_ID, "c", "ownerC", MANAGER));
    audit.verify(() -> SpaceSecurityAudit.publicLinkRemoved(anyLong(), eq("b"), nullable(String.class), anyString()),
                 never());
  }

  @Test
  void aReCheckWhoseReadThrowsLeavesItsLinkAsItWasAndFailsTheChange() {
    exposureStorage.add("a", "A", SPACE_ID, "ownerA", true, false);
    Doc unreadable = exposureStorage.add("b", "B", SPACE_ID, "ownerB", true, false);
    Doc notReached = exposureStorage.add("c", "C", SPACE_ID, "ownerC", true, false);
    long rowA = linkStorage.add("a");
    long rowB = linkStorage.add("b");
    long rowC = linkStorage.add("c");
    exposureStorage.failingPrepare = "b";

    assertThrows(IllegalStateException.class, () -> service.removePublicLinks(space, true, MANAGER));

    assertFalse(linkStorage.has(rowA));
    assertTrue(linkStorage.has(rowB));
    assertTrue(unreadable.everyone);
    assertTrue(linkStorage.has(rowC));
    assertTrue(notReached.everyone);
  }

  @Test
  void aSelectionWhoseReadThrowsFailsTheChangeBeforeAnyRemoval() {
    exposureStorage.add("a", "A", SPACE_ID, "ownerA", true, false);
    long rowA = linkStorage.add("a");
    exposureStorage.failingSelection = true;

    assertThrows(IllegalStateException.class, () -> service.removePublicLinks(space, true, MANAGER));

    assertTrue(linkStorage.has(rowA));
  }

  @Test
  void theCountsSummarizeTheListsAndActivationTakesTheEveryoneLinkedDocumentOutOfBoth() throws Exception {
    exposureStorage.add("linkedAndShared", "Linked and shared", SPACE_ID, "o", false, true);
    exposureStorage.add("everyoneAndLinked", "Everyone and linked", SPACE_ID, "o", true, false);
    exposureStorage.add("sharedFolder", "Shared folder", SPACE_ID, "o", false, true);
    exposureStorage.add("everyoneOnly", "Everyone only", SPACE_ID, "o", true, false);
    exposureStorage.add("linkedOnly", "Linked only", SPACE_ID, "o", false, false);
    linkStorage.add("linkedAndShared");
    linkStorage.add("everyoneAndLinked");
    linkStorage.add("linkedOnly");

    SpaceDocumentsExposure before = service.getExposure(SPACE_ID, MANAGER);
    ExposedDocumentPage linkedBefore = service.getPublicLinkDocuments(SPACE_ID, 0, 10, MANAGER);

    assertEquals(new SpaceDocumentsExposure(3, 4), before);
    assertEquals(before.publicLinks(), linkedBefore.size());
    assertEquals(before.sharedOutside(), exposureStorage.getSharedOutsideDocuments(space).size());

    service.removePublicLinks(space, true, MANAGER);

    assertEquals(new SpaceDocumentsExposure(0, 3), service.getExposure(SPACE_ID, MANAGER));
    assertEquals(Set.of("linkedAndShared", "sharedFolder", "everyoneOnly"),
                 new HashSet<>(exposureStorage.getSharedOutsideDocuments(space).stream().map(ExposedDocument::id).toList()));
  }

  @Test
  void thePublicLinkListIsSortedByNameThenIdPagedAndTotalled() throws Exception {
    exposureStorage.add("id-3", "beta", SPACE_ID, "o", false, false);
    exposureStorage.add("id-2", "Alpha", SPACE_ID, "o", false, false);
    exposureStorage.add("id-1", "alpha", SPACE_ID, "o", false, false);
    exposureStorage.add("id-4", "gamma", OTHER_SPACE_ID, "o", false, false);
    linkStorage.add("id-3");
    linkStorage.add("id-2");
    linkStorage.add("id-4");
    linkStorage.add("id-1");
    linkStorage.add("id-1");

    ExposedDocumentPage firstPage = service.getPublicLinkDocuments(SPACE_ID, 0, 2, MANAGER);
    ExposedDocumentPage secondPage = service.getPublicLinkDocuments(SPACE_ID, 2, 2, MANAGER);
    ExposedDocumentPage beyond = service.getPublicLinkDocuments(SPACE_ID, 5, 2, MANAGER);

    assertEquals(List.of("id-1", "id-2"), firstPage.documents().stream().map(ExposedDocument::id).toList());
    assertEquals(3, firstPage.size());
    assertEquals(List.of("id-3"), secondPage.documents().stream().map(ExposedDocument::id).toList());
    assertTrue(beyond.documents().isEmpty());
    assertEquals(3, beyond.size());
  }

  @Test
  void anUnknownSpaceIsNotFound() {
    assertThrows(ObjectNotFoundException.class, () -> service.getExposure(9L, MANAGER));
    assertThrows(ObjectNotFoundException.class, () -> service.getPublicLinkDocuments(9L, 0, 10, MANAGER));
  }

  @Test
  void aUserWhoDoesNotManageTheSpaceIsRefusedBeforeAnyRead() {
    exposureStorage.add("linked", "Linked", SPACE_ID, "o", true, true);
    linkStorage.add("linked");

    assertThrows(IllegalAccessException.class, () -> service.getExposure(SPACE_ID, "member"));
    assertThrows(IllegalAccessException.class, () -> service.getPublicLinkDocuments(SPACE_ID, 0, 10, "member"));
    assertThrows(IllegalAccessException.class, () -> service.getExposure(SPACE_ID, null));
    assertTrue(linkStorage.reads.isEmpty());
    assertEquals(0, exposureStorage.sharedOutsideReads);
  }

  @Test
  void anInvalidPageIsRefusedOnceTheRightIsChecked() {
    assertThrows(IllegalArgumentException.class, () -> service.getPublicLinkDocuments(SPACE_ID, -1, 10, MANAGER));
    assertThrows(IllegalArgumentException.class, () -> service.getPublicLinkDocuments(SPACE_ID, 0, 0, MANAGER));
    assertThrows(IllegalAccessException.class, () -> service.getPublicLinkDocuments(SPACE_ID, -1, 0, "member"));
  }

  private static Space space(long id) {
    Space space = new Space();
    space.setId(String.valueOf(id));
    space.setGroupId("/spaces/space" + id);
    return space;
  }

  private static class Doc {
    final String id;

    final String name;

    final String owner;

    long         spaceId;

    boolean      everyone;

    boolean      sharedWithOthers;

    Doc(String id, String name, long spaceId, String owner, boolean everyone, boolean sharedWithOthers) {
      this.id = id;
      this.name = name;
      this.spaceId = spaceId;
      this.owner = owner;
      this.everyone = everyone;
      this.sharedWithOthers = sharedWithOthers;
    }

    ExposedDocument toExposedDocument() {
      return new ExposedDocument(id, name, "application/pdf", false, owner);
    }
  }

  private static class FakeExposureStorage implements SpaceDocumentsExposureStorage {
    final Map<String, Doc>       documents   = new LinkedHashMap<>();

    Consumer<Collection<String>> afterSelection = selected -> {
    };

    Consumer<String>             beforePrepare  = documentId -> {
    };

    String                       failingPrepare;

    boolean                      failingSelection;

    int                          sharedOutsideReads;

    Doc add(String id, String name, long spaceId, String owner, boolean everyone, boolean sharedWithOthers) {
      Doc document = new Doc(id, name, spaceId, owner, everyone, sharedWithOthers);
      documents.put(id, document);
      return document;
    }

    @Override
    public List<ExposedDocument> getSpaceDocuments(Space space, Collection<String> documentIds) {
      if (failingSelection) {
        throw new IllegalStateException("unreadable");
      }
      List<ExposedDocument> result = new ArrayList<>();
      for (String documentId : documentIds) {
        Doc document = documents.get(documentId);
        if (document != null && document.spaceId == Long.parseLong(space.getId())) {
          result.add(document.toExposedDocument());
        }
      }
      afterSelection.accept(documentIds);
      return result;
    }

    @Override
    public List<ExposedDocument> getSharedOutsideDocuments(Space space) {
      sharedOutsideReads++;
      return documents.values()
                      .stream()
                      .filter(document -> document.spaceId == Long.parseLong(space.getId()))
                      .filter(document -> document.everyone || document.sharedWithOthers)
                      .map(Doc::toExposedDocument)
                      .toList();
    }

    @Override
    public ExposedDocument prepareLinkRemoval(Space space, String documentId, boolean removeEveryone) {
      beforePrepare.accept(documentId);
      if (documentId.equals(failingPrepare)) {
        throw new IllegalStateException("unreadable");
      }
      Doc document = documents.get(documentId);
      if (document == null || document.spaceId != Long.parseLong(space.getId())) {
        return null;
      }
      if (removeEveryone) {
        document.everyone = false;
      }
      return document.toExposedDocument();
    }
  }

  private static class FakeLinkStorage extends PublicDocumentLinkStorage {
    final TreeMap<Long, String> rows    = new TreeMap<>();

    final List<Long>            reads   = new ArrayList<>();

    final List<Long>            deleted = new ArrayList<>();

    long                        nextId  = 10;

    long add(String nodeId) {
      long id = nextId++;
      rows.put(id, nodeId);
      return id;
    }

    boolean has(long id) {
      return rows.containsKey(id);
    }

    @Override
    public List<PublicDocumentLink> getLinksAfter(long lastId, int limit) {
      reads.add(lastId);
      return rows.tailMap(lastId, false)
                 .entrySet()
                 .stream()
                 .limit(limit)
                 .map(row -> new PublicDocumentLink(row.getKey(), row.getValue()))
                 .toList();
    }

    @Override
    public boolean deleteLink(long id) {
      boolean removed = rows.remove(id) != null;
      if (removed) {
        deleted.add(id);
      }
      return removed;
    }
  }

}
