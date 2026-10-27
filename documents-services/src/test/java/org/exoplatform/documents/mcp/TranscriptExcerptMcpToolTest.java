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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.documents.model.FileNode;
import org.exoplatform.documents.model.TranscriptionState;
import org.exoplatform.documents.service.DocumentFileService;
import org.exoplatform.services.security.Identity;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.manager.IdentityManager;

import io.meeds.mcp.server.model.SimpleToolDefinition;
import io.meeds.mcp.server.util.McpToolUtils;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class TranscriptExcerptMcpToolTest {

  private static final String      USERNAME = "john";

  private static final long        USER     = 100L;

  private static final String      DOC      = "be0688cd7f00010134384ab7e1b15a48";

  /**
   * Six timed lines: one straddling 23:00, one by a guest, one with no
   * speaker, one with no end, one at 31:00 exactly, one long after.
   */
  private static final String      SEGMENTS = """
      [{"start":1370.4,"end":1382,"text":" Let's talk pricing. ","speaker":"1"},
       {"start":1382,"end":1400,"text":"Per seat, I think.","speaker":"2"},
       {"start":1400,"end":1410,"text":"Agreed."},
       {"start":1500,"text":"Next point.","speaker":"3"},
       {"start":1860,"end":1870,"text":"Out of range.","speaker":"1"},
       {"start":3590,"end":3600,"text":"Bye.","speaker":"1"}]
      """;

  /**
   * Five stored chapters, three listed: one with a blank title and one with
   * no start are neither listed nor numbered.
   */
  private static final String      CHAPTERS = """
      [{"start":0,"index":0,"title":"Welcome"},
       {"start":600,"index":1,"title":"  "},
       {"start":1380,"index":2,"title":" Pricing "},
       {"index":3,"title":"No start"},
       {"start":1860,"index":4,"title":"Wrap-up"}]
      """;

  @Mock
  private DocumentFileService      documentFileService;

  @Mock
  private IdentityManager          identityManager;

  private TranscriptExcerptMcpTool tool;

  /**
   * Builds the tool acting as {@value #USERNAME}, on a recording named
   * {@code Office hours.mp4} whose speaker 1 is John Smith and speaker 2 the
   * guest Alice.
   *
   * @throws Exception never
   */
  @BeforeEach
  void setUp() throws Exception {
    tool = new TranscriptExcerptMcpTool(documentFileService, identityManager) {
      @Override
      public Identity getCurrentUserAclIdentity() {
        return new Identity(USERNAME);
      }
    };
    lenient().when(identityManager.getOrCreateUserIdentity(USERNAME))
             .thenReturn(new org.exoplatform.social.core.identity.model.Identity(String.valueOf(USER)));
    FileNode file = new FileNode();
    file.setName("Office hours.mp4");
    lenient().when(documentFileService.getDocumentById(DOC, USERNAME)).thenReturn(file);
    lenient().when(documentFileService.getTranscriptionSegments(DOC, USER)).thenReturn(SEGMENTS);
    lenient().when(documentFileService.getTranscriptionSpeakers(DOC, USER)).thenReturn(Map.of("1", "11", "2", "name:Alice"));
    org.exoplatform.social.core.identity.model.Identity john = new org.exoplatform.social.core.identity.model.Identity("11");
    Profile profile = new Profile(john);
    profile.setProperty(Profile.FULL_NAME, "John Smith");
    john.setProfile(profile);
    lenient().when(identityManager.getIdentity(11L)).thenReturn(john);
  }

  /**
   * A range gives every line overlapping it, the one started before it
   * included and the one starting at its end excluded, each with its moment
   * and its speaker: a user by full name, a guest by name, an unnamed speaker
   * by number, a line with no speaker with no name.
   *
   * @throws Exception never
   */
  @Test
  void aRangeGivesTheLinesSaidInItWithTheirMomentAndSpeaker() throws Exception {
    assertEquals("""
        Transcript of "Office hours.mp4" from 23:00 to 31:00:
        [22:50 · 1370s] John Smith: Let's talk pricing.
        [23:02 · 1382s] Alice: Per seat, I think.
        [23:20 · 1400s] Agreed.
        [25:00 · 1500s] Speaker 3: Next point.""", tool.getTranscriptExcerpt(DOC, null, "23:00", "31:00"));
  }

  /**
   * A line ending exactly where the range starts is not in it, and a line
   * with no end is in it only when it starts in it.
   *
   * @throws Exception never
   */
  @Test
  void theRangeBoundsAreStrict() throws Exception {
    String excerpt = tool.getTranscriptExcerpt(DOC, null, "1382", "1501");
    assertFalse(excerpt.contains("pricing"));
    assertTrue(excerpt.contains("Per seat"));
    assertTrue(excerpt.contains("Next point"));
    assertFalse(tool.getTranscriptExcerpt(DOC, null, "1501", "1800").contains("Next point"));
  }

  /**
   * A moment is read as mm:ss, h:mm:ss or seconds, with or without a
   * fraction; any other form is refused with the accepted ones.
   */
  @Test
  void aMomentIsReadInThreeForms() {
    assertEquals(1380, TranscriptExcerptMcpTool.parseMoment("23:00", "from"));
    assertEquals(3750, TranscriptExcerptMcpTool.parseMoment(" 1:02:30 ", "from"));
    assertEquals(1380, TranscriptExcerptMcpTool.parseMoment("1380", "from"));
    assertEquals(1380.5, TranscriptExcerptMcpTool.parseMoment("1380.5", "from"));
    assertEquals(65, TranscriptExcerptMcpTool.parseMoment("1:05", "from"));
    for (String bad : List.of("23:60", "1:60:00", "abc", "-5", "1:2:3", "12345678", "1:02:30:00")) {
      IllegalArgumentException refusal = assertThrows(IllegalArgumentException.class,
                                                      () -> TranscriptExcerptMcpTool.parseMoment(bad, "from"),
                                                      bad);
      assertTrue(refusal.getMessage().contains("mm:ss"), bad);
    }
    assertThrows(IllegalArgumentException.class, () -> TranscriptExcerptMcpTool.parseMoment(" ", "to"));
  }

  /**
   * A range ending before it starts, or empty, is refused before anything is
   * read, and so is a call with no document id.
   *
   * @throws Exception never
   */
  @Test
  void aRangeEndingBeforeItStartsIsRefused() throws Exception {
    assertThrows(IllegalArgumentException.class, () -> tool.getTranscriptExcerpt(DOC, null, "31:00", "23:00"));
    assertThrows(IllegalArgumentException.class, () -> tool.getTranscriptExcerpt(DOC, null, "23:00", "1380"));
    assertThrows(IllegalArgumentException.class, () -> tool.getTranscriptExcerpt(" ", null, "23:00", "31:00"));
    verify(documentFileService, never()).getDocumentById(anyString(), anyString());
  }

  /**
   * A range longer than the most one call reads stops there and says where to
   * call again from; one that reaches past the transcript's end says nothing
   * of the kind.
   *
   * @throws Exception never
   */
  @Test
  void aLongRangeStopsAtTheMostOneCallReads() throws Exception {
    String capped = tool.getTranscriptExcerpt(DOC, null, "1300", "3600");
    assertTrue(capped.startsWith("Transcript of \"Office hours.mp4\" from 21:40 to 51:40:"));
    assertTrue(capped.contains("Out of range."));
    assertFalse(capped.contains("Bye."));
    assertTrue(capped.endsWith("The excerpt stops at 51:40: one call reads 30 minutes at most. Call again with from 51:40 and to 1:00:00 for what follows."));

    String fractional = tool.getTranscriptExcerpt(DOC, null, "0", "1800.5");
    assertTrue(fractional.endsWith("Call again with from 30:00 and to 30:01 for what follows."), fractional);

    String last = tool.getTranscriptExcerpt(DOC, null, "3000", "9000");
    assertTrue(last.endsWith("[59:50 · 3590s] John Smith: Bye."));
  }

  /**
   * A range where nothing is said says so, with where the transcript ends.
   *
   * @throws Exception never
   */
  @Test
  void aRangeWithNothingSaidSaysWhereTheTranscriptEnds() throws Exception {
    assertEquals("Nothing is said from 1:10:00 to 1:20:00 in the transcript of \"Office hours.mp4\", which ends at 1:00:00.",
                 tool.getTranscriptExcerpt(DOC, null, "1:10:00", "1:20:00"));
  }

  /**
   * A transcript with no timed lines, or unreadable ones, points to the whole
   * transcript, which says why when there is none.
   *
   * @throws Exception never
   */
  @Test
  void aTranscriptWithoutTimedLinesPointsToTheWholeOne() throws Exception {
    when(documentFileService.getTranscriptionSegments(DOC, USER)).thenReturn(null);
    IllegalStateException none = assertThrows(IllegalStateException.class, () -> tool.getTranscriptExcerpt(DOC, null, "0", "60"));
    assertTrue(none.getMessage().contains("get_document_transcription_by_id"));

    when(documentFileService.getTranscriptionSegments(DOC, USER)).thenReturn("{not json");
    assertThrows(IllegalStateException.class, () -> tool.getTranscriptExcerpt(DOC, null, "0", "60"));

    when(documentFileService.getTranscriptionSegments(DOC, USER)).thenReturn("{\"start\":0}");
    assertThrows(IllegalStateException.class, () -> tool.getTranscriptExcerpt(DOC, null, "0", "60"));
  }

  /**
   * A user who may not read the file gets the refusal, and nothing of the
   * transcript is read.
   *
   * @throws Exception never
   */
  @Test
  void aUserWhoCannotReadTheFileReadsNothing() throws Exception {
    when(documentFileService.getDocumentById(DOC, USERNAME)).thenThrow(new IllegalAccessException("denied"))
                                                            .thenThrow(new ObjectNotFoundException("gone"));
    assertThrows(IllegalAccessException.class, () -> tool.getTranscriptExcerpt(DOC, null, "0", "60"));
    assertThrows(ObjectNotFoundException.class, () -> tool.getTranscriptExcerpt(DOC, null, "0", "60"));
    verify(documentFileService, never()).getTranscriptionSegments(anyString(), anyLong());
  }

  /**
   * A chapter runs from its start to the next listed chapter's, chapters with
   * no title or no start being neither listed nor numbered, and the heading
   * names it.
   *
   * @throws Exception never
   */
  @Test
  void aChapterRunsToTheNextOne() throws Exception {
    when(documentFileService.getTranscriptionChapters(DOC, USER)).thenReturn(CHAPTERS);
    assertEquals("""
        Transcript of "Office hours.mp4", chapter 2 "Pricing", from 23:00 to 31:00:
        [22:50 · 1370s] John Smith: Let's talk pricing.
        [23:02 · 1382s] Alice: Per seat, I think.
        [23:20 · 1400s] Agreed.
        [25:00 · 1500s] Speaker 3: Next point.""", tool.getTranscriptExcerpt(DOC, 2, null, ""));
  }

  /**
   * The last chapter runs to the recording's measured duration, else to the
   * end of the last timed line, and a long one stops at the most one call
   * reads, saying the range to call again with.
   *
   * @throws Exception never
   */
  @Test
  void theLastChapterRunsToTheEndOfTheRecording() throws Exception {
    when(documentFileService.getTranscriptionChapters(DOC, USER)).thenReturn(CHAPTERS);
    String last = tool.getTranscriptExcerpt(DOC, 3, null, null);
    assertTrue(last.startsWith("Transcript of \"Office hours.mp4\", chapter 3 \"Wrap-up\", from 31:00 to 1:00:00:"), last);
    assertTrue(last.contains("Out of range.") && last.endsWith("Bye."), last);

    when(documentFileService.getTranscriptionState(DOC, USER)).thenReturn(TranscriptionState.builder().durationSeconds(5400L).build());
    String measured = tool.getTranscriptExcerpt(DOC, 3, null, null);
    assertTrue(measured.startsWith("Transcript of \"Office hours.mp4\", chapter 3 \"Wrap-up\", from 31:00 to 1:01:00:"), measured);
    assertTrue(measured.endsWith("Bye."), measured);

    when(documentFileService.getTranscriptionChapters(DOC, USER)).thenReturn("[{\"start\":0,\"title\":\"All\"}]");
    String capped = tool.getTranscriptExcerpt(DOC, 1, null, null);
    assertTrue(capped.startsWith("Transcript of \"Office hours.mp4\", chapter 1 \"All\", from 00:00 to 30:00:"), capped);
    assertTrue(capped.endsWith("Call again with from 30:00 and to 1:00:00 for what follows."), capped);
  }

  /**
   * A chapter number the transcript does not have, or one asked of a
   * transcript with no chapters, is refused with what to pass instead; a call
   * giving both a chapter and a range, or neither, is refused before anything
   * is read.
   *
   * @throws Exception never
   */
  @Test
  void aChapterTheTranscriptDoesNotHaveIsRefused() throws Exception {
    assertThrows(IllegalArgumentException.class, () -> tool.getTranscriptExcerpt(DOC, 0, null, null));
    assertThrows(IllegalArgumentException.class, () -> tool.getTranscriptExcerpt(DOC, 1, "23:00", "31:00"));
    assertThrows(IllegalArgumentException.class, () -> tool.getTranscriptExcerpt(DOC, 1, null, "31:00"));
    verify(documentFileService, never()).getDocumentById(anyString(), anyString());

    when(documentFileService.getTranscriptionChapters(DOC, USER)).thenReturn(CHAPTERS).thenReturn(null);
    IllegalArgumentException beyond = assertThrows(IllegalArgumentException.class,
                                                   () -> tool.getTranscriptExcerpt(DOC, 4, null, null));
    assertEquals("The transcript of this file has 3 chapters: pass a chapter from 1 to 3.", beyond.getMessage());
    IllegalArgumentException none = assertThrows(IllegalArgumentException.class,
                                                 () -> tool.getTranscriptExcerpt(DOC, 1, null, null));
    assertTrue(none.getMessage().contains("from and to"), none.getMessage());
  }

  /**
   * Called with neither a chapter nor a range, the tool lists the chapters
   * with the numbers they are read by, each with its moment, title and
   * summary, chapters with no title or no start being neither listed nor
   * numbered; a transcript with no chapters says so. The transcript itself is
   * not read.
   *
   * @throws Exception never
   */
  @Test
  void calledAloneTheToolListsTheChapters() throws Exception {
    when(documentFileService.getTranscriptionChapters(DOC, USER)).thenReturn("""
        [{"start":0,"title":"Welcome","summary":" Hello all. "},
         {"start":600,"title":"  "},
         {"start":1380,"title":" Pricing ","summary":"Per seat or flat."},
         {"title":"No start"},
         {"start":3919.4,"title":"Wrap-up"}]
        """).thenReturn(null);
    assertEquals("""
        Chapters of "Office hours.mp4":
        1. [00:00 · 0s] Welcome: Hello all.
        2. [23:00 · 1380s] Pricing: Per seat or flat.
        3. [1:05:19 · 3919s] Wrap-up

        Read one with chapter (its number), or a part by time with from and to.""",
                 tool.getTranscriptExcerpt(DOC, null, null, " "));
    assertEquals("The transcript of \"Office hours.mp4\" has no chapters: read a part of it by time with from and to, or the whole of it with get_document_transcription_by_id.",
                 tool.getTranscriptExcerpt(DOC, null, "", null));
    verify(documentFileService, never()).getTranscriptionSegments(anyString(), anyLong());
  }

  /**
   * A user who may not read the file gets no chapter list.
   *
   * @throws Exception never
   */
  @Test
  void aUserWhoCannotReadTheFileListsNoChapters() throws Exception {
    when(documentFileService.getDocumentById(DOC, USERNAME)).thenThrow(new IllegalAccessException("denied"));
    assertThrows(IllegalAccessException.class, () -> tool.getTranscriptExcerpt(DOC, null, null, null));
    verify(documentFileService, never()).getTranscriptionChapters(anyString(), anyLong());
  }

  /**
   * The tool's only public method is the one its definition names: every
   * public method of an MCP tool plugin becomes a tool, and one with no
   * definition is dropped.
   */
  @Test
  void theOnlyToolIsTheExcerpt() {
    List<String> tools = Arrays.stream(TranscriptExcerptMcpTool.class.getDeclaredMethods())
                               .filter(method -> Modifier.isPublic(method.getModifiers()))
                               .map(Method::getName)
                               .toList();
    assertEquals(List.of("getTranscriptExcerpt"), tools);
  }

  /**
   * The definition's arguments are the method's parameters: the server binds
   * each snake_case argument to the camelCase parameter of that name, and an
   * argument matching none arrives as null, silently. The parameter names
   * survive compilation because the module compiles with {@code -parameters}.
   *
   * @throws Exception never
   */
  @Test
  void theDefinitionArgumentsAreTheMethodParameters() throws Exception {
    URL classes = TranscriptExcerptMcpTool.class.getProtectionDomain().getCodeSource().getLocation();
    URL url = new File(new File(classes.toURI()), "ai-tool-definitions.json").toURI().toURL();
    SimpleToolDefinition definition = McpToolUtils.parseToolDefinitions(url)
                                                  .stream()
                                                  .filter(tool -> "get_transcript_excerpt".equals(tool.getName()))
                                                  .findFirst()
                                                  .orElseThrow();
    JsonNode schema = JsonMapper.builder().build().readTree(definition.getInputSchema());
    List<String> arguments = schema.get("properties")
                                   .propertyNames()
                                   .stream()
                                   .map(name -> Pattern.compile("_([a-z])").matcher(name).replaceAll(m -> m.group(1).toUpperCase()))
                                   .toList();
    Method method = TranscriptExcerptMcpTool.class.getMethod("getTranscriptExcerpt",
                                                             String.class,
                                                             Integer.class,
                                                             String.class,
                                                             String.class);
    assertEquals(Stream.of(method.getParameters()).map(Parameter::getName).toList(), arguments);
    assertEquals(List.of("document_id"), schema.get("required").valueStream().map(JsonNode::asString).toList());
  }

}
