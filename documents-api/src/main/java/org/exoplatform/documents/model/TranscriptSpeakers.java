/**
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
package org.exoplatform.documents.model;

import java.util.Map;
import java.util.function.Function;

/**
 * Reads who the speakers of a transcript are and writes its lines, with no
 * service behind it, for any module that sees the documents API only
 * (EXO-92123). A speaker is stored as {@code <speaker>=<identityId>} for a
 * person with an account, or {@code <speaker>=name:<text>} for a guest named
 * by a plain name. What is written is meant for an assistant, so it is never
 * localised.
 */
public final class TranscriptSpeakers {

  /** The prefix of a stored guest name, matched case-sensitively. */
  public static final String GUEST_PREFIX  = "name:";

  /** The label of a speaker nobody named, followed by its number. */
  public static final String SPEAKER_LABEL = "Speaker";

  /**
   * Utility class.
   */
  private TranscriptSpeakers() {
  }

  /**
   * Tells whether a stored speaker value is a guest's plain name.
   *
   * @param value the stored value
   * @return true when the value starts with {@link #GUEST_PREFIX}
   */
  public static boolean isGuest(String value) {
    return value != null && value.startsWith(GUEST_PREFIX);
  }

  /**
   * Reads the guest name a stored speaker value holds.
   *
   * @param value the stored value
   * @return the text after {@link #GUEST_PREFIX}, or null when the value is
   *         no guest name
   */
  public static String guestName(String value) {
    return isGuest(value) ? value.substring(GUEST_PREFIX.length()) : null;
  }

  /**
   * Names a speaker of a transcript: the full name of the person named for it,
   * the guest's name, or {@code Speaker N} when nobody is named, when the
   * person is gone, or when the lookup finds no name.
   *
   * @param speaker the speaker's number in the transcript
   * @param speakers the stored value of each named speaker, by speaker number
   * @param fullNameOfIdentity gives the full name of a person from their
   *          identity id, or null when there is none
   * @return the speaker's name, never null
   */
  public static String speakerName(String speaker,
                                   Map<String, String> speakers,
                                   Function<String, String> fullNameOfIdentity) {
    String value = speakers == null ? null : speakers.get(speaker);
    String name = null;
    if (isGuest(value)) {
      name = guestName(value);
    } else if (value != null && !value.isBlank() && fullNameOfIdentity != null) {
      name = fullNameOfIdentity.apply(value);
    }
    return name == null || name.isBlank() ? SPEAKER_LABEL + " " + speaker : name;
  }

  /**
   * Writes a moment of a media.
   *
   * @param seconds the moment, in seconds from the beginning of the media; its
   *          fraction is dropped, and a negative or undefined moment is 0
   * @return {@code mm:ss}, or {@code h:mm:ss} past an hour
   */
  public static String formatMoment(double seconds) {
    long whole = wholeSeconds(seconds);
    long hours = whole / 3600;
    long minutes = (whole % 3600) / 60;
    long remainder = whole % 60;
    return hours > 0 ? "%d:%02d:%02d".formatted(hours, minutes, remainder) : "%02d:%02d".formatted(minutes, remainder);
  }

  /**
   * Writes a moment of a media as the documents tools mark it for an
   * assistant, with the whole seconds an assistant can jump to.
   *
   * @param seconds the moment, in seconds from the beginning of the media; its
   *          fraction is dropped, and a negative or undefined moment is 0
   * @return {@code [mm:ss · Ns]}, or {@code [h:mm:ss · Ns]} past an hour, N
   *         being the moment in whole seconds
   */
  public static String formatMarker(double seconds) {
    return "[%s · %ds]".formatted(formatMoment(seconds), wholeSeconds(seconds));
  }

  /**
   * Writes one line of a timed transcript: the moment it was said, as
   * {@link #formatMarker(double)} marks it, then who said it.
   *
   * @param start the moment the line was said, in seconds
   * @param name who said it, or null when the transcript does not tell
   * @param text what was said
   * @return {@code [mm:ss · Ns] Name: text}, or {@code [mm:ss · Ns] text}
   *         when the name is null
   */
  public static String formatLine(double start, String name, String text) {
    return formatMarker(start) + " " + formatTurn(name, text);
  }

  /**
   * Writes one turn of a transcript with no moment.
   *
   * @param name who said it, or null when the transcript does not tell
   * @param text what was said
   * @return {@code Name: text}, or the text alone when the name is null
   */
  public static String formatTurn(String name, String text) {
    return name == null ? text : name + ": " + text;
  }

  /**
   * Drops the fraction of a moment.
   *
   * @param seconds the moment, in seconds
   * @return the whole seconds, 0 for a negative or undefined moment
   */
  private static long wholeSeconds(double seconds) {
    return Double.isFinite(seconds) && seconds > 0 ? (long) seconds : 0;
  }
}
