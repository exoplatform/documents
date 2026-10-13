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

import java.io.File;
import java.net.URL;
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
import io.meeds.mcp.server.service.McpToolSpaceScopeService;
import io.meeds.mcp.server.util.McpToolUtils;

/**
 * Pins the {@code space_scope} declarations the documents add-on ships: which
 * document tools a call limited to one space may use.
 * <p>
 * Only {@code get_root_folder_by_space}, which names its space, is declared.
 * The tools reaching a document or a folder by its id stay undeclared: the id
 * is a JCR node id (hexadecimal), which a limited call can't hand to a
 * resolver (the space scope accepts decimal ids only), and a node reached by
 * id may be a shortcut or a share whose target lies outside the space.
 * {@code search_documents} follows the shortcuts of the space to their
 * targets, even given a space.
 */
public class DocumentToolDefinitionSpaceScopeTest {

  private static final String      ROOT_BY_SPACE = "get_root_folder_by_space";

  private static final String      NODE_ID       = "be0688cd7f00010134384ab7e1b15a48";

  /**
   * Tools that must stay refused under a space scope, and why: they search or
   * list across spaces and the user's own drive, reach a node by a JCR id,
   * move or share content outside the space, or fetch a url.
   */
  private static final Set<String> REFUSED       = Set.of("get_documents_by_folder_id",
                                                          "get_root_folder_for_user",
                                                          "get_document_by_id",
                                                          "get_document_content_by_id",
                                                          "attach_document_to_content",
                                                          "update_document_description",
                                                          "get_document_transcription_by_id",
                                                          "search_documents",
                                                          "list_folder_children",
                                                          "get_folder_breadcrumb",
                                                          "get_folder_tree",
                                                          "list_document_versions",
                                                          "get_documents_size",
                                                          "create_folder",
                                                          "create_document",
                                                          "create_document_from_template",
                                                          "upload_document",
                                                          "rename_document",
                                                          "move_document",
                                                          "copy_document",
                                                          "duplicate_document",
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
   * Exactly {@code get_root_folder_by_space} declares a space scope, as a
   * read naming its {@code space_id} and reading the call's space when it is
   * absent, with no other argument; every other shipped tool is listed as
   * refused, so a tool added or declared fails here.
   */
  @Test
  public void onlyTheRootFolderOfASpaceIsDeclared() {
    Map<String, SimpleToolDefinition> all = shippedTools();
    Map<String, SimpleToolDefinition> declared = all.entrySet()
                                                    .stream()
                                                    .filter(e -> e.getValue().getSpaceScope() != null)
                                                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    assertEquals(Set.of(ROOT_BY_SPACE), declared.keySet());
    SimpleToolDefinition tool = declared.get(ROOT_BY_SPACE);
    SpaceScopeDeclaration scope = tool.getSpaceScope();
    assertNotNull(tool.getAnnotations());
    assertTrue(McpToolSpaceScopeService.isReadTool(tool));
    assertTrue(scope.isWellFormedFor(false));
    assertTrue(scope.isSpaceKind());
    assertEquals("space_id", scope.getSpaceArg());
    assertTrue(scope.isUseScopeWhenAbsent());
    assertTrue(scope.getAllowedArgs() == null || scope.getAllowedArgs().isEmpty());
    assertTrue(scope.getFalseOnlyArgs() == null || scope.getFalseOnlyArgs().isEmpty());

    Set<String> others = all.keySet().stream().filter(name -> !ROOT_BY_SPACE.equals(name)).collect(Collectors.toSet());
    assertEquals(REFUSED, others);
  }

  /**
   * Through the real space scope service and the shipped definitions: the
   * root folder is read for the call's space only, and every other tool,
   * given a node id or a space, is refused.
   */
  @Test
  @SuppressWarnings("unchecked")
  public void aCallLimitedToASpaceReachesItsRootFolderOnly() {
    Map<String, SimpleToolDefinition> all = shippedTools();
    McpToolSpaceScopeService service = new McpToolSpaceScopeService(mock(ObjectProvider.class));
    ActingIdentity scoped = ActingIdentity.agent("agent-docs", "DOCS", Origin.MENTION, 12L);

    assertEquals("{\"space_id\":12}", service.enforce(ROOT_BY_SPACE, all.get(ROOT_BY_SPACE), "{\"space_id\":12}", scoped));
    assertEquals("{\"space_id\":12}", service.enforce(ROOT_BY_SPACE, all.get(ROOT_BY_SPACE), "{}", scoped));
    assertThrows(SpaceScopeRefusedException.class,
                 () -> service.enforce(ROOT_BY_SPACE, all.get(ROOT_BY_SPACE), "{\"space_id\":13}", scoped));
    assertThrows(SpaceScopeRefusedException.class,
                 () -> service.enforce(ROOT_BY_SPACE, all.get(ROOT_BY_SPACE), "{\"space_id\":12,\"extra\":1}", scoped));
    REFUSED.forEach(name -> {
      String input = "{\"document_id\":\"%s\",\"folder_id\":\"%s\",\"space_id\":12}".formatted(NODE_ID, NODE_ID);
      assertThrows(name, SpaceScopeRefusedException.class, () -> service.enforce(name, all.get(name), input, scoped));
    });
    assertFalse(all.isEmpty());
    assertEquals("{\"space_id\":13}",
                 service.enforce(ROOT_BY_SPACE, all.get(ROOT_BY_SPACE), "{\"space_id\":13}", ActingIdentity.person("john", Origin.CHAT)));
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
