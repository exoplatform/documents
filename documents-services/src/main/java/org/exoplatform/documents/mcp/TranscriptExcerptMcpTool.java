/**
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
 */
package org.exoplatform.documents.mcp;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.documents.model.AbstractNode;
import org.exoplatform.documents.model.TranscriptSpeakers;
import org.exoplatform.documents.model.TranscriptionState;
import org.exoplatform.documents.service.DocumentFileService;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;

import io.meeds.mcp.server.plugin.McpToolPlugin;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * The MCP tool reading one part of a recording's transcript (EXO-92130), so an
 * assistant reads the chapter or the few minutes a question is about instead
 * of the whole meeting. Each line keeps the moment it was said and who said
 * it. It acts as the current user, so the file's ACL applies, as for the whole
 * transcript.
 */
@Service
@Profile("mcp-server")
public class TranscriptExcerptMcpTool implements McpToolPlugin {

  /** The longest part of a transcript one call reads, in seconds. */
  public static final long          MAX_RANGE_SECONDS = 30 * 60L;

  /** A moment written {@code mm:ss} or {@code h:mm:ss}. */
  private static final Pattern      CLOCK_MOMENT      = Pattern.compile("(?:(\\d{1,3}):([0-5]\\d)|(\\d{1,5})):([0-5]\\d)");

  /** A moment written in seconds, with or without a fraction. */
  private static final Pattern      SECONDS_MOMENT    = Pattern.compile("\\d{1,7}(?:\\.\\d{1,3})?");

  /** The refusal of a moment written in no accepted form. */
  private static final String       MSG_BAD_MOMENT    =
                                                   "'%s' is not a moment of the recording: write it mm:ss (23:00), h:mm:ss (1:02:30) or in seconds (1380).";

  /** The refusal of a call naming both a chapter and a range. */
  private static final String       MSG_ONE_MODE      =
                                                 "Pass either chapter (its number, 1 for the first listed), or from and to (a time range), not both.";

  /** The refusal of a range that ends before it starts. */
  private static final String       MSG_BAD_RANGE     = "'to' (%s) must come after 'from' (%s).";

  /** The refusal of a chapter number in a transcript with no chapters. */
  private static final String       MSG_NO_CHAPTERS   =
                                                    "The transcript of this file has no chapters: read a part of it by time, with from and to.";

  /** The refusal of a chapter number the transcript does not have. */
  private static final String       MSG_BAD_CHAPTER   = "The transcript of this file has %d chapters: pass a chapter from 1 to %d.";

  /** The refusal of a transcript with no timed lines. */
  private static final String       MSG_NOT_TIMED     =
                                                  "The transcript of this file has no timed lines, so no part of it can be read by chapter or by time. Read it whole with get_document_transcription_by_id, which also says why when there is no transcript.";

  private static final ObjectMapper MAPPER            = JsonMapper.builder().build();

  private final DocumentFileService documentFileService;

  private final IdentityManager     identityManager;

  /**
   * Builds the tool.
   *
   * @param documentFileService the Documents service storing the transcripts
   * @param identityManager the service resolving the users who speak
   */
  public TranscriptExcerptMcpTool(DocumentFileService documentFileService, IdentityManager identityManager) {
    this.documentFileService = documentFileService;
    this.identityManager = identityManager;
  }

  /**
   * Reads one part of a recording's transcript, read with the user's rights:
   * one chapter, or the lines said between two moments; with neither, lists
   * the chapters with their numbers instead. Every line overlapping
   * the part is given, each as {@code [mm:ss · Ns] Name: text}. A chapter runs
   * from its start to the next chapter's, the last one to the end of the
   * recording; chapters are numbered from 1 among those with a title and a
   * start, in the order they are stored, as the outlines of the transcript and
   * of the recordings list number them. A part longer than
   * {@link #MAX_RANGE_SECONDS} is read up to that length, and the answer says
   * where to go on from.
   *
   * @param documentId the recording's UUID
   * @param chapter the chapter's number, from 1; null to read a time range,
   *          or, with from and to blank as well, to list the chapters
   * @param from the first moment of a time range, {@code mm:ss},
   *          {@code h:mm:ss} or seconds; blank to read a chapter
   * @param to the last moment of a time range, in the same forms; blank to
   *          read a chapter
   * @return the lines, under a heading naming the file and the part; the
   *         numbered chapters when neither a chapter nor a range is given
   * @throws IllegalAccessException when the user may not read the file
   * @throws ObjectNotFoundException when the file does not exist, or the user
   *           cannot see it
   * @throws IllegalArgumentException when the document id is missing, both a
   *           chapter and a range are given, a moment is not one,
   *           the range ends before it starts, or the chapter does not exist
   * @throws IllegalStateException when the transcript has no timed lines
   */
  public String getTranscriptExcerpt(String documentId,
                                     Integer chapter,
                                     String from,
                                     String to) throws IllegalAccessException, ObjectNotFoundException {
    if (StringUtils.isBlank(documentId)) {
      throw new IllegalArgumentException("The 'document_id' parameter is mandatory: the recording's documentId.");
    }
    boolean byRange = StringUtils.isNotBlank(from) || StringUtils.isNotBlank(to);
    if (chapter != null && byRange) {
      throw new IllegalArgumentException(MSG_ONE_MODE);
    } else if (chapter == null && !byRange) {
      return listChapters(documentId);
    }
    double start = 0;
    double end = 0;
    if (byRange) {
      start = parseMoment(from, "from");
      end = parseMoment(to, "to");
      if (end <= start) {
        throw new IllegalArgumentException(MSG_BAD_RANGE.formatted(to, from));
      }
    } else if (chapter < 1) {
      throw new IllegalArgumentException("'chapter' is a chapter's number, 1 for the first listed.");
    }
    AbstractNode document = documentFileService.getDocumentById(documentId, getCurrentUserName());
    long identityId = getCurrentUserIdentityId();
    JsonNode timedLines = readTimedLines(documentId, identityId);
    double transcriptEnd = 0;
    for (JsonNode segment : timedLines) {
      transcriptEnd = Math.max(transcriptEnd, Math.max(readSeconds(segment, "start"), readSeconds(segment, "end")));
    }
    String part = "";
    if (!byRange) {
      List<JsonNode> chapters = readChapters(documentId, identityId);
      if (chapters.isEmpty()) {
        throw new IllegalArgumentException(MSG_NO_CHAPTERS);
      } else if (chapter > chapters.size()) {
        throw new IllegalArgumentException(MSG_BAD_CHAPTER.formatted(chapters.size(), chapters.size()));
      }
      JsonNode selected = chapters.get(chapter - 1);
      start = selected.get("start").asDouble();
      end = chapter < chapters.size() ? chapters.get(chapter).get("start").asDouble()
                                      : getRecordingEnd(documentId, identityId, transcriptEnd);
      if (end <= start) {
        end = Math.max(transcriptEnd, start + 1);
      }
      part = ", chapter %d \"%s\",".formatted(chapter, StringUtils.trim(selected.get("title").asString()));
    }
    double wantedEnd = end;
    boolean capped = end - start > MAX_RANGE_SECONDS;
    if (capped) {
      end = start + MAX_RANGE_SECONDS;
    }
    Map<String, String> speakers = documentFileService.getTranscriptionSpeakers(documentId, identityId);
    List<String> lines = new ArrayList<>();
    for (JsonNode segment : timedLines) {
      double segmentStart = readSeconds(segment, "start");
      String text = segment.hasNonNull("text") ? StringUtils.trimToNull(segment.get("text").asString()) : null;
      if (text != null && segmentStart >= 0 && overlaps(segmentStart, readSeconds(segment, "end"), start, end)) {
        String speaker = segment.hasNonNull("speaker") ? segment.get("speaker").asString() : null;
        lines.add(TranscriptSpeakers.formatLine(segmentStart,
                                                TranscriptSpeakers.speakerName(speaker, speakers, this::getFullName),
                                                text));
      }
    }
    String range = "from %s to %s".formatted(TranscriptSpeakers.formatMoment(start), TranscriptSpeakers.formatMoment(end));
    if (lines.isEmpty()) {
      return "Nothing is said %s in the transcript of \"%s\"%s which ends at %s.".formatted(range,
                                                                                          document.getName(),
                                                                                          part.isEmpty() ? "," : part,
                                                                                          TranscriptSpeakers.formatMoment(transcriptEnd));
    }
    StringBuilder excerpt = new StringBuilder("Transcript of \"%s\"%s %s:\n".formatted(document.getName(), part, range));
    excerpt.append(String.join("\n", lines));
    // the moments are written in whole seconds: the hint starts at the cut rounded down and
    // ends rounded up, so that a call made with it misses no line of the part asked for
    double restEnd = Math.ceil(Math.min(wantedEnd, transcriptEnd));
    if (capped && end < transcriptEnd && restEnd > Math.floor(end)) {
      String next = TranscriptSpeakers.formatMoment(end);
      String rest = TranscriptSpeakers.formatMoment(restEnd);
      excerpt.append("\n\nThe excerpt stops at %s: one call reads %d minutes at most. Call again with from %s and to %s for what follows."
                                                                                                                                         .formatted(next,
                                                                                                                                                    MAX_RANGE_SECONDS
                                                                                                                                                        / 60,
                                                                                                                                                    next,
                                                                                                                                                    rest));
    }
    return excerpt.toString();
  }

  /**
   * Lists the chapters of a recording's transcript with the numbers a chapter
   * is read by, each with its start, title and summary, so that an assistant
   * picks the chapter a question is about without reading the transcript.
   *
   * @param documentId the recording's UUID
   * @return the numbered chapters, or a sentence saying there are none
   * @throws IllegalAccessException when the user may not read the file
   * @throws ObjectNotFoundException when the file does not exist, or the user
   *           cannot see it
   */
  private String listChapters(String documentId) throws IllegalAccessException, ObjectNotFoundException {
    AbstractNode document = documentFileService.getDocumentById(documentId, getCurrentUserName());
    List<JsonNode> chapters = readChapters(documentId, getCurrentUserIdentityId());
    if (chapters.isEmpty()) {
      return "The transcript of \"%s\" has no chapters: read a part of it by time with from and to, or the whole of it with get_document_transcription_by_id.".formatted(document.getName());
    }
    StringBuilder list = new StringBuilder("Chapters of \"%s\":".formatted(document.getName()));
    for (int i = 0; i < chapters.size(); i++) {
      JsonNode chapter = chapters.get(i);
      list.append("\n%d. %s %s".formatted(i + 1,
                                          TranscriptSpeakers.formatMarker(chapter.get("start").asDouble()),
                                          StringUtils.trim(chapter.get("title").asString())));
      String summary = chapter.hasNonNull("summary") ? StringUtils.trimToNull(chapter.get("summary").asString()) : null;
      if (summary != null) {
        list.append(": ").append(summary);
      }
    }
    return list.append("\n\nRead one with chapter (its number), or a part by time with from and to.").toString();
  }

  /**
   * Reads a moment of the recording.
   *
   * @param value the moment, {@code mm:ss}, {@code h:mm:ss} or seconds
   * @param name the argument's name, for the refusal
   * @return the moment, in seconds
   * @throws IllegalArgumentException when the moment is missing or in no
   *           accepted form
   */
  static double parseMoment(String value, String name) {
    String moment = StringUtils.trimToNull(value);
    if (moment == null) {
      throw new IllegalArgumentException("The '%s' parameter is mandatory: %s".formatted(name, MSG_BAD_MOMENT.formatted("")));
    }
    if (SECONDS_MOMENT.matcher(moment).matches()) {
      return Double.parseDouble(moment);
    }
    Matcher clock = CLOCK_MOMENT.matcher(moment);
    if (!clock.matches()) {
      throw new IllegalArgumentException(MSG_BAD_MOMENT.formatted(moment));
    }
    long seconds = Long.parseLong(clock.group(4));
    if (clock.group(1) != null) {
      return Long.parseLong(clock.group(1)) * 3600 + Long.parseLong(clock.group(2)) * 60 + seconds;
    }
    return Long.parseLong(clock.group(3)) * 60 + seconds;
  }

  /**
   * Reads the timed lines of a recording's transcript.
   *
   * @param documentId the recording's UUID
   * @param identityId the identity id of the user
   * @return the timed lines, a JSON array
   * @throws IllegalAccessException when the user may not read the file
   * @throws ObjectNotFoundException when the file does not exist
   * @throws IllegalStateException when there are none, or they cannot be read
   */
  private JsonNode readTimedLines(String documentId, long identityId) throws IllegalAccessException, ObjectNotFoundException {
    String segments = documentFileService.getTranscriptionSegments(documentId, identityId);
    if (StringUtils.isBlank(segments)) {
      throw new IllegalStateException(MSG_NOT_TIMED);
    }
    JsonNode timedLines;
    try {
      timedLines = MAPPER.readTree(segments);
    } catch (JacksonException e) {
      throw new IllegalStateException(MSG_NOT_TIMED, e);
    }
    if (!timedLines.isArray()) {
      throw new IllegalStateException(MSG_NOT_TIMED);
    }
    return timedLines;
  }

  /**
   * Reads the chapters of a recording's transcript that have a title and a
   * start, in the order they are stored: the ones the outlines list and
   * number.
   *
   * @param documentId the recording's UUID
   * @param identityId the identity id of the user
   * @return the chapters, empty when there are none or they cannot be read
   * @throws IllegalAccessException when the user may not read the file
   * @throws ObjectNotFoundException when the file does not exist
   */
  private List<JsonNode> readChapters(String documentId, long identityId) throws IllegalAccessException,
                                                                          ObjectNotFoundException {
    String chapters = documentFileService.getTranscriptionChapters(documentId, identityId);
    if (StringUtils.isBlank(chapters)) {
      return List.of();
    }
    List<JsonNode> result = new ArrayList<>();
    try {
      for (JsonNode chapter : MAPPER.readTree(chapters)) {
        String title = chapter.hasNonNull("title") ? StringUtils.trimToNull(chapter.get("title").asString()) : null;
        if (title != null && readSeconds(chapter, "start") >= 0) {
          result.add(chapter);
        }
      }
    } catch (JacksonException e) {
      return List.of();
    }
    return result;
  }

  /**
   * The end of a recording, where its last chapter ends: its measured
   * duration, else the end of the last timed line.
   *
   * @param documentId the recording's UUID
   * @param identityId the identity id of the user
   * @param transcriptEnd the end of the last timed line, in seconds
   * @return the end, in seconds
   * @throws IllegalAccessException when the user may not read the file
   * @throws ObjectNotFoundException when the file does not exist
   */
  private double getRecordingEnd(String documentId, long identityId, double transcriptEnd) throws IllegalAccessException,
                                                                                           ObjectNotFoundException {
    TranscriptionState state = documentFileService.getTranscriptionState(documentId, identityId);
    Long duration = state == null ? null : state.durationSeconds();
    return duration != null && duration > 0 ? Math.max(duration, transcriptEnd) : transcriptEnd;
  }

  /**
   * Tells whether a line said between two moments falls in a range: it starts
   * before the range ends and ends after it starts; a line with no end falls
   * in it when it starts in it.
   *
   * @param lineStart when the line starts, in seconds
   * @param lineEnd when it ends, in seconds, negative when unknown
   * @param start the range's start, in seconds
   * @param end the range's end, in seconds
   * @return true when the line falls in the range
   */
  private static boolean overlaps(double lineStart, double lineEnd, double start, double end) {
    return lineStart < end && (lineEnd >= 0 ? lineEnd > start : lineStart >= start);
  }

  /**
   * Reads a moment of a timed line or a chapter.
   *
   * @param node the timed line or chapter
   * @param field the moment's field
   * @return the moment, in seconds, or -1 when it is missing, not a finite
   *         number or negative
   */
  private static double readSeconds(JsonNode node, String field) {
    JsonNode value = node.get(field);
    return value != null && value.isNumber() && Double.isFinite(value.asDouble()) && value.asDouble() >= 0 ? value.asDouble()
                                                                                                          : -1;
  }

  /**
   * The full name of the person named for a speaker.
   *
   * @param identityId the stored identity id
   * @return the full name, or null when the identity is gone or the id is not
   *         one
   */
  private String getFullName(String identityId) {
    long id = NumberUtils.toLong(identityId, 0);
    if (id <= 0) {
      return null;
    }
    Identity identity = identityManager.getIdentity(id);
    if (identity == null || identity.isDeleted() || identity.getProfile() == null) {
      return null;
    }
    return StringUtils.trimToNull(identity.getProfile().getFullName());
  }

  /**
   * The identity id of the current user, the one the Documents reads take.
   *
   * @return the identity id
   */
  private long getCurrentUserIdentityId() {
    return identityManager.getOrCreateUserIdentity(getCurrentUserName()).getIdentityId();
  }

}
