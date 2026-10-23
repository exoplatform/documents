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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.documents.constant.TranscriptionStatus;
import org.exoplatform.documents.model.TranscriptionState;
import org.exoplatform.services.listener.ListenerService;

/**
 * What a user can know and ask about the transcription of an audio or video
 * file (EXO-92097): its state, its transcript, and a retry. Every read is made
 * with the user's own rights; the transcription itself is the transcriber's,
 * which listens to {@link #TRANSCRIPTION_RETRY_REQUESTED_EVENT}.
 */
@Service
public class DocumentTranscriptionService {

  /**
   * Broadcast when a user asks to transcribe a file again, with the user name as
   * source and the file's UUID as data.
   */
  public static final String                   TRANSCRIPTION_RETRY_REQUESTED_EVENT = "exo.documents.transcription.retryRequested";

  public static final String                   UNAVAILABLE_MESSAGE                 = "documents.transcription.unavailable";

  public static final String                   NOT_MEDIA_MESSAGE                   = "documents.transcription.notMedia";

  public static final String                   ALREADY_DONE_MESSAGE                = "documents.transcription.alreadyDone";

  /**
   * How long a queued or running transcription may go without its state
   * changing before a retry hands it to the transcriber again: twice the AI
   * queue's default run ceiling, a run that writes its progress after each piece.
   */
  static final long                            STALE_TRANSCRIPTION_MILLIS          = 3600_000L;

  @Autowired
  private DocumentFileService                  documentFileService;

  @Autowired
  private MediaTranscriptionCapabilityRegistry capabilityRegistry;

  @Autowired
  private ListenerService                      listenerService;

  /**
   * Tells whether media files can be transcribed on this server.
   *
   * @return true when a transcription engine is installed
   */
  public boolean isAvailable() {
    return capabilityRegistry.isAvailable();
  }

  /**
   * The transcription state of a file, read with the user's rights.
   *
   * @param documentId the file's UUID
   * @param aclIdentity the identity id of the user
   * @return the state
   * @throws IllegalAccessException when the user may not read the file
   * @throws ObjectNotFoundException when the file does not exist or the user
   *           cannot see it
   */
  public TranscriptionState getState(String documentId, long aclIdentity) throws IllegalAccessException,
                                                                          ObjectNotFoundException {
    return documentFileService.getTranscriptionState(documentId, aclIdentity);
  }

  /**
   * The transcript of a file, read with the user's rights.
   *
   * @param documentId the file's UUID
   * @param aclIdentity the identity id of the user
   * @return the transcript, or null when the file has none
   * @throws IllegalAccessException when the user may not read the file
   * @throws ObjectNotFoundException when the file does not exist or the user
   *           cannot see it
   */
  public String getText(String documentId, long aclIdentity) throws IllegalAccessException, ObjectNotFoundException {
    if (getState(documentId, aclIdentity).status() != TranscriptionStatus.DONE) {
      return null;
    }
    return documentFileService.getAudioTranscription(documentId, aclIdentity);
  }

  /**
   * The timed segments of a file's transcript, read with the user's rights.
   *
   * @param documentId the file's UUID
   * @param aclIdentity the identity id of the user
   * @return the segments as a JSON list, or null when there are none
   * @throws IllegalAccessException when the user may not read the file
   * @throws ObjectNotFoundException when the file does not exist or the user
   *           cannot see it
   */
  public String getSegments(String documentId, long aclIdentity) throws IllegalAccessException, ObjectNotFoundException {
    return documentFileService.getTranscriptionSegments(documentId, aclIdentity);
  }

  /**
   * Asks to transcribe a file again, for any user who can read it. A failed file,
   * or one never transcribed, is handed to the transcriber; one queued or being
   * transcribed is left as it is, unless its state has not changed for
   * {@link #STALE_TRANSCRIPTION_MILLIS}, when its run was lost; a transcribed one
   * is refused.
   *
   * @param documentId the file's UUID
   * @param aclIdentity the identity id of the user
   * @param username the user's name
   * @return true when the file was handed to the transcriber, false when it was
   *         already queued or being transcribed
   * @throws IllegalAccessException when the user may not read the file
   * @throws ObjectNotFoundException when the file does not exist or the user
   *           cannot see it
   * @throws IllegalArgumentException when no transcriber is installed, the file
   *           is no audio or video, or it is already transcribed
   */
  public boolean requestRetry(String documentId, long aclIdentity, String username) throws IllegalAccessException,
                                                                                    ObjectNotFoundException {
    if (!isAvailable()) {
      throw new IllegalArgumentException(UNAVAILABLE_MESSAGE);
    }
    TranscriptionState state = getState(documentId, aclIdentity);
    if (!state.media()) {
      throw new IllegalArgumentException(NOT_MEDIA_MESSAGE);
    } else if (state.status() == TranscriptionStatus.DONE) {
      throw new IllegalArgumentException(ALREADY_DONE_MESSAGE);
    } else if (isInProgress(state)) {
      return false;
    }
    try {
      listenerService.broadcast(TRANSCRIPTION_RETRY_REQUESTED_EVENT, username, documentId);
    } catch (Exception e) {
      throw new IllegalStateException("Error asking to transcribe media file '%s' again".formatted(documentId), e);
    }
    return true;
  }

  /**
   * Tells whether a transcription is queued or running, and still moving.
   *
   * @param state the file's transcription state
   * @return true when it is queued or running and its state changed recently
   */
  private boolean isInProgress(TranscriptionState state) {
    if (state.status() != TranscriptionStatus.QUEUED && state.status() != TranscriptionStatus.PROCESSING) {
      return false;
    }
    return state.updated() != null && System.currentTimeMillis() - state.updated() < STALE_TRANSCRIPTION_MILLIS;
  }

}
