/*
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
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Test;

import io.meeds.mcp.server.model.SimpleToolDefinition;
import io.meeds.mcp.server.util.McpToolUtils;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Pins that the documents add-on's tool methods and its shipped
 * {@code ai-tool-definitions.json} match: mcp-server turns a public method
 * into a tool only when a definition carries its snake_case name, and binds
 * each argument by the camelCase of its {@code input_schema} property to the
 * Java parameter of that name, passing null when none has it. Neither is
 * checked by mcp-server at build time or at boot.
 */
public class DocumentToolDefinitionsTest {

  /**
   * Every public method of the module's tool classes has a definition, every definition has a
   * method, and every {@code input_schema} property of a definition names a
   * parameter of its method, once turned to camelCase.
   * Mutants: a definition dropped or renamed, a method renamed, a property
   * (upload_id above all) whose parameter is missing or named otherwise.
   */
  @Test
  public void everyToolMethodAndDefinitionMatch() {
    Map<String, Method> methods = Stream.of(DocumentMcpTool.class, TranscriptExcerptMcpTool.class)
                                        .flatMap(toolClass -> Arrays.stream(toolClass.getDeclaredMethods()))
                                        .filter(method -> Modifier.isPublic(method.getModifiers()))
                                        .filter(method -> !Modifier.isStatic(method.getModifiers()))
                                        .filter(method -> !method.isSynthetic())
                                        .collect(Collectors.toMap(method -> toSnakeCase(method.getName()), Function.identity()));
    Map<String, SimpleToolDefinition> definitions = shippedTools();

    assertEquals(methods.keySet(), definitions.keySet());
    definitions.forEach((name, definition) -> {
      Set<String> parameters = Arrays.stream(methods.get(name).getParameters())
                                     .map(Parameter::getName)
                                     .collect(Collectors.toSet());
      JsonNode schema = JsonMapper.builder().build().readTree(definition.getInputSchema());
      Set<String> properties = new HashSet<>();
      schema.path("properties").propertyNames().forEach(property -> properties.add(toCamelCase(property)));
      assertTrue(name + ": " + properties + " not all in " + parameters, parameters.containsAll(properties));
    });
    assertTrue(definitions.get("upload_document").getInputSchema().contains("\"upload_id\""));
    assertTrue(definitions.get("add_document_version").getInputSchema().contains("\"upload_id\""));
  }

  /**
   * get_document_access is a read (EXO-95034): read-only, idempotent, never
   * asking for an approval, scoped to a space as get_document_permissions
   * is, and its description says it is refused unless the user may manage
   * the document's access. Mutants: readOnlyHint false; require_approval
   * set; the space scope dropped; the refusal left out of the description.
   *
   * @throws Exception never
   */
  @Test
  public void getDocumentAccessIsARefusableReadScopedAsThePermissionsRead() throws Exception {
    JsonMapper mapper = JsonMapper.builder().build();
    Map<String, JsonNode> tools = new TreeMap<>();
    for (JsonNode tool : mapper.readTree(resource("ai-tool-definitions.json")).get("tools")) {
      tools.put(tool.get("name").asString(), tool);
    }
    JsonNode access = tools.get("get_document_access");
    assertNotNull(access);
    assertTrue(access.get("annotations").get("readOnlyHint").asBoolean());
    assertFalse(access.get("annotations").get("destructiveHint").asBoolean());
    assertTrue(access.get("annotations").get("idempotentHint").asBoolean());
    assertFalse("A read never asks for an approval", access.has("require_approval"));
    assertEquals(tools.get("get_document_permissions").get("space_scope"), access.get("space_scope"));
    List<String> properties = new java.util.ArrayList<>();
    access.get("input_schema").get("properties").propertyNames().forEach(properties::add);
    assertEquals(List.of("document_id"), properties);
    String description = access.get("description").asString();
    assertTrue(description, description.contains("Refused, with the reason and no list, unless you may manage the document's access"));
    assertTrue(description, description.contains("by name"));
  }

  /**
   * search_documents says that with neither a query nor updated_since it lists
   * the scope's files, the most recent first, and its query is optional with
   * no condition (EXO-94927). A booted instance takes that text through
   * mcp-server's upgrade digests: the module's upgrades file lists every text
   * search_documents shipped before, from platform-private-distributions,
   * where the tool was first defined, then from this module, and never the
   * current one.
   * Mutants: the sentence removed, the query still said to need a date, the
   * head's former digest removed, an earlier digest removed, the current
   * digest listed.
   *
   * @throws Exception never
   */
  @Test
  public void searchDocumentsListsTheScopesRecentFilesAndReplacesItsShippedTexts() throws Exception {
    JsonMapper mapper = JsonMapper.builder().build();
    JsonNode search = null;
    for (JsonNode tool : mapper.readTree(resource("ai-tool-definitions.json")).get("tools")) {
      if ("search_documents".equals(tool.get("name").asString())) {
        search = tool;
      }
    }
    assertNotNull(search);
    String description = search.get("description").asString();
    assertTrue(description,
               description.contains("With neither a query nor updated_since, lists the scope's files, the most recent first."));
    String query = search.get("input_schema").get("properties").get("query").get("description").asString();
    assertEquals("Full-text search query string. Optional: without it, the files of the scope are listed, the most recent first.",
                 query);

    Set<String> replaced = new HashSet<>();
    for (JsonNode tool : mapper.readTree(resource("ai-tool-definition-upgrades.json")).get("tools")) {
      if ("search_documents".equals(tool.get("name").asString())) {
        tool.get("replaced_definitions").forEach(digest -> replaced.add(digest.asString()));
      }
    }
    // the digests (jq -jcS '{description, input_schema}' | shasum -a 256) of the texts shipped before EXO-94927:
    // platform-private-distributions 802222172, be1c40ded, df8b620d9, then documents 1f2c6b805, 013bdd00b, 20a7fdc8e
    List.of("aca16da632490d85676cbb47daeb88715e3a0758ad03867a569cf1d0fcee6780",
            "ba02168dd192a9c68dac9dacec8afce8c65db5c8db6c2f235abd0704918c1d6b",
            "730825e85b8df00014dc003f120adba652889f62289b65a8c11d7dcda04c90b2",
            "d3e347ce117f582a0b63507483bbf334ebe715ef520c28bdb90dfda20f034110",
            "6439b74c3301e63f39ddc5048f4398b82bd66f11ea9c1b40d560254b6046e2ea",
            "3355a37e4de74828f7eaff2175d0341678c39488228055daf31dbb93c45a024c")
        .forEach(digest -> assertTrue(digest + " not in " + replaced, replaced.contains(digest)));
    Map<String, Object> current = new TreeMap<>();
    current.put("description", description);
    current.put("input_schema", sorted(mapper.treeToValue(search.get("input_schema"), Object.class)));
    String digest = HexFormat.of()
                             .formatHex(MessageDigest.getInstance("SHA-256")
                                                     .digest(mapper.writeValueAsString(current).getBytes(StandardCharsets.UTF_8)));
    assertFalse("the current text replaces nothing: " + digest, replaced.contains(digest));
  }

  /**
   * Gives a JSON value its keys in sorted order at every level, as
   * {@code jq -S} prints them.
   *
   * @param value a value read from JSON
   * @return the same value, every object a sorted map
   */
  private static Object sorted(Object value) {
    if (value instanceof Map<?, ?> map) {
      Map<String, Object> sortedMap = new TreeMap<>();
      map.forEach((key, child) -> sortedMap.put(String.valueOf(key), sorted(child)));
      return sortedMap;
    } else if (value instanceof List<?> list) {
      return list.stream().map(DocumentToolDefinitionsTest::sorted).toList();
    }
    return value;
  }

  /**
   * Opens one of the module's shipped resources.
   *
   * @param name the resource's name
   * @return its content
   * @throws Exception when it can't be read
   */
  private static java.io.InputStream resource(String name) throws Exception {
    URL classes = DocumentMcpTool.class.getProtectionDomain().getCodeSource().getLocation();
    return new java.io.FileInputStream(new File(new File(classes.toURI()), name));
  }

  /**
   * Turns a Java method name into the tool name mcp-server looks up.
   *
   * @param name the camelCase name
   * @return the snake_case name
   */
  private static String toSnakeCase(String name) {
    return name.replaceAll("([A-Z])", "_$1").toLowerCase(java.util.Locale.ROOT);
  }

  /**
   * Turns a snake_case argument name into the Java parameter name it binds
   * to.
   *
   * @param name the snake_case name
   * @return the camelCase name
   */
  private static String toCamelCase(String name) {
    StringBuilder camelCase = new StringBuilder();
    boolean upper = false;
    for (char c : name.toCharArray()) {
      if (c == '_') {
        upper = true;
      } else {
        camelCase.append(upper ? Character.toUpperCase(c) : c);
        upper = false;
      }
    }
    return camelCase.toString();
  }

  /**
   * Reads the definitions the module ships.
   *
   * @return every shipped document tool definition, by name
   */
  private static Map<String, SimpleToolDefinition> shippedTools() {
    try {
      URL classes = DocumentMcpTool.class.getProtectionDomain().getCodeSource().getLocation();
      URL url = new File(new File(classes.toURI()), "ai-tool-definitions.json").toURI().toURL();
      List<SimpleToolDefinition> tools = McpToolUtils.parseToolDefinitions(url);
      assertNotNull(tools);
      return tools.stream().collect(Collectors.toMap(SimpleToolDefinition::getName, tool -> tool));
    } catch (Exception e) {
      throw new IllegalStateException("The document tool definitions couldn't be read", e);
    }
  }

}
