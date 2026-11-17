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
 *
*/
package org.exoplatform.documents.exception;

import io.meeds.social.space.security.SpaceSecurityAudit;
import io.meeds.social.space.security.exception.SpaceSecurityRefusedException;

/**
 * A change to a document's access the document's space refuses. Its message is
 * the code naming the rule; it carries the space and the document so that the
 * entry point which received the request logs the refusal with its channel.
 */
public class DocumentSharingRefusedException extends SpaceSecurityRefusedException {

  /** The space limits who manages its documents' access. */
  public static final String ACCESS_MANAGEMENT_LIMITED = "documents.space.accessManagementLimited";

  /** A change of the document's access list, a share included. */
  public static final String CHANGE_PERMISSIONS_ACTION = "changePermissions";

  /** The creation or the update of the document's public link. */
  public static final String CREATE_PUBLIC_LINK_ACTION = "createPublicLink";

  /** The revocation of the document's public link. */
  public static final String REVOKE_PUBLIC_LINK_ACTION = "revokePublicLink";

  /** Channel of the documents REST endpoints. */
  public static final String REST_CHANNEL              = "REST";

  /** Channel of the documents MCP tools. */
  public static final String MCP_CHANNEL               = "MCP";

  private static final long  serialVersionUID          = 3020473614216712735L;

  private final long         spaceId;

  private final String       documentId;

  private final String       action;

  public DocumentSharingRefusedException(String messageCode, long spaceId, String documentId, String action) {
    super(messageCode);
    this.spaceId = spaceId;
    this.documentId = documentId;
    this.action = action;
  }

  public long getSpaceId() {
    return spaceId;
  }

  public String getDocumentId() {
    return documentId;
  }

  public String getAction() {
    return action;
  }

  /**
   * Logs the refusal in the space security audit.
   *
   * @param actor the user whose request was refused
   * @param channel the channel the request came through
   */
  public void audit(String actor, String channel) {
    SpaceSecurityAudit.actionRefused(spaceId, documentId, action, actor, channel);
  }

}
