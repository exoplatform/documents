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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.documents.constant.TranscriptionStatus;
import org.exoplatform.documents.model.TranscriptionState;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;

@ExtendWith(MockitoExtension.class)
class DocumentTranscriptionServiceTest {

  private static final String                  DOCUMENT_ID = "doc-uuid";

  private static final long                    VIEWER_ID   = 7L;

  private static final String                  VIEWER      = "john";

  @Mock
  private DocumentFileService                  documentFileService;

  @Mock
  private MediaTranscriptionCapabilityRegistry capabilityRegistry;

  @Mock
  private ListenerService                      listenerService;

  @Spy
  private List<TranscriptChaptersRequester>    chaptersRequesters = new ArrayList<>();

  @Mock
  private TranscriptChaptersRequester          requester;

  @Mock
  private IdentityManager                      identityManager;

  @InjectMocks
  private DocumentTranscriptionService         service;

  @Test
  void retryIsRefusedWhenNoTranscriberIsInstalled() throws Exception {
    when(capabilityRegistry.isAvailable()).thenReturn(false);

    IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                                              () -> service.requestRetry(DOCUMENT_ID, VIEWER_ID, VIEWER));

    assertEquals(DocumentTranscriptionService.UNAVAILABLE_MESSAGE, e.getMessage());
    verifyNoInteractions(documentFileService);
    verifyNoInteractions(listenerService);
  }

  @Test
  void retryIsRefusedForAFileThatIsNoMedia() throws Exception {
    mockState(TranscriptionState.builder().media(false).build());

    IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                                              () -> service.requestRetry(DOCUMENT_ID, VIEWER_ID, VIEWER));

    assertEquals(DocumentTranscriptionService.NOT_MEDIA_MESSAGE, e.getMessage());
    verifyNoInteractions(listenerService);
  }

  @Test
  void retryIsRefusedForATranscribedFile() throws Exception {
    mockState(TranscriptionState.builder().status(TranscriptionStatus.DONE).media(true).build());

    IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                                              () -> service.requestRetry(DOCUMENT_ID, VIEWER_ID, VIEWER));

    assertEquals(DocumentTranscriptionService.ALREADY_DONE_MESSAGE, e.getMessage());
    verifyNoInteractions(listenerService);
  }

  @Test
  void retryHandsAFailedFileToTheTranscriber() throws Exception {
    mockState(TranscriptionState.builder().status(TranscriptionStatus.FAILED).error("provider.timeout").media(true).build());

    assertTrue(service.requestRetry(DOCUMENT_ID, VIEWER_ID, VIEWER));

    verify(listenerService).broadcast(DocumentTranscriptionService.TRANSCRIPTION_RETRY_REQUESTED_EVENT, VIEWER, DOCUMENT_ID);
  }

  @Test
  void retryHandsAFileNeverTranscribedToTheTranscriber() throws Exception {
    mockState(TranscriptionState.builder().media(true).build());

    assertTrue(service.requestRetry(DOCUMENT_ID, VIEWER_ID, VIEWER));

    verify(listenerService).broadcast(DocumentTranscriptionService.TRANSCRIPTION_RETRY_REQUESTED_EVENT, VIEWER, DOCUMENT_ID);
  }

  @Test
  void retryLeavesAQueuedOrRunningTranscriptionAlone() throws Exception {
    long now = System.currentTimeMillis();
    mockState(TranscriptionState.builder().status(TranscriptionStatus.QUEUED).updated(now).media(true).build());
    assertFalse(service.requestRetry(DOCUMENT_ID, VIEWER_ID, VIEWER));

    mockState(TranscriptionState.builder().status(TranscriptionStatus.PROCESSING).progress("3/9").updated(now).media(true).build());
    assertFalse(service.requestRetry(DOCUMENT_ID, VIEWER_ID, VIEWER));

    verify(listenerService, never()).broadcast(anyString(), any(), any());
  }

  @Test
  void retryHandsAStaleRunToTheTranscriberAgain() throws Exception {
    long stale = System.currentTimeMillis() - DocumentTranscriptionService.STALE_TRANSCRIPTION_MILLIS - 1000;
    mockState(TranscriptionState.builder().status(TranscriptionStatus.PROCESSING).updated(stale).media(true).build());
    assertTrue(service.requestRetry(DOCUMENT_ID, VIEWER_ID, VIEWER));

    mockState(TranscriptionState.builder().status(TranscriptionStatus.QUEUED).media(true).build());
    assertTrue(service.requestRetry(DOCUMENT_ID, VIEWER_ID, VIEWER), "Expected a queued state with no update time to be retried");
  }

  @Test
  void theTextIsReadOnlyOnceTranscribed() throws Exception {
    when(documentFileService.getTranscriptionState(DOCUMENT_ID, VIEWER_ID)).thenReturn(TranscriptionState.builder()
                                                                                                         .status(TranscriptionStatus.PROCESSING)
                                                                                                         .build());
    assertNull(service.getText(DOCUMENT_ID, VIEWER_ID));
    verify(documentFileService, never()).getAudioTranscription(anyString(), anyLong());

    when(documentFileService.getTranscriptionState(DOCUMENT_ID, VIEWER_ID)).thenReturn(TranscriptionState.builder()
                                                                                                         .status(TranscriptionStatus.DONE)
                                                                                                         .build());
    when(documentFileService.getAudioTranscription(DOCUMENT_ID, VIEWER_ID)).thenReturn("hello");
    assertEquals("hello", service.getText(DOCUMENT_ID, VIEWER_ID));
  }

  /**
   * A transcript read with timed segments and no chapters is handed to what
   * makes chapters, whose answer tells whether they are coming (EXO-92122).
   */
  @Test
  void aTranscriptReadWithNoChaptersAsksForThemAndTellsWhetherTheyAreComing() throws Exception {
    chaptersRequesters.add(requester);
    mockState(TranscriptionState.builder().status(TranscriptionStatus.DONE).hasSegments(true).media(true).build());

    when(requester.requestChapters(DOCUMENT_ID)).thenReturn(true);
    assertTrue(service.requestChapters(DOCUMENT_ID, VIEWER_ID));

    when(requester.requestChapters(DOCUMENT_ID)).thenReturn(false);
    assertFalse(service.requestChapters(DOCUMENT_ID, VIEWER_ID));
  }

  /**
   * With nothing to make chapters, none are coming (EXO-92122): no requester,
   * and no list at all, which is what an optional injection leaves when the
   * transcriber is not installed.
   */
  @Test
  void noChaptersAreComingWithoutAnythingToMakeThem() throws Exception {
    mockState(TranscriptionState.builder().status(TranscriptionStatus.DONE).hasSegments(true).media(true).build());
    assertFalse(service.requestChapters(DOCUMENT_ID, VIEWER_ID));

    Field requesters = DocumentTranscriptionService.class.getDeclaredField("chaptersRequesters");
    requesters.setAccessible(true);
    requesters.set(service, null);
    assertFalse(service.requestChapters(DOCUMENT_ID, VIEWER_ID));
  }

  /**
   * Every requester is asked, whatever an earlier one answered, and
   * the chapters are coming when one of them says so (EXO-92122).
   */
  @Test
  void everyRequesterIsAskedAndOneComingIsEnough() throws Exception {
    TranscriptChaptersRequester other = mock(TranscriptChaptersRequester.class);
    chaptersRequesters.add(requester);
    chaptersRequesters.add(other);
    mockState(TranscriptionState.builder().status(TranscriptionStatus.DONE).hasSegments(true).media(true).build());
    when(requester.requestChapters(DOCUMENT_ID)).thenReturn(true);
    when(other.requestChapters(DOCUMENT_ID)).thenReturn(false);

    assertTrue(service.requestChapters(DOCUMENT_ID, VIEWER_ID));

    verify(requester).requestChapters(DOCUMENT_ID);
    verify(other).requestChapters(DOCUMENT_ID);
  }

  /**
   * Nothing is asked for a transcript that has chapters, has no timed segments
   * or is not done, nor without a transcriber (EXO-92122).
   */
  @Test
  void noChaptersAreAskedForATranscriptThatCannotGetThem() throws Exception {
    chaptersRequesters.add(requester);
    mockState(TranscriptionState.builder().status(TranscriptionStatus.DONE).hasSegments(true).hasChapters(true).media(true).build());
    assertFalse(service.requestChapters(DOCUMENT_ID, VIEWER_ID));
    mockState(TranscriptionState.builder().status(TranscriptionStatus.DONE).media(true).build());
    assertFalse(service.requestChapters(DOCUMENT_ID, VIEWER_ID));
    mockState(TranscriptionState.builder().status(TranscriptionStatus.PROCESSING).hasSegments(true).media(true).build());
    assertFalse(service.requestChapters(DOCUMENT_ID, VIEWER_ID));
    when(capabilityRegistry.isAvailable()).thenReturn(false);
    assertFalse(service.requestChapters(DOCUMENT_ID, VIEWER_ID));

    verifyNoInteractions(requester);
  }

  /** Segments of a two-speaker transcript, as the transcription writes them. */
  private static final String SPEAKER_SEGMENTS = "[{\"start\":0.0,\"end\":2.0,\"text\":\"Hello.\",\"speaker\":\"1\"},"
      + "{\"start\":2.0,\"end\":3.0,\"text\":\"Yes.\",\"speaker\":\"2\"}]";

  /**
   * A user who cannot edit the file is refused before anything else is
   * checked, so a refusal never tells what the transcript holds (EXO-92123).
   */
  @Test
  void namingASpeakerIsRefusedToAUserWhoCannotEditTheFile() throws Exception {
    when(documentFileService.getTranscriptionState(DOCUMENT_ID, VIEWER_ID)).thenReturn(done());
    when(documentFileService.hasEditPermissionOnDocument(DOCUMENT_ID, VIEWER_ID)).thenReturn(false);

    assertThrows(IllegalAccessException.class, () -> service.setSpeaker(DOCUMENT_ID, "9", 42L, VIEWER_ID));

    verify(documentFileService, never()).getTranscriptionSegments(anyString(), anyLong());
    verify(documentFileService, never()).setTranscriptionSpeaker(anyString(), anyString(), any(), anyLong());
  }

  /**
   * A file the user cannot see is not found, whatever else the request says.
   */
  @Test
  void namingASpeakerOfAFileTheUserCannotSeeIsNotFound() throws Exception {
    when(documentFileService.getTranscriptionState(DOCUMENT_ID, VIEWER_ID)).thenThrow(new ObjectNotFoundException(DOCUMENT_ID));

    assertThrows(ObjectNotFoundException.class, () -> service.setSpeaker(DOCUMENT_ID, "1", 42L, VIEWER_ID));

    verify(documentFileService, never()).hasEditPermissionOnDocument(anyString(), anyLong());
  }

  /**
   * Only a speaker the transcript holds can be named, and only in a transcript
   * that tells its speakers apart.
   */
  @Test
  void onlyASpeakerOfADiarizedTranscriptCanBeNamed() throws Exception {
    when(documentFileService.hasEditPermissionOnDocument(DOCUMENT_ID, VIEWER_ID)).thenReturn(true);
    when(documentFileService.getTranscriptionState(DOCUMENT_ID, VIEWER_ID)).thenReturn(done());
    when(documentFileService.getTranscriptionSegments(DOCUMENT_ID, VIEWER_ID)).thenReturn(SPEAKER_SEGMENTS);
    assertEquals(DocumentTranscriptionService.UNKNOWN_SPEAKER_MESSAGE,
                 assertThrows(IllegalArgumentException.class, () -> service.setSpeaker(DOCUMENT_ID, "3", 42L, VIEWER_ID)).getMessage());

    when(documentFileService.getTranscriptionSegments(DOCUMENT_ID, VIEWER_ID)).thenReturn("[{\"start\":0.0,\"end\":2.0,\"text\":\"Hello.\"}]");
    assertEquals(DocumentTranscriptionService.NO_SPEAKERS_MESSAGE,
                 assertThrows(IllegalArgumentException.class, () -> service.setSpeaker(DOCUMENT_ID, "1", 42L, VIEWER_ID)).getMessage());

    lenient().when(documentFileService.getTranscriptionSegments(DOCUMENT_ID, VIEWER_ID)).thenReturn(SPEAKER_SEGMENTS);
    when(documentFileService.getTranscriptionState(DOCUMENT_ID, VIEWER_ID)).thenReturn(TranscriptionState.builder()
                                                                                                         .status(TranscriptionStatus.PROCESSING)
                                                                                                         .build());
    assertEquals(DocumentTranscriptionService.NO_SPEAKERS_MESSAGE,
                 assertThrows(IllegalArgumentException.class, () -> service.setSpeaker(DOCUMENT_ID, "1", 42L, VIEWER_ID)).getMessage(),
                 "Expected no naming while the transcript is being written");
    verify(documentFileService, never()).setTranscriptionSpeaker(anyString(), anyString(), any(), anyLong());
  }

  /**
   * A speaker can be said to be an enabled user only: not a space, nor a
   * deleted or disabled user, nor an identity that does not exist.
   */
  @Test
  void aSpeakerIsNamedAfterAnEnabledUserOnly() throws Exception {
    mockNameableTranscript();
    Identity space = identity(false, false, true);
    Identity deleted = identity(true, true, true);
    Identity disabled = identity(true, false, false);
    when(identityManager.getIdentity("41")).thenReturn(null);
    when(identityManager.getIdentity("43")).thenReturn(space);
    when(identityManager.getIdentity("44")).thenReturn(deleted);
    when(identityManager.getIdentity("45")).thenReturn(disabled);

    for (long refused : new long[] { 41L, 43L, 44L, 45L }) {
      assertEquals(DocumentTranscriptionService.NOT_A_USER_MESSAGE,
                   assertThrows(IllegalArgumentException.class, () -> service.setSpeaker(DOCUMENT_ID, "1", refused, VIEWER_ID)).getMessage(),
                   "Expected identity " + refused + " refused");
    }
    verify(documentFileService, never()).setTranscriptionSpeaker(anyString(), anyString(), any(), anyLong());
  }

  /**
   * A speaker is named after an enabled user, and unnamed without any person
   * to check.
   */
  @Test
  void aSpeakerIsNamedAfterAnEnabledUser_andUnnamed() throws Exception {
    mockNameableTranscript();
    Identity user = identity(true, false, true);
    when(identityManager.getIdentity("42")).thenReturn(user);

    service.setSpeaker(DOCUMENT_ID, "2", 42L, VIEWER_ID);
    service.setSpeaker(DOCUMENT_ID, "2", null, VIEWER_ID);

    verify(documentFileService).setTranscriptionSpeaker(DOCUMENT_ID, "2", "42", VIEWER_ID);
    verify(documentFileService).setTranscriptionSpeaker(DOCUMENT_ID, "2", null, VIEWER_ID);
  }

  /**
   * A guest with no account is named by a plain name, trimmed and stored
   * after the guest prefix with no escaping; a name of exactly the most
   * characters allowed, counted as characters and not as UTF-16 units, is
   * kept (EXO-92123).
   */
  @Test
  void aSpeakerIsNamedAfterAGuest() throws Exception {
    mockNameableTranscript();
    String longest = "é".repeat(79) + "\uD83D\uDE00";

    service.setGuestSpeaker(DOCUMENT_ID, "2", "  Jean-Luc <b>=Ö  ", VIEWER_ID);
    service.setGuestSpeaker(DOCUMENT_ID, "1", longest, VIEWER_ID);

    verify(documentFileService).setTranscriptionSpeaker(DOCUMENT_ID, "2", "name:Jean-Luc <b>=Ö", VIEWER_ID);
    verify(documentFileService).setTranscriptionSpeaker(DOCUMENT_ID, "1", "name:" + longest, VIEWER_ID);
    verify(identityManager, never()).getIdentity(anyString());
  }

  /**
   * A guest name is refused when it is empty once trimmed, longer than the
   * most characters allowed, or holds the suggestions' separator, a line break
   * or a control character; nothing is stored.
   */
  @Test
  void aGuestNameIsRefusedWhenEmptyTooLongOrHoldingASeparatorOrControl() throws Exception {
    mockNameableTranscript();
    String[] refusedNames = { null, "", "   ", "a".repeat(DocumentTranscriptionService.GUEST_NAME_MAX_LENGTH + 1), "Jean|Luc",
      "Jean\nLuc", "Jean\rLuc", "Jean\u2028Luc", "Jean\u2029Luc", "Jean\tLuc", "Jean\u0000Luc", "Jean\u007FLuc", "Jean\u0085Luc" };

    for (String refused : refusedNames) {
      assertEquals(DocumentTranscriptionService.INVALID_NAME_MESSAGE,
                   assertThrows(IllegalArgumentException.class,
                                () -> service.setGuestSpeaker(DOCUMENT_ID, "1", refused, VIEWER_ID)).getMessage(),
                   "Expected the name '" + refused + "' refused");
    }
    assertEquals("a".repeat(DocumentTranscriptionService.GUEST_NAME_MAX_LENGTH),
                 DocumentTranscriptionService.toGuestName(" " + "a".repeat(DocumentTranscriptionService.GUEST_NAME_MAX_LENGTH) + " "));
    verify(documentFileService, never()).setTranscriptionSpeaker(anyString(), anyString(), any(), anyLong());
  }

  /**
   * Naming a guest takes the same checks as naming a person: a user who
   * cannot edit the file is refused, and only a speaker the transcript holds
   * can be named.
   */
  @Test
  void aGuestIsNamedOnlyByAnEditorForASpeakerTheTranscriptHolds() throws Exception {
    when(documentFileService.getTranscriptionState(DOCUMENT_ID, VIEWER_ID)).thenReturn(done());
    when(documentFileService.hasEditPermissionOnDocument(DOCUMENT_ID, VIEWER_ID)).thenReturn(false);
    assertThrows(IllegalAccessException.class, () -> service.setGuestSpeaker(DOCUMENT_ID, "1", "Jean-Luc", VIEWER_ID));

    when(documentFileService.hasEditPermissionOnDocument(DOCUMENT_ID, VIEWER_ID)).thenReturn(true);
    when(documentFileService.getTranscriptionSegments(DOCUMENT_ID, VIEWER_ID)).thenReturn(SPEAKER_SEGMENTS);
    assertEquals(DocumentTranscriptionService.UNKNOWN_SPEAKER_MESSAGE,
                 assertThrows(IllegalArgumentException.class,
                              () -> service.setGuestSpeaker(DOCUMENT_ID, "3", "Jean-Luc", VIEWER_ID)).getMessage());
    verify(documentFileService, never()).setTranscriptionSpeaker(anyString(), anyString(), any(), anyLong());
  }

  private void mockNameableTranscript() throws Exception {
    when(documentFileService.getTranscriptionState(DOCUMENT_ID, VIEWER_ID)).thenReturn(done());
    when(documentFileService.hasEditPermissionOnDocument(DOCUMENT_ID, VIEWER_ID)).thenReturn(true);
    when(documentFileService.getTranscriptionSegments(DOCUMENT_ID, VIEWER_ID)).thenReturn(SPEAKER_SEGMENTS);
  }

  private static TranscriptionState done() {
    return TranscriptionState.builder().status(TranscriptionStatus.DONE).media(true).build();
  }

  private static Identity identity(boolean user, boolean deleted, boolean enabled) {
    Identity identity = mock(Identity.class);
    lenient().when(identity.isUser()).thenReturn(user);
    lenient().when(identity.isDeleted()).thenReturn(deleted);
    lenient().when(identity.isEnable()).thenReturn(enabled);
    return identity;
  }

  private void mockState(TranscriptionState state) throws Exception {
    when(capabilityRegistry.isAvailable()).thenReturn(true);
    when(documentFileService.getTranscriptionState(DOCUMENT_ID, VIEWER_ID)).thenReturn(state);
  }

}
