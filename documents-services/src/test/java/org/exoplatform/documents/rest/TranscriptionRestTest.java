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
package org.exoplatform.documents.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.documents.constant.TranscriptionStatus;
import org.exoplatform.documents.model.TranscriptionState;
import org.exoplatform.documents.rest.model.TranscriptionStateEntity;
import org.exoplatform.documents.service.DocumentTranscriptionService;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;

import jakarta.servlet.http.HttpServletRequest;

@ExtendWith(MockitoExtension.class)
class TranscriptionRestTest {

  private static final String          DOCUMENT_ID = "doc-uuid";

  @Mock
  private DocumentTranscriptionService documentTranscriptionService;

  @Mock
  private IdentityManager              identityManager;

  @Mock
  private HttpServletRequest           request;

  @InjectMocks
  private TranscriptionRest            rest;

  @BeforeEach
  void setUp() {
    lenient().when(request.getRemoteUser()).thenReturn("john");
    Identity identity = mock(Identity.class);
    lenient().when(identity.getId()).thenReturn("7");
    lenient().when(identityManager.getOrCreateUserIdentity("john")).thenReturn(identity);
  }

  @Test
  void theStateSaysUnavailableAndReadsNothingWithoutATranscriber() throws Exception {
    when(documentTranscriptionService.isAvailable()).thenReturn(false);

    ResponseEntity<TranscriptionStateEntity> response = rest.getState(request, DOCUMENT_ID);
    TranscriptionStateEntity state = response.getBody();

    assertEquals("no-store", response.getHeaders().getCacheControl());
    assertFalse(state.available());
    assertNull(state.status());
    verify(documentTranscriptionService, never()).getState(anyString(), anyLong());
  }

  @Test
  void theStateIsTheViewersReadOfTheFile() throws Exception {
    when(documentTranscriptionService.isAvailable()).thenReturn(true);
    when(documentTranscriptionService.getState(DOCUMENT_ID, 7L)).thenReturn(TranscriptionState.builder()
                                                                                              .status(TranscriptionStatus.FAILED)
                                                                                              .progress("2/9")
                                                                                              .error("provider.timeout")
                                                                                              .language("en")
                                                                                              .durationSeconds(5400L)
                                                                                              .updated(1000L)
                                                                                              .hasSegments(true)
                                                                                              .hasChapters(true)
                                                                                              .build());

    ResponseEntity<TranscriptionStateEntity> response = rest.getState(request, DOCUMENT_ID);

    assertEquals(new TranscriptionStateEntity(true, "FAILED", "2/9", "provider.timeout", "en", 5400L, 1000L, true, true),
                 response.getBody());
    assertEquals("no-store", response.getHeaders().getCacheControl(), "Expected a viewer's read never to be kept by a shared cache");
  }

  @Test
  void aFileTheViewerCannotSeeAnswers404WithAFixedCode() throws Exception {
    when(documentTranscriptionService.isAvailable()).thenReturn(true);
    when(documentTranscriptionService.getState(DOCUMENT_ID, 7L)).thenThrow(new ObjectNotFoundException("/Groups/spaces/x/secret.mp4"));

    ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> rest.getState(request, DOCUMENT_ID));

    assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
    assertEquals("documents.transcription.notFound", e.getReason(), "Expected no repository path in the answer");
  }

  @Test
  void aRefusedReadAnswers403WithAFixedCode() throws Exception {
    when(documentTranscriptionService.isAvailable()).thenReturn(true);
    when(documentTranscriptionService.getState(DOCUMENT_ID, 7L)).thenThrow(new IllegalAccessException("john may not read /x"));

    ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> rest.getState(request, DOCUMENT_ID));

    assertEquals(HttpStatus.FORBIDDEN, e.getStatusCode());
    assertEquals("documents.transcription.forbidden", e.getReason());
  }

  @Test
  void noTranscriptYetAnswers404() throws Exception {
    when(documentTranscriptionService.getText(DOCUMENT_ID, 7L)).thenReturn(null);

    ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> rest.getText(request, DOCUMENT_ID));

    assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
    assertEquals("documents.transcription.noTranscript", e.getReason());
  }

  @Test
  void aRetryIsAccepted() throws Exception {
    when(documentTranscriptionService.requestRetry(DOCUMENT_ID, 7L, "john")).thenReturn(true);

    assertEquals(HttpStatus.ACCEPTED, rest.retry(request, DOCUMENT_ID).getStatusCode());
  }

  @Test
  void aRetryAlreadyInProgressIsAcceptedToo() throws Exception {
    when(documentTranscriptionService.requestRetry(DOCUMENT_ID, 7L, "john")).thenReturn(false);

    assertEquals(HttpStatus.ACCEPTED, rest.retry(request, DOCUMENT_ID).getStatusCode());
  }

  @Test
  void aRefusedRetryAnswers400WithItsMessageCode() throws Exception {
    when(documentTranscriptionService.requestRetry(DOCUMENT_ID, 7L, "john"))
                                                                         .thenThrow(new IllegalArgumentException(DocumentTranscriptionService.ALREADY_DONE_MESSAGE));

    ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> rest.retry(request, DOCUMENT_ID));

    assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
    assertTrue(e.getReason().equals(DocumentTranscriptionService.ALREADY_DONE_MESSAGE));
  }

  @Test
  void theTranscriptAndItsSegmentsAreNeverKeptByASharedCache() throws Exception {
    when(documentTranscriptionService.getText(DOCUMENT_ID, 7L)).thenReturn("hello");
    when(documentTranscriptionService.getSegments(DOCUMENT_ID, 7L)).thenReturn("[]");

    ResponseEntity<String> text = rest.getText(request, DOCUMENT_ID);
    ResponseEntity<String> segments = rest.getSegments(request, DOCUMENT_ID);

    assertEquals("hello", text.getBody());
    assertEquals("no-store", text.getHeaders().getCacheControl());
    assertEquals("[]", segments.getBody());
    assertEquals("no-store", segments.getHeaders().getCacheControl());
  }

  /**
   * The chapters are the viewer's read, never kept by a shared cache, and a
   * transcript with none answers 404 with a fixed code (EXO-92122).
   */
  @Test
  void theChaptersAreTheViewersReadAndNoneAnswers404() throws Exception {
    when(documentTranscriptionService.getChapters(DOCUMENT_ID, 7L)).thenReturn("[{\"title\":\"Welcome\"}]");

    ResponseEntity<String> chapters = rest.getChapters(request, DOCUMENT_ID);

    assertEquals("[{\"title\":\"Welcome\"}]", chapters.getBody());
    assertEquals("no-store", chapters.getHeaders().getCacheControl());

    when(documentTranscriptionService.getChapters(DOCUMENT_ID, 7L)).thenReturn(null);
    ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> rest.getChapters(request, DOCUMENT_ID));
    assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
    assertEquals("documents.transcription.noChapters", e.getReason());

    when(documentTranscriptionService.getChapters(DOCUMENT_ID, 7L)).thenThrow(new IllegalAccessException("john may not read /x"));
    e = assertThrows(ResponseStatusException.class, () -> rest.getChapters(request, DOCUMENT_ID));
    assertEquals(HttpStatus.FORBIDDEN, e.getStatusCode());
    assertEquals("documents.transcription.forbidden", e.getReason());
  }

}
