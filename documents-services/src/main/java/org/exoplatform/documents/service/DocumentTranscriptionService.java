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

import java.util.List;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.documents.constant.TranscriptionStatus;
import org.exoplatform.documents.model.TranscriptSpeakers;
import org.exoplatform.documents.model.TranscriptionState;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Optional;
import org.exoplatform.documents.model.DocumentAudience;
import org.exoplatform.documents.model.TranscriptionQuote;
import org.exoplatform.documents.model.TranscriptionSpeakerCandidates;
import org.exoplatform.documents.model.TranscriptionSpeakerProposal;
import org.exoplatform.documents.model.TranscriptionSpeakerSuggestion;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;

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

  public static final String                   NO_SPEAKERS_MESSAGE                 = "documents.transcription.speaker.noSpeakers";

  public static final String                   UNKNOWN_SPEAKER_MESSAGE             = "documents.transcription.speaker.unknown";

  public static final String                   NOT_A_USER_MESSAGE                  = "documents.transcription.speaker.notAUser";

  public static final String                   INVALID_NAME_MESSAGE                = "documents.transcription.speaker.invalidName";

  /** The longest guest name a speaker can be given, in characters, once trimmed. */
  public static final int                      GUEST_NAME_MAX_LENGTH               = 80;

  /** How long a queued or running transcription may go unchanged before its run is taken as lost. */
  static final long                            STALE_TRANSCRIPTION_MILLIS          = TranscriptionState.STALE_MILLIS;

  /** The zero-width non-joiner, part of the spelling of Persian and Indic names. */
  private static final int                     ZERO_WIDTH_NON_JOINER               = 0x200C;

  /** The zero-width joiner, part of the spelling of Indic names. */
  private static final int                     ZERO_WIDTH_JOINER                   = 0x200D;

  @Autowired
  private DocumentFileService                  documentFileService;

  @Autowired
  private MediaTranscriptionCapabilityRegistry capabilityRegistry;

  @Autowired
  private ListenerService                      listenerService;

  @Autowired(required = false)
  private List<TranscriptChaptersRequester>    chaptersRequesters;

  @Autowired
  private IdentityManager                      identityManager;

  private final ObjectMapper                   objectMapper                        = new ObjectMapper();

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
   * The chapters a file's transcript is grouped into (EXO-92122), read with the
   * user's rights.
   *
   * @param documentId the file's UUID
   * @param aclIdentity the identity id of the user
   * @return the chapters as a JSON list, or null when there are none
   * @throws IllegalAccessException when the user may not read the file
   * @throws ObjectNotFoundException when the file does not exist or the user
   *           cannot see it
   */
  public String getChapters(String documentId, long aclIdentity) throws IllegalAccessException, ObjectNotFoundException {
    return documentFileService.getTranscriptionChapters(documentId, aclIdentity);
  }

  /**
   * The person each named speaker of a file's transcript is, read with the
   * user's rights (EXO-92123).
   *
   * @param documentId the file's UUID
   * @param aclIdentity the identity id of the user
   * @return the identity id of each named speaker, by speaker number; empty when
   *         none is named
   * @throws IllegalAccessException when the user may not read the file
   * @throws ObjectNotFoundException when the file does not exist or the user
   *           cannot see it
   */
  public Map<String, String> getSpeakers(String documentId, long aclIdentity) throws IllegalAccessException,
                                                                              ObjectNotFoundException {
    return documentFileService.getTranscriptionSpeakers(documentId, aclIdentity);
  }

  /**
   * Tells whether a user may say who the speakers of a file's transcript are:
   * anyone who can edit the file (EXO-92123).
   *
   * @param documentId the file's UUID
   * @param aclIdentity the identity id of the user
   * @return true when the user can edit the file
   * @throws IllegalAccessException when the user's identity cannot be resolved
   */
  public boolean canNameSpeakers(String documentId, long aclIdentity) throws IllegalAccessException {
    return documentFileService.hasEditPermissionOnDocument(documentId, aclIdentity);
  }

  /**
   * Says which person a speaker of a file's transcript is, or that it is nobody
   * named any more, for anyone who can edit the file (EXO-92123). The speaker
   * must be one the transcript holds, and the person an enabled user.
   *
   * @param documentId the file's UUID
   * @param speaker the speaker's number in the transcript
   * @param identityId the identity id of the person, or null to unname the
   *          speaker
   * @param aclIdentity the identity id of the user naming it
   * @throws IllegalAccessException when the user may not edit the file
   * @throws ObjectNotFoundException when the file does not exist or the user
   *           cannot see it
   * @throws IllegalArgumentException when the transcript tells no speakers
   *           apart, holds no such speaker, or the person is no enabled user
   */
  public void setSpeaker(String documentId, String speaker, Long identityId, long aclIdentity) throws IllegalAccessException,
                                                                                                ObjectNotFoundException {
    checkNameable(documentId, speaker, identityId, aclIdentity);
    documentFileService.setTranscriptionSpeaker(documentId, speaker, identityId == null ? null : String.valueOf(identityId), aclIdentity);
  }

  /**
   * Says that a speaker of a file's transcript is a guest with no account,
   * known by a plain name, for anyone who can edit the file (EXO-92123). The
   * name is stored as {@link TranscriptSpeakers#GUEST_PREFIX} followed by the
   * trimmed name, with no escaping: whoever shows it escapes it.
   *
   * @param documentId the file's UUID
   * @param speaker the speaker's number in the transcript
   * @param name the guest's name
   * @param aclIdentity the identity id of the user naming it
   * @throws IllegalAccessException when the user may not edit the file
   * @throws ObjectNotFoundException when the file does not exist or the user
   *           cannot see it
   * @throws IllegalArgumentException when the transcript tells no speakers
   *           apart, holds no such speaker, or the name is refused by
   *           {@link #toGuestName(String)}
   */
  public void setGuestSpeaker(String documentId, String speaker, String name, long aclIdentity) throws IllegalAccessException,
                                                                                                ObjectNotFoundException {
    checkNameable(documentId, speaker, null, aclIdentity);
    String guestName = toGuestName(name);
    documentFileService.setTranscriptionSpeaker(documentId, speaker, TranscriptSpeakers.GUEST_PREFIX + guestName, aclIdentity);
  }

  /**
   * Checks that a speaker of a file's transcript may be named by a user: the
   * user can edit the file, the transcript tells this speaker apart, and the
   * person is an enabled user.
   *
   * @param documentId the file's UUID
   * @param speaker the speaker's number in the transcript
   * @param identityId the identity id of the person, or null to unname the
   *          speaker, or to name it after a guest
   * @param aclIdentity the identity id of the user naming it
   * @throws IllegalAccessException when the user may not edit the file
   * @throws ObjectNotFoundException when the file does not exist or the user
   *           cannot see it
   * @throws IllegalArgumentException when the transcript tells no speakers
   *           apart, holds no such speaker, or the person is no enabled user
   */
  private void checkNameable(String documentId, String speaker, Long identityId, long aclIdentity) throws IllegalAccessException,
                                                                                                 ObjectNotFoundException {
    TranscriptionState state = getState(documentId, aclIdentity);
    if (!canNameSpeakers(documentId, aclIdentity)) {
      throw new IllegalAccessException("User may not name the speakers of file " + documentId);
    }
    Set<String> speakers = state.status() == TranscriptionStatus.DONE ? readSpeakers(getSegments(documentId, aclIdentity)) : Set.of();
    if (speakers.isEmpty()) {
      throw new IllegalArgumentException(NO_SPEAKERS_MESSAGE);
    } else if (!speakers.contains(speaker)) {
      throw new IllegalArgumentException(UNKNOWN_SPEAKER_MESSAGE);
    } else if (identityId != null && !isEnabledUser(identityId)) {
      throw new IllegalArgumentException(NOT_A_USER_MESSAGE);
    }
  }

  /**
   * Validates a guest's name, for any caller that stores or proposes one.
   *
   * @param name the name as typed
   * @return the name, trimmed
   * @throws IllegalArgumentException when the name holds no letter or digit,
   *           is longer than {@link #GUEST_NAME_MAX_LENGTH} characters once
   *           trimmed, or holds a {@code |}, a line break, a control character
   *           or a Unicode format character other than the zero-width joiner and
   *           non-joiner
   */
  public static String toGuestName(String name) {
    String guestName = name == null ? "" : name.strip();
    if (guestName.codePoints().noneMatch(Character::isLetterOrDigit) || guestName.codePointCount(0, guestName.length()) > GUEST_NAME_MAX_LENGTH
        || guestName.codePoints().anyMatch(DocumentTranscriptionService::isRefusedInName)) {
      throw new IllegalArgumentException(INVALID_NAME_MESSAGE);
    }
    return guestName;
  }

  /**
   * Tells whether a character may not be part of a guest's name: the
   * separator of the stored suggestions, a line break, a control character, or
   * an invisible format character (a zero-width space, a bidi override), but
   * the zero-width joiner and non-joiner, which Persian and Indic names are
   * spelt with.
   *
   * @param codePoint the character
   * @return true when the name may not hold it
   */
  private static boolean isRefusedInName(int codePoint) {
    int type = Character.getType(codePoint);
    return codePoint == '|' || Character.isISOControl(codePoint) || type == Character.LINE_SEPARATOR
           || type == Character.PARAGRAPH_SEPARATOR || type == Character.FORMAT && codePoint != ZERO_WIDTH_NON_JOINER && codePoint != ZERO_WIDTH_JOINER;
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
    } else if (state.isInProgress(System.currentTimeMillis())) {
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
   * Reads the speakers a transcript's segments name.
   *
   * @param segments the segments as a JSON list, possibly null
   * @return the speakers, empty when the segments name none
   */
  private Set<String> readSpeakers(String segments) {
    Set<String> speakers = new HashSet<>();
    if (StringUtils.isNotBlank(segments)) {
      for (JsonNode segment : objectMapper.readTree(segments)) {
        JsonNode segmentSpeaker = segment.get("speaker");
        if (segmentSpeaker != null && segmentSpeaker.isString()) {
          speakers.add(segmentSpeaker.asString());
        }
      }
    }
    return speakers;
  }

  /**
   * Tells whether an identity is a user who may be named: one that exists, is
   * not deleted and is enabled.
   *
   * @param identityId the identity id
   * @return true for an enabled user
   */
  private boolean isEnabledUser(long identityId) {
    Identity identity = identityManager.getIdentity(String.valueOf(identityId));
    return identity != null && identity.isUser() && !identity.isDeleted() && identity.isEnable();
  }

  /**
   * Asks for the chapters of a transcript a user opens, for any user who can
   * read the file (EXO-92122): a transcript done before chapters were switched
   * on gets them the first time it is read. Only a transcript done, with timed
   * segments and no chapters, is handed to the transcriber, which makes them at
   * most once per file and tells whether they are coming.
   *
   * @param documentId the file's UUID
   * @param aclIdentity the identity id of the user
   * @return true when the chapters are coming: queued now, or queued earlier
   *         and not made yet; false when none are to be made
   * @throws IllegalAccessException when the user may not read the file
   * @throws ObjectNotFoundException when the file does not exist or the user
   *           cannot see it
   */
  public boolean requestChapters(String documentId, long aclIdentity) throws IllegalAccessException, ObjectNotFoundException {
    if (!isAvailable()) {
      return false;
    }
    TranscriptionState state = getState(documentId, aclIdentity);
    if (state.status() != TranscriptionStatus.DONE || !state.hasSegments() || state.hasChapters()) {
      return false;
    }
    if (chaptersRequesters == null) {
      return false;
    }
    // every requester is asked, whatever an earlier one answered
    boolean coming = false;
    for (TranscriptChaptersRequester requester : chaptersRequesters) {
      coming |= requester.requestChapters(documentId);
    }
    return coming;
  }

  /**
   * The most people taken as the possible speakers of a recording linked to no
   * meeting (EXO-92126): past it, a space's members are left out, a name said
   * matching too many people to point to one.
   */
  static final int                             MAX_CANDIDATES                      = 50;

  private static final Log                     LOG                                 = ExoLogger.getLogger(DocumentTranscriptionService.class);

  @Autowired
  private SpaceService                         spaceService;

  @Autowired(required = false)
  private List<TranscriptionAttendeesPlugin>   attendeesPlugins                    = List.of();

  /**
   * The people who may be the speakers of a recording (EXO-92126): the
   * attendees of its meeting when an add-on knows it; otherwise the file's
   * owner, the users it is shared with one by one and, up to
   * {@link #MAX_CANDIDATES}, the members of the space that holds it. Only
   * enabled users are kept.
   *
   * @param documentId the recording file's UUID
   * @return the candidates, and whether they are a meeting's attendees
   * @throws ObjectNotFoundException when the file does not exist
   */
  public TranscriptionSpeakerCandidates getSpeakerCandidates(String documentId) throws ObjectNotFoundException {
    Set<Long> attendees = new LinkedHashSet<>();
    boolean meeting = false;
    for (TranscriptionAttendeesPlugin plugin : attendeesPlugins) {
      try {
        Optional<List<Long>> pluginAttendees = plugin.getAttendees(documentId);
        if (pluginAttendees != null && pluginAttendees.isPresent()) {
          meeting = true;
          attendees.addAll(pluginAttendees.get());
        }
      } catch (RuntimeException e) {
        LOG.warn("The attendees of the meeting of recording '{}' couldn't be read from {}", documentId, plugin.getClass().getName(), e);
      }
    }
    if (meeting) {
      return new TranscriptionSpeakerCandidates(true, attendees.stream().filter(this::isEnabledUser).toList());
    }
    DocumentAudience audience = documentFileService.getDocumentAudience(documentId);
    Set<String> usernames = new LinkedHashSet<>();
    if (StringUtils.isNotBlank(audience.owner())) {
      usernames.add(audience.owner());
    }
    usernames.addAll(audience.users());
    Space space = audience.spaceGroupId() == null ? null : spaceService.getSpaceByGroupId(audience.spaceGroupId());
    if (space != null && space.getMembers() != null && usernames.size() + space.getMembers().length <= MAX_CANDIDATES) {
      usernames.addAll(List.of(space.getMembers()));
    }
    List<Long> candidates = new ArrayList<>();
    for (String username : usernames) {
      Identity identity = identityManager.getOrCreateUserIdentity(username);
      if (identity != null && candidates.size() < MAX_CANDIDATES && isEnabledUser(Long.parseLong(identity.getId()))) {
        candidates.add(Long.parseLong(identity.getId()));
      }
    }
    return new TranscriptionSpeakerCandidates(false, candidates);
  }

  /**
   * Keeps the proposals made for the speakers of a recording's transcript, as
   * the system, then names the speakers whose proposal is strong (EXO-92126).
   * Nothing is kept or named when the transcript changed since the proposals
   * were made from it. A strong proposal names its speaker as the recording's
   * owner, so only while the owner can still edit the file, and only when
   * neither the speaker nor the person is named yet: it never replaces a name a
   * person gave. A proposal made by elimination is applied after the others,
   * and only while the voice it follows from is still named after the person it
   * was taken to be. Every proposal is kept, the applied ones marked so, the
   * others shown to the people who can edit the file until one confirms or
   * dismisses them.
   *
   * @param documentId the recording file's UUID
   * @param suggestions the proposals, one per speaker at most
   * @param ownerIdentityId the identity id of the recording's owner, or null
   *          when unknown, which names no speaker
   * @param transcriptUpdated the update time of the transcription state the
   *          proposals were made from, in milliseconds since the epoch
   * @return the proposals kept, each saying whether it was applied; none when
   *         the transcript changed
   * @throws ObjectNotFoundException when the file does not exist
   */
  public List<TranscriptionSpeakerSuggestion> applySpeakerSuggestions(String documentId,
                                                                      List<TranscriptionSpeakerSuggestion> suggestions,
                                                                      Long ownerIdentityId,
                                                                      long transcriptUpdated) throws ObjectNotFoundException {
    List<TranscriptionSpeakerSuggestion> kept = new ArrayList<>(suggestions.stream()
                                                                           .map(suggestion -> suggestion.withAutoApplied(false))
                                                                           .toList());
    // kept before any speaker is named, so that no name is ever applied without the proposal that explains it
    if (!documentFileService.saveTranscriptionSpeakerSuggestions(documentId, List.copyOf(kept), transcriptUpdated)) {
      return List.of();
    }
    if (ownerIdentityId == null) {
      return kept;
    }
    // the proposals shown by the names said first: one made by elimination follows from them
    for (boolean byElimination : new boolean[] { false, true }) {
      for (int i = 0; i < kept.size(); i++) {
        TranscriptionSpeakerSuggestion suggestion = kept.get(i);
        if (suggestion.strong()
            && (suggestion.premiseSpeaker() != null) == byElimination
            && (!byElimination || premiseHolds(documentId, suggestion, ownerIdentityId))
            && autoName(documentId, suggestion, ownerIdentityId, transcriptUpdated)) {
          kept.set(i, suggestion.withAutoApplied(true));
        }
      }
    }
    return kept;
  }

  /**
   * The proposals of who the speakers of a file's transcript are, for a user
   * who can edit the file (EXO-92126), each with the sentences that show it. A
   * proposal waiting for a person is shown while neither its speaker nor its
   * person is named; an applied one while its speaker is still named after its
   * person.
   *
   * @param documentId the file's UUID
   * @param aclIdentity the identity id of the user
   * @return the proposals to show, in the order they were made
   * @throws IllegalAccessException when the user may not edit the file
   * @throws ObjectNotFoundException when the file does not exist or the user
   *           cannot see it
   */
  public List<TranscriptionSpeakerProposal> getSpeakerProposals(String documentId, long aclIdentity) throws IllegalAccessException,
                                                                                                    ObjectNotFoundException {
    if (!canNameSpeakers(documentId, aclIdentity)) {
      throw new IllegalAccessException("User may not name the speakers of file " + documentId);
    }
    List<TranscriptionSpeakerSuggestion> suggestions = documentFileService.getTranscriptionSpeakerSuggestions(documentId, aclIdentity);
    if (suggestions.isEmpty()) {
      return List.of();
    }
    Map<String, String> named = getSpeakers(documentId, aclIdentity);
    List<JsonNode> segments = readSegments(getSegments(documentId, aclIdentity));
    List<TranscriptionSpeakerProposal> proposals = new ArrayList<>();
    for (TranscriptionSpeakerSuggestion suggestion : suggestions) {
      String person = String.valueOf(suggestion.identityId());
      boolean shown = suggestion.autoApplied() ? person.equals(named.get(suggestion.speaker()))
                                               : !named.containsKey(suggestion.speaker()) && !named.containsValue(person);
      if (shown) {
        List<TranscriptionQuote> quotes = new ArrayList<>();
        for (int index : suggestion.evidence()) {
          if (index >= 0 && index < segments.size()) {
            JsonNode segment = segments.get(index);
            quotes.add(new TranscriptionQuote(segment.path("start").asDouble(),
                                              segment.path("speaker").isString() ? segment.path("speaker").asString() : null,
                                              segment.path("text").asString("")));
          }
        }
        proposals.add(new TranscriptionSpeakerProposal(suggestion.speaker(),
                                                       suggestion.identityId(),
                                                       suggestion.strong(),
                                                       suggestion.autoApplied(),
                                                       quotes,
                                                       suggestion.premiseSpeaker()));
      }
    }
    return proposals;
  }

  /**
   * Dismisses the proposal for one speaker of a file's transcript, for a user
   * who can edit the file (EXO-92126).
   *
   * @param documentId the file's UUID
   * @param speaker the speaker's number in the transcript
   * @param aclIdentity the identity id of the user
   * @throws IllegalAccessException when the user may not edit the file
   * @throws ObjectNotFoundException when the file does not exist or the user
   *           cannot see it
   */
  public void dismissSpeakerProposal(String documentId, String speaker, long aclIdentity) throws IllegalAccessException,
                                                                                         ObjectNotFoundException {
    if (!canNameSpeakers(documentId, aclIdentity)) {
      throw new IllegalAccessException("User may not name the speakers of file " + documentId);
    }
    documentFileService.removeTranscriptionSpeakerSuggestion(documentId, speaker, aclIdentity);
  }

  /**
   * Names a speaker after a strong proposal, as the recording's owner, when
   * neither the speaker nor the person is named yet and the transcript is still
   * the one it was made from, then marks the stored proposal as applied.
   *
   * @param documentId the file's UUID
   * @param suggestion the proposal
   * @param ownerIdentityId the identity id of the recording's owner
   * @param transcriptUpdated the update time of the transcription state the
   *          proposal was made from
   * @return true when the speaker was named and its proposal marked; false when
   *         it, or the person, already was named, the transcript changed, the
   *         proposal was dismissed meanwhile, or the owner may no longer name it
   */
  private boolean autoName(String documentId,
                           TranscriptionSpeakerSuggestion suggestion,
                           long ownerIdentityId,
                           long transcriptUpdated) {
    try {
      checkNameable(documentId, suggestion.speaker(), suggestion.identityId(), ownerIdentityId);
      return documentFileService.nameTranscriptionSpeakerIfUnnamed(documentId,
                                                                   suggestion.speaker(),
                                                                   String.valueOf(suggestion.identityId()),
                                                                   transcriptUpdated,
                                                                   ownerIdentityId)
          && documentFileService.markTranscriptionSpeakerSuggestionApplied(documentId,
                                                                           suggestion.speaker(),
                                                                           suggestion.identityId(),
                                                                           transcriptUpdated);
    } catch (IllegalAccessException | ObjectNotFoundException | IllegalArgumentException | IllegalStateException e) {
      LOG.debug("Speaker {} of recording '{}' is left to a person to name", suggestion.speaker(), documentId, e);
      return false;
    }
  }

  /**
   * Tells whether the voice a proposal made by elimination follows from is still
   * named after the person it was taken to be, read with the owner's rights.
   *
   * @param documentId the file's UUID
   * @param suggestion the proposal
   * @param ownerIdentityId the identity id of the recording's owner
   * @return true while that voice is named after that person
   */
  private boolean premiseHolds(String documentId, TranscriptionSpeakerSuggestion suggestion, long ownerIdentityId) {
    try {
      return String.valueOf(suggestion.premiseIdentityId())
                   .equals(getSpeakers(documentId, ownerIdentityId).get(suggestion.premiseSpeaker()));
    } catch (IllegalAccessException | ObjectNotFoundException e) {
      LOG.debug("The named speakers of recording '{}' cannot be read by its owner", documentId, e);
      return false;
    }
  }

  /**
   * Reads a transcript's segments.
   *
   * @param segments the segments as a JSON list, possibly null
   * @return the segments, in order; empty when there are none
   */
  private List<JsonNode> readSegments(String segments) {
    List<JsonNode> nodes = new ArrayList<>();
    if (StringUtils.isNotBlank(segments)) {
      objectMapper.readTree(segments).forEach(nodes::add);
    }
    return nodes;
  }

}
