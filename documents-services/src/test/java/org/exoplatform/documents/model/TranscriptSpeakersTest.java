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
package org.exoplatform.documents.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

/**
 * Pins how the speakers of a transcript are read and its lines written
 * (EXO-92123).
 */
class TranscriptSpeakersTest {

  private static final Function<String, String> FULL_NAMES = Map.of("42", "Julien Dupont", "43", " ")::get;

  /**
   * A guest value starts with the lower-case prefix, matched case-sensitively;
   * anything else is an identity id.
   */
  @Test
  void aGuestIsTheLowerCaseNamePrefixOnly() {
    assertTrue(TranscriptSpeakers.isGuest("name:Jean-Luc"));
    assertEquals("Jean-Luc", TranscriptSpeakers.guestName("name:Jean-Luc"));
    assertEquals("a=b:c", TranscriptSpeakers.guestName("name:a=b:c"));
    for (String notGuest : new String[] { null, "42", "Name:Jean", "NAME:Jean", " name:Jean", "name" }) {
      assertFalse(TranscriptSpeakers.isGuest(notGuest), "Expected '" + notGuest + "' to be no guest");
      assertNull(TranscriptSpeakers.guestName(notGuest));
    }
  }

  /**
   * A speaker is named after the person, the guest, or {@code Speaker N} when
   * nobody is named, the person is gone, or the lookup has no name; a sentence
   * with no speaker has no name.
   */
  @Test
  void aSpeakerIsNamedAfterThePersonTheGuestOrItsNumber() {
    Map<String, String> speakers = Map.of("1", "42", "2", "name:Jean-Luc", "3", "41", "4", "43", "5", "name:");

    assertEquals("Julien Dupont", TranscriptSpeakers.speakerName("1", speakers, FULL_NAMES));
    assertEquals("Jean-Luc", TranscriptSpeakers.speakerName("2", speakers, FULL_NAMES));
    assertEquals("Speaker 3", TranscriptSpeakers.speakerName("3", speakers, FULL_NAMES), "Expected a gone person unnamed");
    assertEquals("Speaker 4", TranscriptSpeakers.speakerName("4", speakers, FULL_NAMES), "Expected a blank full name unnamed");
    assertEquals("Speaker 5", TranscriptSpeakers.speakerName("5", speakers, FULL_NAMES), "Expected an empty guest name unnamed");
    assertEquals("Speaker 6", TranscriptSpeakers.speakerName("6", speakers, FULL_NAMES));
    assertEquals("Speaker 1", TranscriptSpeakers.speakerName("1", null, FULL_NAMES));
    assertNull(TranscriptSpeakers.speakerName(null, speakers, FULL_NAMES), "Expected a sentence with no speaker to have no name");
    assertNull(TranscriptSpeakers.speakerName(" ", speakers, FULL_NAMES));
    assertEquals("Speaker 1", TranscriptSpeakers.speakerName("1", speakers, null));
    assertEquals("Jean-Luc", TranscriptSpeakers.speakerName("2", speakers, id -> {
      throw new AssertionError("A guest is never looked up: " + id);
    }));
  }

  /**
   * A moment is written {@code mm:ss}, {@code h:mm:ss} past an hour, its
   * fraction dropped, a negative or undefined moment being 0.
   */
  @Test
  void aMomentIsWrittenInMinutesThenHours() {
    assertEquals("00:00", TranscriptSpeakers.formatMoment(0));
    assertEquals("00:59", TranscriptSpeakers.formatMoment(59.99));
    assertEquals("59:59", TranscriptSpeakers.formatMoment(3599));
    assertEquals("1:00:00", TranscriptSpeakers.formatMoment(3600));
    assertEquals("1:02:05", TranscriptSpeakers.formatMoment(3725.4));
    assertEquals("00:00", TranscriptSpeakers.formatMoment(-3));
    assertEquals("00:00", TranscriptSpeakers.formatMoment(Double.NaN));
    assertEquals("00:00", TranscriptSpeakers.formatMoment(Double.POSITIVE_INFINITY));
  }

  /**
   * The marker carries the moment and its whole seconds, as the documents
   * tools write it for an assistant.
   */
  @Test
  void aMarkerCarriesTheMomentAndItsWholeSeconds() {
    assertEquals("[00:07 · 7s]", TranscriptSpeakers.formatMarker(7.9));
    assertEquals("[1:02:05 · 3725s]", TranscriptSpeakers.formatMarker(3725));
    assertEquals("[00:00 · 0s]", TranscriptSpeakers.formatMarker(-1));
  }

  /**
   * A line carries its marker, then who said it when known; a turn with no
   * moment carries only who said it.
   */
  @Test
  void aLineCarriesItsMarkerThenWhoSaidIt() {
    assertEquals("[01:05 · 65s] Julien Dupont: Hello.", TranscriptSpeakers.formatLine(65.2, "Julien Dupont", "Hello."));
    assertEquals("[01:05 · 65s] Hello.", TranscriptSpeakers.formatLine(65.2, null, "Hello."));
    assertEquals("Speaker 2: Yes.", TranscriptSpeakers.formatTurn("Speaker 2", "Yes."));
    assertEquals("Yes.", TranscriptSpeakers.formatTurn(null, "Yes."));
  }
}
