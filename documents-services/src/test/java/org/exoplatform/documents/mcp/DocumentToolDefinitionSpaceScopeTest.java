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
 * refuses it. A write is declared only when its whole effect stays in the
 * space of the node it names and it takes a single node id.
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
                                                                "get_documents_by_folder_id",
                                                                List.of("folder_id", "files_only", "folders_only", "offset", "limit"),
                                                                "list_folder_children", List.of("folder_id", "offset", "limit"),
                                                                "get_folder_breadcrumb", List.of("folder_id"));

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
   * Tools that must stay refused under a space scope, and why:
   * <ul>
   * <li>they search, size or list across spaces and the user's own drive:
   * {@code get_root_folder_for_user}, {@code search_documents},
   * {@code get_documents_size};</li>
   * <li>{@code get_folder_tree} walks the folders a shortcut stands for, down
   * to their children, with its default {@code with_children};</li>
   * <li>they name two nodes or a node and something else, or leave the node's
   * space: {@code move_document}, {@code copy_document},
   * {@code create_document_shortcut}, {@code share_document},
   * {@code create_public_link}, {@code attach_document_to_content},
   * {@code update_version_summary} and {@code restore_document_version} (a
   * version id of any node), {@code add_version}, {@code upload_document}
   * (a url or a chat attachment), {@code set_document_visibility};</li>
   * <li>{@code delete_document} trashes the node and removes the shortcuts
   * and shares of it that lie in other places, {@code undo_delete_document}
   * names a node of the trash, and the favorites belong to the user.</li>
   * </ul>
   */
  private static final Set<String> REFUSED = Set.of("get_root_folder_for_user",
                                                    "attach_document_to_content",
                                                    "search_documents",
                                                    "get_folder_tree",
                                                    "get_documents_size",
                                                    "upload_document",
                                                    "move_document",
                                                    "copy_document",
                                                    "delete_document",
                                                    "undo_delete_document",
                                                    "restore_document_version",
                                                    "update_version_summary",
                                                    "set_document_visibility",
                                                    "add_document_version",
                                                    "create_public_link",
                                                    "create_document_shortcut",
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
