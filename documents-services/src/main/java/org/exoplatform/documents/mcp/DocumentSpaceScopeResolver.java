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

import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import org.exoplatform.documents.service.DocumentFileService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;

import io.meeds.mcp.server.plugin.SpaceScopeResolver;

/**
 * Tells which space a document or a folder belongs to, for the space scope of
 * the document tools: the space whose {@code Documents} drive holds the node
 * itself, by its node id (32 lowercase hexadecimal digits). A shortcut or a
 * share link, which stands for a node that may lie anywhere, a node of a
 * personal drive, of the trash or of any other place, a space that no longer
 * exists, a missing node and an id that isn't a node id belong to no space.
 * Read as the system, for the space scope decision only.
 */
@Service("documentSpaceScopeResolver")
@Profile("mcp-server")
public class DocumentSpaceScopeResolver implements SpaceScopeResolver {

  /** The name the document tools' {@code space_scope.resolver} gives. */
  public static final String         NAME    = "document";

  /** Logger. */
  private static final Log           LOG     = ExoLogger.getLogger(DocumentSpaceScopeResolver.class);

  /** A node id, as the space scope hands it over. */
  private static final Pattern       NODE_ID = Pattern.compile("[0-9a-f]{32}");

  /** Reads the space drive that holds a node. */
  private final DocumentFileService  documentFileService;

  /** Reads the space of a group. */
  private final SpaceService         spaceService;

  /**
   * @param documentFileService reads the space drive that holds a node
   * @param spaceService        reads the space of a group
   */
  public DocumentSpaceScopeResolver(DocumentFileService documentFileService, SpaceService spaceService) {
    this.documentFileService = documentFileService;
    this.spaceService = spaceService;
  }

  /**
   * @return {@value #NAME}
   */
  @Override
  public String getName() {
    return NAME;
  }

  /**
   * @param objectId the node id
   * @return the id of the space whose {@code Documents} drive holds the node
   *         itself, or null for a shortcut or a share link, a node outside
   *         the drive of a space, a missing node, a missing space or one with
   *         no id, an id that isn't a node id, or a lookup that fails
   */
  @Override
  public Long spaceOf(String objectId) {
    if (objectId == null || !NODE_ID.matcher(objectId).matches()) {
      return null;
    }
    try {
      String groupId = documentFileService.getDocumentSpaceGroupId(objectId);
      Space space = StringUtils.isBlank(groupId) ? null : spaceService.getSpaceByGroupId(groupId);
      Long spaceId = space != null && StringUtils.isNumeric(space.getId()) ? Long.valueOf(space.getId()) : null;
      return spaceId != null && spaceId > 0 ? spaceId : null;
    } catch (RuntimeException e) {
      LOG.debug("The space of document '{}' couldn't be read", objectId, e);
      return null;
    }
  }

}
