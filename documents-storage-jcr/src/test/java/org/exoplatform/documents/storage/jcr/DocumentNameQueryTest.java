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
package org.exoplatform.documents.storage.jcr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import javax.jcr.RepositoryException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.exoplatform.services.jcr.impl.core.LocationFactory;
import org.exoplatform.services.jcr.impl.core.NamespaceRegistryImpl;
import org.exoplatform.services.jcr.impl.core.query.DefaultQueryNodeFactory;
import org.exoplatform.services.jcr.impl.core.query.PropertyFunctionQueryNode;
import org.exoplatform.services.jcr.impl.core.query.QueryRootNode;
import org.exoplatform.services.jcr.impl.core.query.RelationQueryNode;
import org.exoplatform.services.jcr.impl.core.query.TraversingQueryNodeVisitor;
import org.exoplatform.services.jcr.impl.core.query.sql.JCRSQLQueryBuilder;

/**
 * The statement of the name lookup (EXO-95137), parsed by the repository's own
 * JCR-SQL grammar: the name reaches it as one string literal compared in lower
 * case, whatever it holds. Index usage and the access filtering of a real
 * repository are not covered here.
 */
class DocumentNameQueryTest {

  private static final List<String> DRIVES = List.of("/Users/j___/jo___/joh___/john/Private/%",
                                                     "/Groups/spaces/%/Documents/%");

  /**
   * A name built to close the literal and widen the query stays one literal:
   * the parsed statement compares the title and the name to it, and to nothing
   * else. Mutant: the quotes no longer doubled, which parses to another
   * comparison (or fails to parse).
   *
   * @throws Exception never
   */
  @Test
  @DisplayName("A name that tries to close the literal and add a condition stays one compared value")
  void aNameClosingTheLiteralStaysOneValue() throws Exception {
    String name = "x' OR jcr:path LIKE '/%";

    Parsed parsed = parse(JCRDocumentFileStorage.getNameQueryStatement(DRIVES, name));

    assertEquals(List.of("x' or jcr:path like '/%", "x' or jcr:path like '/%"), parsed.nameValues());
    assertEquals(List.of(PropertyFunctionQueryNode.LOWER_CASE, PropertyFunctionQueryNode.LOWER_CASE), parsed.functions);
  }

  /**
   * A name is compared exactly, in lower case: {@code %}, {@code _} and
   * {@code /} are characters of the name, never wildcards or path steps.
   * Mutant: the comparison turned into a LIKE.
   *
   * @throws Exception never
   */
  @Test
  @DisplayName("Wildcard and path characters of a name are compared as themselves, in lower case")
  void aNameIsComparedExactlyInLowerCase() throws Exception {
    Parsed parsed = parse(JCRDocumentFileStorage.getNameQueryStatement(DRIVES, "Draft_50%/V2"));

    assertEquals(List.of("draft_50%/v2", "draft_50%/v2"), parsed.nameValues());
    assertTrue(parsed.relations.stream()
                               .filter(relation -> "draft_50%/v2".equals(relation.getStringValue()))
                               .allMatch(relation -> relation.getOperation() != 23),
               "a name is never compared with LIKE (operation 23)");
  }

  /**
   * The statement searches the given drives only, files, folders and
   * shortcuts only, and leaves hidden nodes out; it parses with the drives'
   * real path shapes (a user bucket's underscores, a space's wildcard step).
   * Mutants: the hidden filter dropped; the type filter dropped.
   *
   * @throws Exception never
   */
  @Test
  @DisplayName("The statement keeps to the drives, to files, folders and shortcuts, and leaves hidden nodes out")
  void theStatementKeepsToTheDrivesAndLeavesHiddenNodesOut() throws Exception {
    String statement = JCRDocumentFileStorage.getNameQueryStatement(DRIVES, "workflow");

    parse(statement);
    assertTrue(statement.contains("jcr:path LIKE '/Users/j___/jo___/joh___/john/Private/%' OR jcr:path LIKE '/Groups/spaces/%/Documents/%'"),
               statement);
    assertTrue(statement.contains("jcr:primaryType = 'nt:file'") && statement.contains("jcr:primaryType = 'nt:folder'")
        && statement.contains("jcr:primaryType = 'exo:symlink'"), statement);
    assertTrue(statement.endsWith(" AND NOT jcr:mixinTypes LIKE 'exo:hiddenable'"), statement);
  }

  /**
   * A text written as a JCR SQL literal has each of its quotes doubled.
   * Mutant: the quotes left as they are.
   */
  @Test
  @DisplayName("A quote inside a literal is doubled")
  void aQuoteIsDoubled() {
    assertEquals("'o''brien''s plan'", JCRDocumentFileStorage.toSqlLiteral("o'brien's plan"));
  }

  /**
   * Parses a statement with the repository's grammar and collects its
   * comparisons and functions.
   *
   * @param statement the statement
   * @return what it compares
   * @throws RepositoryException when the grammar refuses it
   */
  private static Parsed parse(String statement) throws RepositoryException {
    QueryRootNode root = JCRSQLQueryBuilder.createQuery(statement,
                                                        new LocationFactory(new NamespaceRegistryImpl()),
                                                        new DefaultQueryNodeFactory());
    Parsed parsed = new Parsed();
    root.accept(parsed, null);
    return parsed;
  }

  /**
   * The comparisons and the property functions of a parsed statement.
   */
  private static class Parsed extends TraversingQueryNodeVisitor {

    private final List<RelationQueryNode> relations = new ArrayList<>();

    private final List<String>            functions = new ArrayList<>();

    /**
     * {@inheritDoc}
     */
    @Override
    public Object visit(RelationQueryNode node, Object data) throws RepositoryException {
      relations.add(node);
      // the default visitor stops at a comparison: its property function is an operand of it
      node.acceptOperands(this, data);
      return data;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object visit(PropertyFunctionQueryNode node, Object data) {
      functions.add(node.getFunctionName());
      return data;
    }

    /**
     * @return the values compared under a property function, the name's
     *         comparisons
     */
    private List<String> nameValues() {
      return relations.stream()
                      .filter(relation -> relation.getOperands().length > 0)
                      .map(RelationQueryNode::getStringValue)
                      .toList();
    }
  }
}
