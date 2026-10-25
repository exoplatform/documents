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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.documents.model.TranscriptionState;
import org.exoplatform.documents.rest.model.TranscriptionStateEntity;
import org.exoplatform.documents.service.DocumentTranscriptionService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.manager.IdentityManager;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;

/**
 * The transcription of an audio or video file (EXO-92097): its state, its
 * transcript, its timed segments, and a retry. Every answer is read with the
 * caller's own rights, so none may be stored by a shared cache: a file the
 * caller cannot open answers 404.
 */
@RestController
@RequestMapping("/transcriptions")
@Tag(name = "/documents-portlet/rest/transcriptions", description = "Transcription of audio and video files")
public class TranscriptionRest {

  private static final Log             LOG                 = ExoLogger.getLogger(TranscriptionRest.class);

  private static final String          NOT_FOUND_MESSAGE   = "documents.transcription.notFound";

  private static final String          FORBIDDEN_MESSAGE   = "documents.transcription.forbidden";

  private static final String          NO_TRANSCRIPT_MESSAGE = "documents.transcription.noTranscript";

  private static final String          NO_CHAPTERS_MESSAGE = "documents.transcription.noChapters";

  @Autowired
  private DocumentTranscriptionService documentTranscriptionService;

  @Autowired
  private IdentityManager              identityManager;

  /**
   * Answers the transcription state of a file.
   *
   * @param request the HTTP request, which names the caller
   * @param documentId the file's UUID
   * @return the state, never stored by a shared cache; only
   *         {@code available=false} when no transcription engine is installed
   */
  @GetMapping(path = "{documentId}", produces = MediaType.APPLICATION_JSON_VALUE)
  @Secured("users")
  @Operation(summary = "Transcription state of an audio or video file", method = "GET")
  @ApiResponses(value = {
    @ApiResponse(responseCode = "200", description = "Request fulfilled"),
    @ApiResponse(responseCode = "403", description = "The file cannot be read by the caller"),
    @ApiResponse(responseCode = "404", description = "The file does not exist or the caller cannot see it"),
  })
  public ResponseEntity<TranscriptionStateEntity> getState(HttpServletRequest request,
                                           @Parameter(description = "The file's UUID", required = true)
                                           @PathVariable("documentId")
                                           String documentId) {
    if (!documentTranscriptionService.isAvailable()) {
      return ResponseEntity.ok()
                           .cacheControl(CacheControl.noStore())
                           .body(new TranscriptionStateEntity(false, null, null, null, null, null, null, false, false));
    }
    try {
      TranscriptionState state = documentTranscriptionService.getState(documentId, getCurrentIdentityId(request));
      return ResponseEntity.ok()
                           .cacheControl(CacheControl.noStore())
                           .body(new TranscriptionStateEntity(true,
                                                              state.status() == null ? null : state.status().name(),
                                                              state.progress(),
                                                              state.error(),
                                                              state.language(),
                                                              state.durationSeconds(),
                                                              state.updated(),
                                                              state.hasSegments(),
                                                              state.hasChapters()));
    } catch (IllegalAccessException e) {
      throw forbidden(documentId, e);
    } catch (ObjectNotFoundException e) {
      throw notFound(documentId, e);
    }
  }

  /**
   * Answers the transcript of a file, as plain text.
   *
   * @param request the HTTP request, which names the caller
   * @param documentId the file's UUID
   * @return the transcript, never stored by a shared cache
   */
  @GetMapping(path = "{documentId}/text", produces = MediaType.TEXT_PLAIN_VALUE)
  @Secured("users")
  @Operation(summary = "Transcript of an audio or video file", method = "GET")
  @ApiResponses(value = {
    @ApiResponse(responseCode = "200", description = "Request fulfilled"),
    @ApiResponse(responseCode = "403", description = "The file cannot be read by the caller"),
    @ApiResponse(responseCode = "404", description = "No such file, or no transcript yet"),
  })
  public ResponseEntity<String> getText(HttpServletRequest request,
                        @Parameter(description = "The file's UUID", required = true)
                        @PathVariable("documentId")
                        String documentId) {
    try {
      String text = documentTranscriptionService.getText(documentId, getCurrentIdentityId(request));
      if (text == null) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, NO_TRANSCRIPT_MESSAGE);
      }
      return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(text);
    } catch (IllegalAccessException e) {
      throw forbidden(documentId, e);
    } catch (ObjectNotFoundException e) {
      throw notFound(documentId, e);
    }
  }

  /**
   * Answers the timed segments of a file's transcript, a JSON list of start,
   * end and text.
   *
   * @param request the HTTP request, which names the caller
   * @param documentId the file's UUID
   * @return the segments, never stored by a shared cache
   */
  @GetMapping(path = "{documentId}/segments", produces = MediaType.APPLICATION_JSON_VALUE)
  @Secured("users")
  @Operation(summary = "Timed segments of the transcript of an audio or video file", method = "GET")
  @ApiResponses(value = {
    @ApiResponse(responseCode = "200", description = "Request fulfilled"),
    @ApiResponse(responseCode = "403", description = "The file cannot be read by the caller"),
    @ApiResponse(responseCode = "404", description = "No such file, or no segments"),
  })
  public ResponseEntity<String> getSegments(HttpServletRequest request,
                            @Parameter(description = "The file's UUID", required = true)
                            @PathVariable("documentId")
                            String documentId) {
    try {
      String segments = documentTranscriptionService.getSegments(documentId, getCurrentIdentityId(request));
      if (segments == null) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, NO_TRANSCRIPT_MESSAGE);
      }
      return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(segments);
    } catch (IllegalAccessException e) {
      throw forbidden(documentId, e);
    } catch (ObjectNotFoundException e) {
      throw notFound(documentId, e);
    }
  }

  /**
   * Answers the chapters a file's transcript is grouped into (EXO-92122), a JSON
   * list of chapters, each with its start in seconds, the index of its first
   * segment, its title and its summary.
   *
   * @param request the HTTP request, which names the caller
   * @param documentId the file's UUID
   * @return the chapters, never stored by a shared cache
   */
  @GetMapping(path = "{documentId}/chapters", produces = MediaType.APPLICATION_JSON_VALUE)
  @Secured("users")
  @Operation(summary = "Chapters of the transcript of an audio or video file", method = "GET")
  @ApiResponses(value = {
    @ApiResponse(responseCode = "200", description = "Request fulfilled"),
    @ApiResponse(responseCode = "403", description = "The file cannot be read by the caller"),
    @ApiResponse(responseCode = "404", description = "No such file, or no chapters"),
  })
  public ResponseEntity<String> getChapters(HttpServletRequest request,
                                            @Parameter(description = "The file's UUID", required = true)
                                            @PathVariable("documentId")
                                            String documentId) {
    try {
      String chapters = documentTranscriptionService.getChapters(documentId, getCurrentIdentityId(request));
      if (chapters == null) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, NO_CHAPTERS_MESSAGE);
      }
      return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(chapters);
    } catch (IllegalAccessException e) {
      throw forbidden(documentId, e);
    } catch (ObjectNotFoundException e) {
      throw notFound(documentId, e);
    }
  }

  /**
   * Asks for the chapters of a transcript being read (EXO-92122); a transcript
   * that has them, or none to make, is left as it is. The answer tells the
   * reader whether to wait for them.
   *
   * @param request the HTTP request, which names the caller
   * @param documentId the file's UUID
   * @return 202 when the chapters are coming (queued now, or earlier and not
   *         made yet), 204 when none are to be made
   */
  @PostMapping(path = "{documentId}/chapters")
  @Secured("users")
  @Operation(summary = "Ask for the chapters of the transcript of an audio or video file", method = "POST")
  @ApiResponses(value = {
    @ApiResponse(responseCode = "202", description = "The chapters are coming"),
    @ApiResponse(responseCode = "204", description = "No chapters are to be made for this transcript"),
    @ApiResponse(responseCode = "403", description = "The file cannot be read by the caller"),
    @ApiResponse(responseCode = "404", description = "The file does not exist or the caller cannot see it"),
  })
  public ResponseEntity<Void> requestChapters(HttpServletRequest request,
                                              @Parameter(description = "The file's UUID", required = true)
                                              @PathVariable("documentId")
                                              String documentId) {
    try {
      boolean coming = documentTranscriptionService.requestChapters(documentId, getCurrentIdentityId(request));
      return coming ? ResponseEntity.accepted().build() : ResponseEntity.noContent().build();
    } catch (IllegalAccessException e) {
      throw forbidden(documentId, e);
    } catch (ObjectNotFoundException e) {
      throw notFound(documentId, e);
    }
  }

  /**
   * Asks to transcribe a file again.
   *
   * @param request the HTTP request, which names the caller
   * @param documentId the file's UUID
   * @return 202 once the file is handed to the transcriber, or when it is
   *         already queued or being transcribed
   */
  @PostMapping(path = "{documentId}/retry")
  @Secured("users")
  @Operation(summary = "Transcribe an audio or video file again", method = "POST")
  @ApiResponses(value = {
    @ApiResponse(responseCode = "202", description = "Request accepted"),
    @ApiResponse(responseCode = "400", description = "No transcriber installed, not a media file, or already transcribed"),
    @ApiResponse(responseCode = "403", description = "The file cannot be read by the caller"),
    @ApiResponse(responseCode = "404", description = "The file does not exist or the caller cannot see it"),
  })
  public ResponseEntity<Void> retry(HttpServletRequest request,
                                    @Parameter(description = "The file's UUID", required = true)
                                    @PathVariable("documentId")
                                    String documentId) {
    try {
      documentTranscriptionService.requestRetry(documentId, getCurrentIdentityId(request), request.getRemoteUser());
      return ResponseEntity.accepted().build();
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
    } catch (IllegalAccessException e) {
      throw forbidden(documentId, e);
    } catch (ObjectNotFoundException e) {
      throw notFound(documentId, e);
    }
  }

  /**
   * The identity id of the caller.
   *
   * @param request the HTTP request
   * @return the caller's identity id
   */
  private long getCurrentIdentityId(HttpServletRequest request) {
    return Long.parseLong(identityManager.getOrCreateUserIdentity(request.getRemoteUser()).getId());
  }

  /**
   * The 403 answer, whose body is a fixed message code; the repository's own
   * message goes to the debug log only.
   *
   * @param documentId the file's UUID
   * @param e the refusal
   * @return the exception to throw
   */
  private ResponseStatusException forbidden(String documentId, Exception e) {
    LOG.debug("Transcription of file {} refused to the caller", documentId, e);
    return new ResponseStatusException(HttpStatus.FORBIDDEN, FORBIDDEN_MESSAGE);
  }

  /**
   * The 404 answer, whose body is a fixed message code.
   *
   * @param documentId the file's UUID
   * @param e the cause
   * @return the exception to throw
   */
  private ResponseStatusException notFound(String documentId, Exception e) {
    LOG.debug("Transcription of file {} not found", documentId, e);
    return new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND_MESSAGE);
  }

}
