/*
 * Copyright (C) 2026 eXo Platform SAS
 *
 * This program is free software: you can redistribute it and/or modify
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
package org.exoplatform.documents.storage.jcr.model;

import javax.jcr.Node;

import org.exoplatform.documents.constant.DocumentNodeType;

/**
 * Where a chain of shortcuts ends, as {@code DocumentNodeDescriber} follows it.
 *
 * @param id the id of the node ending the chain, null when the chain is broken
 * @param node the node ending the chain, null when the chain is broken
 * @param type that node's type, null when the chain is broken
 */
public record ChainEnd(String id, Node node, DocumentNodeType type) {
}
