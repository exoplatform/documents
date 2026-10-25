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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

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
import org.exoplatform.documents.model.TranscriptionBusyException;
import org.exoplatform.documents.model.TranscriptionQuote;
import org.exoplatform.documents.model.TranscriptionSpeakerProposal;
import org.exoplatform.documents.model.TranscriptionState;
import org.exoplatform.documents.rest.model.TranscriptionSpeakerEntity;
import org.exoplatform.documents.rest.model.TranscriptionSpeakerProposalEntity;
import org.exoplatform.documents.rest.model.TranscriptionSpeakersEntity;
import org.exoplatform.documents.rest.model.TranscriptionStateEntity;
import org.exoplatform.documents.service.DocumentTranscriptionService;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.model.Profile;
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

  /**
   * A request for the chapters answers 202 only when they are coming, so that
   * the reader waits for them; 204 when none are to be made (EXO-92122).
   */
  @Test
  void aChaptersRequestAnswers202OnlyWhenTheyAreComing() throws Exception {
    when(documentTranscriptionService.requestChapters(DOCUMENT_ID, 7L)).thenReturn(true);
    assertEquals(HttpStatus.ACCEPTED, rest.requestChapters(request, DOCUMENT_ID).getStatusCode());

    when(documentTranscriptionService.requestChapters(DOCUMENT_ID, 7L)).thenReturn(false);
    assertEquals(HttpStatus.NO_CONTENT, rest.requestChapters(request, DOCUMENT_ID).getStatusCode());
  }

  /**
   * A chapters request on a file the viewer cannot read answers 403, on one
   * they cannot see 404, each with a fixed code and no repository path
   * (EXO-92122).
   */
  @Test
  void aChaptersRequestOnAnUnreadableFileAnswers403Or404() throws Exception {
    when(documentTranscriptionService.requestChapters(DOCUMENT_ID, 7L)).thenThrow(new IllegalAccessException("john may not read /x"));
    ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> rest.requestChapters(request, DOCUMENT_ID));
    assertEquals(HttpStatus.FORBIDDEN, e.getStatusCode());
    assertEquals("documents.transcription.forbidden", e.getReason());

    doThrow(new ObjectNotFoundException("/Groups/spaces/x/secret.mp4")).when(documentTranscriptionService).requestChapters(DOCUMENT_ID, 7L);
    e = assertThrows(ResponseStatusException.class, () -> rest.requestChapters(request, DOCUMENT_ID));
    assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
    assertEquals("documents.transcription.notFound", e.getReason());
  }

  /**
   * The named speakers come with the person each is, a deleted person left
   * out, and whether the caller may name them (EXO-92123).
   */
  @Test
  void theNamedSpeakersComeWithTheirPerson_andWhetherTheCallerMayNameThem() throws Exception {
    when(documentTranscriptionService.getSpeakers(DOCUMENT_ID, 7L)).thenReturn(new TreeMap<>(Map.of("1", "42", "2", "43", "3", "name:Jean-Luc <b>")));
    when(documentTranscriptionService.canNameSpeakers(DOCUMENT_ID, 7L)).thenReturn(true);
    Identity julien = mock(Identity.class);
    Profile profile = mock(Profile.class);
    when(julien.getRemoteId()).thenReturn("julien");
    when(julien.getProfile()).thenReturn(profile);
    when(profile.getFullName()).thenReturn("Julien Dupont");
    when(profile.getAvatarUrl()).thenReturn("/avatar/julien");
    Identity deleted = mock(Identity.class);
    when(deleted.isDeleted()).thenReturn(true);
    lenient().when(deleted.getProfile()).thenReturn(profile);
    when(identityManager.getIdentity("42")).thenReturn(julien);
    when(identityManager.getIdentity("43")).thenReturn(deleted);

    ResponseEntity<TranscriptionSpeakersEntity> answer = rest.getSpeakers(request, DOCUMENT_ID);

    assertEquals("no-store", answer.getHeaders().getCacheControl());
    assertTrue(answer.getBody().canEdit());
    assertEquals(List.of(new TranscriptionSpeakerEntity("1", "42", "julien", "Julien Dupont", "/avatar/julien", false),
                         new TranscriptionSpeakerEntity("3", null, null, "Jean-Luc <b>", null, true)),
                 answer.getBody().speakers());
    verify(identityManager, never()).getIdentity("name:Jean-Luc <b>");
  }

  /**
   * Naming a speaker answers 204, a refused value 400 with its message code,
   * and a caller who cannot edit 403; unnaming sends no person.
   */
  @Test
  void namingASpeakerAnswersItsStatus() throws Exception {
    assertEquals(HttpStatus.NO_CONTENT, rest.setSpeaker(request, DOCUMENT_ID, "1", 42L, null).getStatusCode());
    verify(documentTranscriptionService).setSpeaker(DOCUMENT_ID, "1", 42L, 7L);

    assertEquals(HttpStatus.NO_CONTENT, rest.unsetSpeaker(request, DOCUMENT_ID, "1").getStatusCode());
    verify(documentTranscriptionService).setSpeaker(DOCUMENT_ID, "1", null, 7L);

    doThrow(new IllegalArgumentException("documents.transcription.speaker.unknown")).when(documentTranscriptionService)
                                                                                    .setSpeaker(DOCUMENT_ID, "9", 42L, 7L);
    ResponseStatusException refused = assertThrows(ResponseStatusException.class, () -> rest.setSpeaker(request, DOCUMENT_ID, "9", 42L, null));
    assertEquals(HttpStatus.BAD_REQUEST, refused.getStatusCode());
    assertEquals("documents.transcription.speaker.unknown", refused.getReason());

    doThrow(new TranscriptionBusyException("held up")).when(documentTranscriptionService).setSpeaker(DOCUMENT_ID, "3", 42L, 7L);
    ResponseStatusException busy = assertThrows(ResponseStatusException.class, () -> rest.setSpeaker(request, DOCUMENT_ID, "3", 42L, null));
    assertEquals(HttpStatus.CONFLICT, busy.getStatusCode());
    assertEquals("documents.transcription.speaker.busy", busy.getReason());

    doThrow(new IllegalAccessException("john may not edit /x")).when(documentTranscriptionService).setSpeaker(DOCUMENT_ID, "2", 42L, 7L);
    ResponseStatusException forbidden = assertThrows(ResponseStatusException.class, () -> rest.setSpeaker(request, DOCUMENT_ID, "2", 42L, null));
    assertEquals(HttpStatus.FORBIDDEN, forbidden.getStatusCode());
    assertEquals("documents.transcription.forbidden", forbidden.getReason(), "Expected no repository path in the answer");
  }

  /**
   * A guest is named by a plain name, handed to the service as typed: the
   * service trims and validates it (EXO-92123).
   */
  @Test
  void namingASpeakerAfterAGuestAnswers204() throws Exception {
    assertEquals(HttpStatus.NO_CONTENT, rest.setSpeaker(request, DOCUMENT_ID, "2", null, " Jean-Luc ").getStatusCode());

    verify(documentTranscriptionService).setGuestSpeaker(DOCUMENT_ID, "2", " Jean-Luc ", 7L);
    verify(documentTranscriptionService, never()).setSpeaker(anyString(), anyString(), any(), anyLong());

    doThrow(new IllegalArgumentException("documents.transcription.speaker.invalidName")).when(documentTranscriptionService)
                                                                                        .setGuestSpeaker(DOCUMENT_ID, "2", "a|b", 7L);
    ResponseStatusException refused = assertThrows(ResponseStatusException.class,
                                                   () -> rest.setSpeaker(request, DOCUMENT_ID, "2", null, "a|b"));
    assertEquals(HttpStatus.BAD_REQUEST, refused.getStatusCode());
    assertEquals("documents.transcription.speaker.invalidName", refused.getReason());
  }

  /**
   * Naming a speaker takes exactly one of a person and a guest name: both, or
   * neither, is refused with 400 before the service is asked.
   */
  @Test
  void namingASpeakerTakesExactlyOneOfAPersonAndAGuestName() throws Exception {
    for (Object[] params : new Object[][] { { 42L, "Jean-Luc" }, { null, null } }) {
      ResponseStatusException refused = assertThrows(ResponseStatusException.class,
                                                      () -> rest.setSpeaker(request,
                                                                            DOCUMENT_ID,
                                                                            "1",
                                                                            (Long) params[0],
                                                                            (String) params[1]));
      assertEquals(HttpStatus.BAD_REQUEST, refused.getStatusCode());
      assertEquals("documents.transcription.speaker.identityOrName", refused.getReason());
    }
    verify(documentTranscriptionService, never()).setSpeaker(anyString(), anyString(), any(), anyLong());
    verify(documentTranscriptionService, never()).setGuestSpeaker(anyString(), anyString(), any(), anyLong());
  }

  /**
   * The chapters of a file the viewer cannot see answer 404 with a fixed code
   * and no repository path (EXO-92122).
   */
  @Test
  void theChaptersOfAnUnseenFileAnswer404() throws Exception {
    when(documentTranscriptionService.getChapters(DOCUMENT_ID, 7L)).thenThrow(new ObjectNotFoundException("/Groups/spaces/x/secret.mp4"));

    ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> rest.getChapters(request, DOCUMENT_ID));

    assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
    assertEquals("documents.transcription.notFound", e.getReason());
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

  /**
   * The proposals come with the person each names, a deleted person left out,
   * never stored by a shared cache; a caller who cannot edit the file is
   * answered 403 (EXO-92126).
   */
  @Test
  void theSpeakerProposalsComeWithTheirPerson_andAreForEditorsOnly() throws Exception {
    List<TranscriptionQuote> quotes = List.of(new TranscriptionQuote(4, "1", "Merci Julien."));
    when(documentTranscriptionService.getSpeakerProposals(DOCUMENT_ID, 7L)).thenReturn(List.of(
        new TranscriptionSpeakerProposal("2", 42, true, false, quotes, "1"),
        new TranscriptionSpeakerProposal("3", 43, false, false, List.of(), null)));
    Identity julien = mock(Identity.class);
    Profile profile = mock(Profile.class);
    when(julien.getId()).thenReturn("42");
    when(julien.getRemoteId()).thenReturn("julien");
    when(julien.getProfile()).thenReturn(profile);
    when(profile.getFullName()).thenReturn("Julien Dupont");
    when(profile.getAvatarUrl()).thenReturn("/avatar/julien");
    Identity deleted = mock(Identity.class);
    when(deleted.isDeleted()).thenReturn(true);
    when(identityManager.getIdentity("42")).thenReturn(julien);
    when(identityManager.getIdentity("43")).thenReturn(deleted);

    ResponseEntity<List<TranscriptionSpeakerProposalEntity>> answer = rest.getSpeakerProposals(request, DOCUMENT_ID);

    assertEquals("no-store", answer.getHeaders().getCacheControl());
    assertEquals(List.of(new TranscriptionSpeakerProposalEntity("2", "42", "julien", "Julien Dupont", "/avatar/julien", true, false, quotes, "1")),
                 answer.getBody());

    when(documentTranscriptionService.getSpeakerProposals(DOCUMENT_ID, 7L)).thenThrow(new IllegalAccessException("no"));
    assertEquals(HttpStatus.FORBIDDEN,
                 assertThrows(ResponseStatusException.class, () -> rest.getSpeakerProposals(request, DOCUMENT_ID)).getStatusCode());
  }

  /**
   * Dismissing a proposal answers 204, 403 to a caller who cannot edit the
   * file, 404 for a file the caller cannot see, 409 when another change of
   * the file held it up (EXO-92126).
   */
  @Test
  void dismissingASpeakerProposalAnswersItsStatus() throws Exception {
    assertEquals(HttpStatus.NO_CONTENT, rest.dismissSpeakerProposal(request, DOCUMENT_ID, "2").getStatusCode());
    verify(documentTranscriptionService).dismissSpeakerProposal(DOCUMENT_ID, "2", 7L);

    doThrow(new IllegalAccessException("no")).when(documentTranscriptionService).dismissSpeakerProposal(DOCUMENT_ID, "3", 7L);
    assertEquals(HttpStatus.FORBIDDEN,
                 assertThrows(ResponseStatusException.class, () -> rest.dismissSpeakerProposal(request, DOCUMENT_ID, "3")).getStatusCode());

    doThrow(new ObjectNotFoundException("none")).when(documentTranscriptionService).dismissSpeakerProposal(DOCUMENT_ID, "4", 7L);
    assertEquals(HttpStatus.NOT_FOUND,
                 assertThrows(ResponseStatusException.class, () -> rest.dismissSpeakerProposal(request, DOCUMENT_ID, "4")).getStatusCode());
    doThrow(new TranscriptionBusyException("held up")).when(documentTranscriptionService).dismissSpeakerProposal(DOCUMENT_ID, "5", 7L);
    ResponseStatusException busy = assertThrows(ResponseStatusException.class, () -> rest.dismissSpeakerProposal(request, DOCUMENT_ID, "5"));
    assertEquals(HttpStatus.CONFLICT, busy.getStatusCode());
    assertEquals("documents.transcription.speaker.busy", busy.getReason());
  }

}
