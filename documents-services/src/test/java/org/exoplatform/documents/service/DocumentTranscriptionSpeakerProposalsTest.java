/*
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
package org.exoplatform.documents.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.exoplatform.documents.constant.TranscriptionStatus;
import org.exoplatform.documents.model.DocumentAudience;
import org.exoplatform.documents.model.TranscriptionQuote;
import org.exoplatform.documents.model.TranscriptionSpeakerCandidates;
import org.exoplatform.documents.model.TranscriptionSpeakerProposal;
import org.exoplatform.documents.model.TranscriptionSpeakerSuggestion;
import org.exoplatform.documents.model.TranscriptionState;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;

/**
 * The proposals of who the speakers of a recording are (EXO-92126): who may be
 * proposed, how a strong proposal is applied, and what an editor is shown.
 */
@ExtendWith(MockitoExtension.class)
class DocumentTranscriptionSpeakerProposalsTest {

  private static final String                  DOCUMENT_ID = "doc-uuid";

  private static final long                    EDITOR_ID   = 7L;

  private static final long                    OWNER_ID    = 9L;

  private static final long                    UPDATED     = 1_700_000_000_000L;

  private static final String                  SEGMENTS    = "[{\"start\":0.0,\"end\":2.0,\"text\":\"Julien, à toi.\",\"speaker\":\"1\"},"
      + "{\"start\":2.5,\"end\":3.0,\"text\":\"Oui.\",\"speaker\":\"2\"},"
      + "{\"start\":4.0,\"end\":5.0,\"text\":\"Merci Julien.\",\"speaker\":\"1\"}]";

  @Mock
  private DocumentFileService                  documentFileService;

  @Mock
  private MediaTranscriptionCapabilityRegistry capabilityRegistry;

  @Mock
  private ListenerService                      listenerService;

  @Mock
  private IdentityManager                      identityManager;

  @Mock
  private SpaceService                         spaceService;

  @InjectMocks
  private DocumentTranscriptionService         service;

  /**
   * When an add-on knows the recording's meeting, its attendees are the
   * candidates, enabled users only; a plugin that fails or knows nothing does
   * not count, and the file's audience is not read.
   */
  @Test
  void theCandidatesAreTheMeetingsAttendeesWhenAPluginKnowsThem() throws Exception {
    TranscriptionAttendeesPlugin knowing = mock(TranscriptionAttendeesPlugin.class);
    TranscriptionAttendeesPlugin failing = mock(TranscriptionAttendeesPlugin.class);
    TranscriptionAttendeesPlugin unaware = mock(TranscriptionAttendeesPlugin.class);
    when(knowing.getAttendees(DOCUMENT_ID)).thenReturn(Optional.of(List.of(42L, 43L, 44L)));
    when(failing.getAttendees(DOCUMENT_ID)).thenThrow(new IllegalStateException("down"));
    when(unaware.getAttendees(DOCUMENT_ID)).thenReturn(Optional.empty());
    setPlugins(failing, unaware, knowing);
    mockUser("42", true);
    mockUser("43", true);
    mockUser("44", false);

    assertEquals(new TranscriptionSpeakerCandidates(true, List.of(42L, 43L)), service.getSpeakerCandidates(DOCUMENT_ID));
    verify(documentFileService, never()).getDocumentAudience(anyString());
  }

  /**
   * With no meeting, the candidates are the file's owner, the users it is
   * shared with and the members of its space, each once, enabled users only.
   */
  @Test
  void withNoMeetingTheCandidatesAreTheOwnerTheSharesAndTheSpace() throws Exception {
    when(documentFileService.getDocumentAudience(DOCUMENT_ID)).thenReturn(new DocumentAudience("mary",
                                                                                                List.of("mary", "john"),
                                                                                                "/spaces/team"));
    Space space = mock(Space.class);
    when(space.getMembers()).thenReturn(new String[] { "paul", "mary", "anne" });
    when(spaceService.getSpaceByGroupId("/spaces/team")).thenReturn(space);
    mockUsername("mary", "1", true);
    mockUsername("john", "2", true);
    mockUsername("paul", "3", true);
    mockUsername("anne", "4", false);

    assertEquals(new TranscriptionSpeakerCandidates(false, List.of(1L, 2L, 3L)), service.getSpeakerCandidates(DOCUMENT_ID));
  }

  /**
   * A space with more members than the candidates allow is left out: a name
   * said would match too many people.
   */
  @Test
  void aLargeSpaceIsLeftOutOfTheCandidates() throws Exception {
    when(documentFileService.getDocumentAudience(DOCUMENT_ID)).thenReturn(new DocumentAudience("mary", List.of("john"), "/spaces/all"));
    Space space = mock(Space.class);
    String[] members = new String[DocumentTranscriptionService.MAX_CANDIDATES - 1];
    Arrays.fill(members, "someone");
    when(space.getMembers()).thenReturn(members);
    when(spaceService.getSpaceByGroupId("/spaces/all")).thenReturn(space);
    mockUsername("mary", "1", true);
    mockUsername("john", "2", true);
    Identity someone = mock(Identity.class);
    lenient().when(someone.getId()).thenReturn("5");
    lenient().when(identityManager.getOrCreateUserIdentity("someone")).thenReturn(someone);
    lenient().when(identityManager.getIdentity("5")).thenReturn(someone);
    lenient().when(someone.isUser()).thenReturn(true);
    lenient().when(someone.isEnable()).thenReturn(true);

    assertEquals(new TranscriptionSpeakerCandidates(false, List.of(1L, 2L)), service.getSpeakerCandidates(DOCUMENT_ID));
  }

  /**
   * Every proposal is kept before any speaker is named; then a strong one names
   * its speaker as the owner, on the transcript it was made from, and its stored
   * proposal is marked applied; a likely one is never applied.
   */
  @Test
  void aStrongProposalNamesItsSpeakerAsTheOwner_afterAllAreKept() throws Exception {
    mockNameableBy(OWNER_ID);
    mockUser("42", true);
    Identity other = mock(Identity.class);
    lenient().when(other.isUser()).thenReturn(true);
    lenient().when(other.isEnable()).thenReturn(true);
    lenient().when(identityManager.getIdentity("43")).thenReturn(other);
    when(documentFileService.saveTranscriptionSpeakerSuggestions(eq(DOCUMENT_ID), any(), eq(UPDATED))).thenReturn(true);
    when(documentFileService.nameTranscriptionSpeakerIfUnnamed(DOCUMENT_ID, "1", "42", UPDATED, OWNER_ID)).thenReturn(true);
    lenient().when(documentFileService.nameTranscriptionSpeakerIfUnnamed(DOCUMENT_ID, "2", "43", UPDATED, OWNER_ID)).thenReturn(true);
    when(documentFileService.markTranscriptionSpeakerSuggestionApplied(DOCUMENT_ID, "1", 42, UPDATED)).thenReturn(true);
    lenient().when(documentFileService.markTranscriptionSpeakerSuggestionApplied(DOCUMENT_ID, "2", 43, UPDATED)).thenReturn(true);
    TranscriptionSpeakerSuggestion strong = new TranscriptionSpeakerSuggestion("1", 42, true, false, List.of(0, 2));
    TranscriptionSpeakerSuggestion likely = new TranscriptionSpeakerSuggestion("2", 43, false, false, List.of(0));

    List<TranscriptionSpeakerSuggestion> kept = service.applySpeakerSuggestions(DOCUMENT_ID, List.of(strong, likely), OWNER_ID, UPDATED);

    assertEquals(List.of(strong.withAutoApplied(true), likely), kept);
    InOrder order = inOrder(documentFileService);
    order.verify(documentFileService).saveTranscriptionSpeakerSuggestions(DOCUMENT_ID, List.of(strong, likely), UPDATED);
    order.verify(documentFileService).nameTranscriptionSpeakerIfUnnamed(DOCUMENT_ID, "1", "42", UPDATED, OWNER_ID);
    order.verify(documentFileService).markTranscriptionSpeakerSuggestionApplied(DOCUMENT_ID, "1", 42, UPDATED);
    verify(documentFileService, never()).nameTranscriptionSpeakerIfUnnamed(DOCUMENT_ID, "2", "43", UPDATED, OWNER_ID);
  }

  /**
   * Nothing is kept nor named when the transcript changed since the proposals
   * were made from it.
   */
  @Test
  void proposalsForATranscriptMadeSinceAreDropped() throws Exception {
    TranscriptionSpeakerSuggestion strong = new TranscriptionSpeakerSuggestion("1", 42, true, false, List.of(0, 2));
    when(documentFileService.saveTranscriptionSpeakerSuggestions(DOCUMENT_ID, List.of(strong), UPDATED)).thenReturn(false);

    assertEquals(List.of(), service.applySpeakerSuggestions(DOCUMENT_ID, List.of(strong), OWNER_ID, UPDATED));
    verify(documentFileService, never()).nameTranscriptionSpeakerIfUnnamed(anyString(), anyString(), anyString(), anyLong(), anyLong());
  }

  /**
   * A strong proposal stays a proposal when the owner cannot edit the file,
   * when the speaker or the person is already named, when it was dismissed
   * while its speaker was being named, or when the owner is unknown.
   */
  @Test
  void aStrongProposalWaitsWhenItCannotBeApplied() throws Exception {
    TranscriptionSpeakerSuggestion strong = new TranscriptionSpeakerSuggestion("1", 42, true, false, List.of(0, 2));
    when(documentFileService.saveTranscriptionSpeakerSuggestions(DOCUMENT_ID, List.of(strong), UPDATED)).thenReturn(true);
    when(documentFileService.getTranscriptionState(DOCUMENT_ID, OWNER_ID)).thenReturn(done());
    when(documentFileService.hasEditPermissionOnDocument(DOCUMENT_ID, OWNER_ID)).thenReturn(false);

    assertEquals(List.of(strong), service.applySpeakerSuggestions(DOCUMENT_ID, List.of(strong), OWNER_ID, UPDATED));
    assertEquals(List.of(strong), service.applySpeakerSuggestions(DOCUMENT_ID, List.of(strong), null, UPDATED));
    verify(documentFileService, never()).nameTranscriptionSpeakerIfUnnamed(anyString(), anyString(), anyString(), anyLong(), anyLong());

    mockNameableBy(OWNER_ID);
    mockUser("42", true);
    when(documentFileService.nameTranscriptionSpeakerIfUnnamed(DOCUMENT_ID, "1", "42", UPDATED, OWNER_ID)).thenReturn(false);
    assertEquals(List.of(strong), service.applySpeakerSuggestions(DOCUMENT_ID, List.of(strong), OWNER_ID, UPDATED));
    verify(documentFileService, never()).markTranscriptionSpeakerSuggestionApplied(anyString(), anyString(), anyLong(), anyLong());

    when(documentFileService.nameTranscriptionSpeakerIfUnnamed(DOCUMENT_ID, "1", "42", UPDATED, OWNER_ID)).thenReturn(true);
    when(documentFileService.markTranscriptionSpeakerSuggestionApplied(DOCUMENT_ID, "1", 42, UPDATED)).thenReturn(false);
    assertEquals(List.of(strong), service.applySpeakerSuggestions(DOCUMENT_ID, List.of(strong), OWNER_ID, UPDATED));
  }

  /**
   * A proposal made by elimination is applied after the others, and only while
   * the voice it follows from is named after the person it was taken to be.
   */
  @Test
  void aProposalByEliminationIsAppliedOnlyWhileItsPremiseHolds() throws Exception {
    mockNameableBy(OWNER_ID);
    mockUser("42", true);
    mockUser("43", true);
    TranscriptionSpeakerSuggestion eliminated = new TranscriptionSpeakerSuggestion("2", 43, true, false, List.of(0), "1", 42L);
    TranscriptionSpeakerSuggestion found = new TranscriptionSpeakerSuggestion("1", 42, true, false, List.of(0, 2));
    when(documentFileService.saveTranscriptionSpeakerSuggestions(eq(DOCUMENT_ID), any(), eq(UPDATED))).thenReturn(true);
    when(documentFileService.nameTranscriptionSpeakerIfUnnamed(DOCUMENT_ID, "1", "42", UPDATED, OWNER_ID)).thenReturn(true);
    when(documentFileService.markTranscriptionSpeakerSuggestionApplied(DOCUMENT_ID, "1", 42, UPDATED)).thenReturn(true);
    when(documentFileService.getTranscriptionSpeakers(DOCUMENT_ID, OWNER_ID)).thenReturn(Map.of("1", "42"));
    when(documentFileService.nameTranscriptionSpeakerIfUnnamed(DOCUMENT_ID, "2", "43", UPDATED, OWNER_ID)).thenReturn(true);
    when(documentFileService.markTranscriptionSpeakerSuggestionApplied(DOCUMENT_ID, "2", 43, UPDATED)).thenReturn(true);

    assertEquals(List.of(eliminated.withAutoApplied(true), found.withAutoApplied(true)),
                 service.applySpeakerSuggestions(DOCUMENT_ID, List.of(eliminated, found), OWNER_ID, UPDATED));
    InOrder order = inOrder(documentFileService);
    order.verify(documentFileService).nameTranscriptionSpeakerIfUnnamed(DOCUMENT_ID, "1", "42", UPDATED, OWNER_ID);
    order.verify(documentFileService).nameTranscriptionSpeakerIfUnnamed(DOCUMENT_ID, "2", "43", UPDATED, OWNER_ID);

    when(documentFileService.getTranscriptionSpeakers(DOCUMENT_ID, OWNER_ID)).thenReturn(Map.of("1", "44"));
    assertEquals(List.of(eliminated, found.withAutoApplied(true)),
                 service.applySpeakerSuggestions(DOCUMENT_ID, List.of(eliminated, found), OWNER_ID, UPDATED));
    verify(documentFileService).nameTranscriptionSpeakerIfUnnamed(DOCUMENT_ID, "2", "43", UPDATED, OWNER_ID);
  }

  /**
   * An editor is shown a waiting proposal while neither its speaker nor its
   * person is named, and an applied one while its speaker is still named after
   * its person, each with the sentences that show it.
   */
  @Test
  void anEditorIsShownTheProposalsThatStillHold() throws Exception {
    when(documentFileService.hasEditPermissionOnDocument(DOCUMENT_ID, EDITOR_ID)).thenReturn(true);
    when(documentFileService.getTranscriptionSpeakerSuggestions(DOCUMENT_ID, EDITOR_ID)).thenReturn(List.of(
        new TranscriptionSpeakerSuggestion("1", 42, true, true, List.of(0, 2)),
        new TranscriptionSpeakerSuggestion("2", 43, false, false, List.of(1, 9), "1", 42L),
        new TranscriptionSpeakerSuggestion("3", 44, false, false, List.of(0)),
        new TranscriptionSpeakerSuggestion("4", 45, true, true, List.of(0)),
        new TranscriptionSpeakerSuggestion("6", 47, false, false, List.of(0))));
    when(documentFileService.getTranscriptionSpeakers(DOCUMENT_ID, EDITOR_ID)).thenReturn(Map.of("1", "42", "5", "44", "4", "46", "6", "48"));
    when(documentFileService.getTranscriptionSegments(DOCUMENT_ID, EDITOR_ID)).thenReturn(SEGMENTS);

    assertEquals(List.of(new TranscriptionSpeakerProposal("1",
                                                          42,
                                                          true,
                                                          true,
                                                          List.of(new TranscriptionQuote(0, "1", "Julien, à toi."),
                                                                  new TranscriptionQuote(4, "1", "Merci Julien.")),
                                                          null),
                         new TranscriptionSpeakerProposal("2", 43, false, false, List.of(new TranscriptionQuote(2.5, "2", "Oui.")), "1")),
                 service.getSpeakerProposals(DOCUMENT_ID, EDITOR_ID));
  }

  /**
   * A user who cannot edit the file is shown no proposal, and cannot dismiss
   * one; an editor dismisses with their own rights.
   */
  @Test
  void proposalsAreForEditorsOnly() throws Exception {
    when(documentFileService.hasEditPermissionOnDocument(DOCUMENT_ID, EDITOR_ID)).thenReturn(false);

    assertThrows(IllegalAccessException.class, () -> service.getSpeakerProposals(DOCUMENT_ID, EDITOR_ID));
    assertThrows(IllegalAccessException.class, () -> service.dismissSpeakerProposal(DOCUMENT_ID, "1", EDITOR_ID));
    verify(documentFileService, never()).getTranscriptionSpeakerSuggestions(anyString(), anyLong());
    verify(documentFileService, never()).removeTranscriptionSpeakerSuggestion(anyString(), anyString(), anyLong());

    when(documentFileService.hasEditPermissionOnDocument(DOCUMENT_ID, EDITOR_ID)).thenReturn(true);
    service.dismissSpeakerProposal(DOCUMENT_ID, "1", EDITOR_ID);
    verify(documentFileService).removeTranscriptionSpeakerSuggestion(DOCUMENT_ID, "1", EDITOR_ID);
  }

  private void mockNameableBy(long identityId) throws Exception {
    when(documentFileService.getTranscriptionState(DOCUMENT_ID, identityId)).thenReturn(done());
    when(documentFileService.hasEditPermissionOnDocument(DOCUMENT_ID, identityId)).thenReturn(true);
    when(documentFileService.getTranscriptionSegments(DOCUMENT_ID, identityId)).thenReturn(SEGMENTS);
  }

  private void mockUser(String identityId, boolean enabled) {
    Identity identity = mock(Identity.class);
    lenient().when(identity.getId()).thenReturn(identityId);
    lenient().when(identity.isUser()).thenReturn(true);
    lenient().when(identity.isEnable()).thenReturn(enabled);
    when(identityManager.getIdentity(identityId)).thenReturn(identity);
  }

  private void mockUsername(String username, String identityId, boolean enabled) {
    Identity identity = mock(Identity.class);
    when(identity.getId()).thenReturn(identityId);
    when(identityManager.getOrCreateUserIdentity(username)).thenReturn(identity);
    mockUser(identityId, enabled);
  }

  private void setPlugins(TranscriptionAttendeesPlugin... plugins) throws Exception {
    Field field = DocumentTranscriptionService.class.getDeclaredField("attendeesPlugins");
    field.setAccessible(true);
    field.set(service, List.of(plugins));
  }

  private static TranscriptionState done() {
    return TranscriptionState.builder().status(TranscriptionStatus.DONE).media(true).build();
  }

}
