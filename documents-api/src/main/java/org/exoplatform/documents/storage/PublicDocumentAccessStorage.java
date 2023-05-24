/*
 * Copyright (C) 2023 eXo Platform SAS
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
package org.exoplatform.documents.storage;

import org.exoplatform.documents.model.PublicDocumentAccess;

public interface PublicDocumentAccessStorage {

    /**
     * Gets a public access by its document id (nodeId)
     * @param nodeId node id
     * @return {@link PublicDocumentAccess}
     */
    PublicDocumentAccess getPublicDocumentAccessByNodeId(String nodeId);

    /**
     * Removes an existing document public access
     *
     * @param nodeId document id
     */
    void removePublicDocumentAccess(String nodeId);

    /**
     * Save a created document public access
     *
     * @param publicDocumentAccess document public access object
     * @param userId current user id
     * @return {@link PublicDocumentAccess}
     */
    PublicDocumentAccess savePublicDocumentAccess(PublicDocumentAccess publicDocumentAccess, long userId);

}
