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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.net.URL;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.Test;
import org.springframework.beans.factory.ObjectProvider;

import io.meeds.mcp.server.constant.Origin;
import io.meeds.mcp.server.model.ActingIdentity;
import io.meeds.mcp.server.model.SimpleToolDefinition;
import io.meeds.mcp.server.model.SpaceScopeDeclaration;
import io.meeds.mcp.server.model.SpaceScopeRefusedException;
import io.meeds.mcp.server.plugin.SpaceScopeResolver;
import io.meeds.mcp.server.service.McpToolSpaceScopeService;
import io.meeds.mcp.server.util.McpToolUtils;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Pins the {@code space_scope} declarations the documents add-on ships: which
 * document tools a call limited to one space may use, and how.
 * <p>
 * The tools reaching a document or a folder by its node id declare it as an
 * object argument of the {@code document} resolver, in the {@code hex32}
 * format: the resolver tells the space whose drive holds the node itself,
 * and none for a shortcut, a share link or a node outside a space's drive.
 * A read listing a folder shows its shortcut entries without following them;
 * opening what a shortcut stands for goes through the same resolver, which
 * refuses it. A write is declared when its whole effect stays in the space
 * of the node it names and it takes a single node id, and also a move, a
 * copy or a shortcut, whose destination folder {@code DocumentMcpTool}
 * checks is in the same space, a new version from inline content only, and
 * the node's visibility (which may also mark some of a folder's direct
 * children, all in the same space).
 */
public class DocumentToolDefinitionSpaceScopeTest {

  private static final String      ROOT_BY_SPACE  = "get_root_folder_by_space";

  private static final String      NODE_ID        = "be0688cd7f00010134384ab7e1b15a48";

  private static final String      OTHER_NODE_ID  = "00000000000000000000000000000001";

  private static final long        SPACE_ID       = 12L;

  /**
   * Reads by node id: the id's argument and the other arguments a call may
   * carry.
   */
  private static final Map<String, List<String>> READS = Map.of("get_document_by_id", List.of("document_id"),
                                                                "get_document_content_by_id", List.of("document_id"),
                                                                "get_document_transcription_by_id", List.of("document_id"),
                                                                "list_document_versions", List.of("document_id"),
                                                                "get_document_permissions", List.of("document_id"),
                                                                "get_documents_by_folder_id",
                                                                List.of("folder_id", "files_only", "folders_only", "offset", "limit"),
                                                                "list_folder_children", List.of("folder_id", "offset", "limit", "updated_since"));

  /**
   * Writes by node id, whose whole effect stays in the space of that node.
   */
  private static final Map<String, List<String>> WRITES = Map.of("create_folder", List.of("parent_folder_id", "name"),
                                                                 "create_document", List.of("parent_folder_id", "name", "content"),
                                                                 "create_document_from_template",
                                                                 List.of("parent_folder_id", "name", "document_type"),
                                                                 "rename_document", List.of("document_id", "new_name"),
                                                                 "update_document_description",
                                                                 List.of("document_id", "html_description"),
                                                                 "duplicate_document", List.of("document_id", "prefix"));

  /**
   * Writes by node id that take a second node or content: the destination
   * folder of a move, a copy
   * or a shortcut, which {@code DocumentMcpTool} checks is in the call's
   * space, a move always keeping both nodes on a name clash (its
   * {@code createNewVersion} conflict action removes the moved node outright,
   * past the trash and its shortcuts elsewhere); a new version from inline
   * content or bytes, never a url or a chat attachment; and the node's
   * visibility, which marks the node and, for a folder, possibly some of its
   * direct children, all in the node's space.
   * The id's argument first, then the accepted ones.
   */
  private static final Map<String, List<String>> SECOND_NODE_WRITES =
                                                                     Map.of("move_document",
                                                                            List.of("document_id", "destination_folder_id"),
                                                                            "copy_document", List.of("document_id", "destination_folder_id"),
                                                                            "create_document_shortcut",
                                                                            List.of("document_id", "destination_folder_id", "conflict_action"),
                                                                            "set_document_visibility", List.of("document_id", "hidden"),
                                                                            "add_document_version",
                                                                            List.of("document_id", "content", "base64", "version_summary"));

  /**
   * The schema arguments of {@link #SECOND_NODE_WRITES} the declarations
   * refuse: a move's conflict action, and a new version's url and chat
   * attachment.
   */
  private static final Map<String, List<String>> REFUSED_ARGUMENTS =
                                                                    Map.of("move_document",
                                                                           List.of("conflict_action"),
                                                                           "add_document_version",
                                                                           List.of("url", "attachment_object_type", "attachment_object_id"));

  /**
   * Tools that must stay refused under a space scope, and why:
   * <ul>
   * <li>they search, size or list across spaces and the user's own drive:
   * {@code get_root_folder_for_user}, {@code search_documents},
   * {@code get_documents_size};</li>
   * <li>{@code get_folder_breadcrumb} replaces an ancestor by a node taken
   * from a navigation history that clients feed with any symlink id under any
   * owner id, so it may name the folders of another space;</li>
   * <li>{@code get_folder_tree} walks the folders a shortcut stands for, down
   * to their children, with its default {@code with_children};</li>
   * <li>they name a node and something else, or leave the node's space:
   * {@code share_document}, {@code create_public_link},
   * {@code attach_document_to_content}, {@code update_version_summary} and
   * {@code restore_document_version} (a version id of any node),
   * {@code upload_document} (a url or a chat attachment);</li>
   * <li>{@code delete_document} trashes the node and removes the shortcuts
   * and shares of it that lie in other places, {@code undo_delete_document}
   * names a node of the trash, and the favorites belong to the user.</li>
   * </ul>
   */
  private static final Set<String> REFUSED = Set.of("get_root_folder_for_user",
                                                    "attach_document_to_content",
                                                    "search_documents",
                                                    "get_folder_tree",
                                                    "get_folder_breadcrumb",
                                                    "get_documents_size",
                                                    "upload_document",
                                                    "delete_document",
                                                    "undo_delete_document",
                                                    "restore_document_version",
                                                    "update_version_summary",
                                                    "create_public_link",
                                                    "share_document",
                                                    "favorite_document",
                                                    "unfavorite_document");

  /**
   * Exactly the root folder of a space and the tools above declare a space
   * scope, in the expected shape; every other shipped tool is listed as
   * refused, so a tool added or declared fails here.
   */
  @Test
  public void exactlyTheListedToolsAreDeclaredInTheExpectedShape() {
    Map<String, SimpleToolDefinition> all = shippedTools();
    Set<String> declared = all.entrySet()
                              .stream()
                              .filter(e -> e.getValue().getSpaceScope() != null)
                              .map(Map.Entry::getKey)
                              .collect(Collectors.toSet());
    Set<String> expected = new HashSet<>(READS.keySet());
    expected.addAll(WRITES.keySet());
    expected.addAll(SECOND_NODE_WRITES.keySet());
    expected.add(ROOT_BY_SPACE);
    assertEquals(expected, declared);
    Set<String> others = all.keySet().stream().filter(name -> !declared.contains(name)).collect(Collectors.toSet());
    assertEquals(REFUSED, others);

    SpaceScopeDeclaration root = all.get(ROOT_BY_SPACE).getSpaceScope();
    assertTrue(McpToolSpaceScopeService.isReadTool(all.get(ROOT_BY_SPACE)));
    assertTrue(root.isWellFormedFor(false));
    assertTrue(root.isSpaceKind());
    assertEquals("space_id", root.getSpaceArg());
    assertTrue(root.isUseScopeWhenAbsent());
    assertTrue(root.getAllowedArgs() == null || root.getAllowedArgs().isEmpty());
    assertTrue(root.getFalseOnlyArgs() == null || root.getFalseOnlyArgs().isEmpty());

    READS.forEach((name, args) -> assertObjectDeclaration(all.get(name), name, args, false));
    WRITES.forEach((name, args) -> assertObjectDeclaration(all.get(name), name, args, true));
    SECOND_NODE_WRITES.forEach((name, args) -> {
      SimpleToolDefinition tool = all.get(name);
      SpaceScopeDeclaration scope = tool.getSpaceScope();
      assertTrue(name, tool.isRequireApproval());
      assertFalse(name, McpToolSpaceScopeService.isReadTool(tool));
      assertTrue(name, scope.isWellFormedFor(true));
      assertTrue(name, scope.isObjectKind());
      assertEquals(name, "hex32", scope.getIdFormat());
      assertEquals(name, DocumentSpaceScopeResolver.NAME, scope.getResolver());
      assertEquals(name, args.get(0), scope.getObjectArg());
      assertEquals(name, args.subList(1, args.size()), scope.getAllowedArgs());
      REFUSED_ARGUMENTS.getOrDefault(name, List.of())
                       .forEach(argument -> assertFalse(name + " " + argument, scope.getAllowedArgs().contains(argument)));
    });
  }

  /**
   * Through the real space scope service, a move, a copy, a shortcut, a
   * visibility change and a new version of a node of the call's space run as
   * an approved proposal with the arguments they accept, and are refused for
   * a node of another space, outside a proposal, and with an argument the
   * declaration refuses: a username for each, a move's conflict action, and
   * a url or a chat attachment for a new version. The destination folder is not the space
   * scope's to check: {@code DocumentMcpTool} checks it.
   * Mutants: a declaration dropped, its object argument changed, or an
   * argument added to the accepted ones (the url above all).
   */
  @Test
  public void secondNodeWritesReachTheNodesOfTheSpaceOnly() {
    Map<String, SimpleToolDefinition> all = shippedTools();
    McpToolSpaceScopeService service = new McpToolSpaceScopeService(providerOf(resolver()));
    ActingIdentity reader = ActingIdentity.agent("agent-docs", "DOCS", Origin.MENTION, SPACE_ID);
    ActingIdentity proposal = ActingIdentity.agent("agent-docs", "DOCS", Origin.PROPOSAL, SPACE_ID);
    Map<String, String> values = Map.of("destination_folder_id", "\"" + OTHER_NODE_ID + "\"",
                                        "conflict_action", "\"keepBoth\"",
                                        "hidden", "true",
                                        "content", "\"v2\"",
                                        "base64", "\"AAAA\"",
                                        "version_summary", "\"Second draft\"");

    SECOND_NODE_WRITES.forEach((name, args) -> {
      SimpleToolDefinition tool = all.get(name);
      StringBuilder rest = new StringBuilder();
      args.subList(1, args.size()).forEach(arg -> rest.append(",\"").append(arg).append("\":").append(values.get(arg)));
      String inSpace = "{\"document_id\":\"%s\"%s}".formatted(NODE_ID, rest);
      assertEquals(name, inSpace, service.enforce(name, tool, inSpace, proposal));
      assertThrows(name, SpaceScopeRefusedException.class, () -> service.enforce(name, tool, inSpace, reader));
      String otherSpace = "{\"document_id\":\"%s\"%s}".formatted(OTHER_NODE_ID, rest);
      assertThrows(name, SpaceScopeRefusedException.class, () -> service.enforce(name, tool, otherSpace, proposal));
      String withUser = "{\"document_id\":\"%s\"%s,\"username\":\"mary\"}".formatted(NODE_ID, rest);
      assertThrows(name, SpaceScopeRefusedException.class, () -> service.enforce(name, tool, withUser, proposal));
      REFUSED_ARGUMENTS.getOrDefault(name, List.of()).forEach(argument -> {
        String refused = "{\"document_id\":\"%s\",\"%s\":\"x\"}".formatted(NODE_ID, argument);
        assertThrows(name + " " + argument, SpaceScopeRefusedException.class, () -> service.enforce(name, tool, refused, proposal));
      });
    });
  }

  /**
   * Through the real space scope service and the shipped definitions, with a
   * resolver that puts one node in the call's space and another elsewhere:
   * a read runs for a node of the space and for it only, a write runs for an
   * approved proposal only, an id in any other shape, a missing node id and
   * an argument the declaration doesn't list are refused, and a refused tool
   * stays refused.
   */
  @Test
  public void aCallLimitedToASpaceReachesTheNodesOfThatSpaceOnly() {
    Map<String, SimpleToolDefinition> all = shippedTools();
    McpToolSpaceScopeService service = new McpToolSpaceScopeService(providerOf(resolver()));
    ActingIdentity reader = ActingIdentity.agent("agent-docs", "DOCS", Origin.MENTION, SPACE_ID);
    ActingIdentity proposal = ActingIdentity.agent("agent-docs", "DOCS", Origin.PROPOSAL, SPACE_ID);

    assertEquals("{\"space_id\":12}", service.enforce(ROOT_BY_SPACE, all.get(ROOT_BY_SPACE), "{}", reader));
    READS.forEach((name, args) -> checkTool(service, all.get(name), name, args, reader));
    WRITES.forEach((name, args) -> checkTool(service, all.get(name), name, args, proposal));
    WRITES.forEach((name, args) -> {
      String input = inputOf(args.get(0), NODE_ID);
      assertThrows(name, SpaceScopeRefusedException.class, () -> service.enforce(name, all.get(name), input, reader));
    });
    REFUSED.forEach(name -> {
      String input = "{\"document_id\":\"%s\",\"folder_id\":\"%s\",\"space_id\":12}".formatted(NODE_ID, NODE_ID);
      assertThrows(name, SpaceScopeRefusedException.class, () -> service.enforce(name, all.get(name), input, proposal));
    });
    assertFalse(all.isEmpty());
  }

  /**
   * Through the real space scope service, a call limited to a space can't
   * name a space by its name: {@code space_name} isn't an argument the root
   * folder's declaration accepts, so the server refuses it before the tool
   * runs, whichever space the name designates, alone or beside the scope's
   * own {@code space_id}; the scope's space is reached by leaving the space
   * out. A name can't stand in for an out-of-scope id.
   * Mutants: {@code space_name} added to the accepted arguments.
   */
  @Test
  public void aCallLimitedToASpaceCannotNameASpace() {
    Map<String, SimpleToolDefinition> all = shippedTools();
    SimpleToolDefinition root = all.get(ROOT_BY_SPACE);
    McpToolSpaceScopeService service = new McpToolSpaceScopeService(providerOf(resolver()));
    ActingIdentity reader = ActingIdentity.agent("agent-docs", "DOCS", Origin.MENTION, SPACE_ID);

    for (String input : List.of("{\"space_name\":\"Marketing\"}",
                                "{\"space_name\":\"Legal\"}",
                                "{\"space_id\":12,\"space_name\":\"Marketing\"}",
                                "{\"space_id\":13}")) {
      SpaceScopeRefusedException e = assertThrows(input,
                                                   SpaceScopeRefusedException.class,
                                                   () -> service.enforce(ROOT_BY_SPACE, root, input, reader));
      assertTrue(e.getMessage(), e.getMessage().contains("limited to the space with id 12"));
    }
    assertEquals("{\"space_id\":12}", service.enforce(ROOT_BY_SPACE, root, "{}", reader));
  }

  /**
   * The tools taking a space accept it by its id or by its name, both
   * optional in the schema, and say not to guess an id; the root folder of a
   * space requires neither, so the scope fills its space in.
   * Mutants: a {@code space_name} property dropped, or {@code space_id}
   * required again.
   */
  @Test
  public void theSpaceToolsTakeASpaceByIdOrByName() {
    Map<String, SimpleToolDefinition> all = shippedTools();
    for (String name : List.of(ROOT_BY_SPACE, "search_documents", "share_document")) {
      JsonNode schema = JsonMapper.builder().build().readTree(all.get(name).getInputSchema());
      assertEquals(name, "integer", schema.path("properties").path("space_id").path("type").asString());
      assertEquals(name, "string", schema.path("properties").path("space_name").path("type").asString());
      Set<String> required = new HashSet<>();
      schema.path("required").forEach(node -> required.add(node.asString()));
      assertFalse(name, required.contains("space_id") || required.contains("space_name"));
    }
    assertTrue(all.get(ROOT_BY_SPACE).getDescription().contains("Never guess a space_id"));
  }

  /**
   * Checks one declared tool against the real service.
   *
   * @param service the real space scope service
   * @param tool    the tool's shipped definition
   * @param name    the tool name
   * @param args    the arguments the tool declares, the node id's first
   * @param identity the acting identity that may run the tool
   */
  private static void checkTool(McpToolSpaceScopeService service,
                                SimpleToolDefinition tool,
                                String name,
                                List<String> args,
                                ActingIdentity identity) {
    String idArg = args.get(0);
    String sent = inputOf(idArg, NODE_ID);
    assertEquals(name, sent, service.enforce(name, tool, sent, identity));
    for (String input : List.of(inputOf(idArg, OTHER_NODE_ID),
                                inputOf(idArg, "7"),
                                "{\"%s\":7}".formatted(idArg),
                                inputOf(idArg, NODE_ID.toUpperCase()),
                                inputOf(idArg, " " + NODE_ID),
                                inputOf(idArg, "00000000000000000000000000000002"),
                                "{}")) {
      assertThrows(name + " " + input, SpaceScopeRefusedException.class, () -> service.enforce(name, tool, input, identity));
    }
    String withExtra = "{\"%s\":\"%s\",\"base64\":\"AAAA\"}".formatted(idArg, NODE_ID);
    assertThrows(name, SpaceScopeRefusedException.class, () -> service.enforce(name, tool, withExtra, identity));
  }

  /**
   * @param arg the argument name
   * @param id  its value
   * @return the call's arguments
   */
  private static String inputOf(String arg, String id) {
    return "{\"%s\":\"%s\"}".formatted(arg, id);
  }

  /**
   * Checks the shape of a by-id declaration against the tool's own schema.
   *
   * @param tool  the tool's definition
   * @param name  the tool name
   * @param args  the arguments the tool declares, the node id's first
   * @param write true when the tool is a write
   */
  private static void assertObjectDeclaration(SimpleToolDefinition tool, String name, List<String> args, boolean write) {
    assertNotNull(name, tool);
    SpaceScopeDeclaration scope = tool.getSpaceScope();
    assertEquals(name, write, !McpToolSpaceScopeService.isReadTool(tool));
    assertTrue(name, scope.isWellFormedFor(write));
    assertTrue(name, scope.isObjectKind());
    assertTrue(name, scope.isHexId());
    assertEquals(name, "hex32", scope.getIdFormat());
    assertEquals(name, DocumentSpaceScopeResolver.NAME, scope.getResolver());
    assertEquals(name, args.get(0), scope.getObjectArg());
    assertEquals(name, args.subList(1, args.size()), scope.getAllowedArgs() == null ? List.of() : scope.getAllowedArgs());
    assertTrue(name, scope.getFalseOnlyArgs() == null || scope.getFalseOnlyArgs().isEmpty());
    assertTrue(name, scope.getWhenAbsent() == null);
    JsonNode schema = JsonMapper.builder().build().readTree(tool.getInputSchema());
    Set<String> properties = new HashSet<>();
    schema.path("properties").propertyNames().forEach(properties::add);
    assertTrue(name + " " + properties, properties.containsAll(args));
    Set<String> required = new HashSet<>();
    schema.path("required").forEach(node -> required.add(node.asString()));
    assertTrue(name, required.contains(scope.getObjectArg()));
    assertFalse(name, properties.contains("attachment_object_id") || properties.contains("url") || properties.contains("base64"));
  }

  /**
   * @return a resolver putting {@code NODE_ID} in the call's space and
   *         {@code OTHER_NODE_ID} in another one
   */
  private static SpaceScopeResolver resolver() {
    SpaceScopeResolver resolver = mock(SpaceScopeResolver.class);
    when(resolver.getName()).thenReturn(DocumentSpaceScopeResolver.NAME);
    when(resolver.spaceOf(NODE_ID)).thenReturn(SPACE_ID);
    when(resolver.spaceOf(OTHER_NODE_ID)).thenReturn(13L);
    return resolver;
  }

  /**
   * @param resolver the only contributed resolver
   * @return the provider of the contributed resolvers
   */
  @SuppressWarnings("unchecked")
  private static ObjectProvider<SpaceScopeResolver> providerOf(SpaceScopeResolver resolver) {
    ObjectProvider<SpaceScopeResolver> provider = mock(ObjectProvider.class);
    when(provider.orderedStream()).thenAnswer(invocation -> java.util.stream.Stream.of(resolver));
    return provider;
  }

  /**
   * @return every shipped document tool definition, by name
   */
  private static Map<String, SimpleToolDefinition> shippedTools() {
    try {
      URL classes = DocumentMcpTool.class.getProtectionDomain().getCodeSource().getLocation();
      URL url = new File(new File(classes.toURI()), "ai-tool-definitions.json").toURI().toURL();
      List<SimpleToolDefinition> tools = McpToolUtils.parseToolDefinitions(url);
      return tools.stream().collect(Collectors.toMap(SimpleToolDefinition::getName, tool -> tool));
    } catch (Exception e) {
      throw new IllegalStateException("The document tool definitions couldn't be read", e);
    }
  }

}
