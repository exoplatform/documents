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
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
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

  private void mockState(TranscriptionState state) throws Exception {
    when(capabilityRegistry.isAvailable()).thenReturn(true);
    when(documentFileService.getTranscriptionState(DOCUMENT_ID, VIEWER_ID)).thenReturn(state);
  }

}
