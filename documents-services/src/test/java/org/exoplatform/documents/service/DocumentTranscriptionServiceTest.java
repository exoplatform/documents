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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import org.exoplatform.documents.constant.TranscriptionStatus;
import org.exoplatform.documents.model.TranscriptionState;
import org.exoplatform.services.listener.ListenerService;

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
   * With nothing to make chapters, none are coming (EXO-92122).
   */
  @Test
  void noChaptersAreComingWithoutAnythingToMakeThem() throws Exception {
    mockState(TranscriptionState.builder().status(TranscriptionStatus.DONE).hasSegments(true).media(true).build());

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

  private void mockState(TranscriptionState state) throws Exception {
    when(capabilityRegistry.isAvailable()).thenReturn(true);
    when(documentFileService.getTranscriptionState(DOCUMENT_ID, VIEWER_ID)).thenReturn(state);
  }

}
