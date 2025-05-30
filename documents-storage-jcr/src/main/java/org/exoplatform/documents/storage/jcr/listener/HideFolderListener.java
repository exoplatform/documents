/*
 * Copyright (C) 2025 eXo Platform SAS.
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
package org.exoplatform.documents.storage.jcr.listener;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.query.Query;
import javax.jcr.query.QueryResult;

import org.exoplatform.documents.storage.jcr.util.NodeTypeConstants;
import org.exoplatform.services.listener.Event;
import org.exoplatform.services.listener.Listener;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

public class HideFolderListener extends Listener<Node, Boolean> {

  private static final Log LOG = ExoLogger.getLogger(HideFolderListener.class);

  @Override
  public void onEvent(Event<Node, Boolean> event) throws RepositoryException {
    Boolean hidden = event.getData();
    Node folder = event.getSource();
    String statementOfFolders = getFolderDocumentsQuery(folder.getPath());
    Query jcrQuery = folder.getSession().getWorkspace().getQueryManager().createQuery(statementOfFolders, Query.SQL);
    QueryResult queryResult = jcrQuery.execute();
    NodeIterator nodeIterator = queryResult.getNodes();
    while (nodeIterator.hasNext()) {
      Node node = nodeIterator.nextNode();
      try {
        boolean isAlreadyHidden = node.isNodeType(NodeTypeConstants.EXO_HIDDENABLE);
        if (hidden && !isAlreadyHidden) {
          node.addMixin(NodeTypeConstants.EXO_HIDDENABLE);
        } else if (hidden) {
          break;
        } else if (isAlreadyHidden) {
          node.removeMixin(NodeTypeConstants.EXO_HIDDENABLE);
        } else {
          break;
        }
      } catch (RepositoryException e) {
        LOG.error("Cannot hide/unhide documents under folder:" + folder.getPath(), e);
      }
    }
    folder.save();
  }

  private String getFolderDocumentsQuery(String folderPath) {
    return new StringBuilder().append("SELECT * FROM nt:base")
                              .append(" WHERE jcr:path LIKE '")
                              .append(folderPath)
                              .append("/%'")
                              .append(" AND NOT jcr:path LIKE '")
                              .append(folderPath)
                              .append("/%/%' ")
                              .toString();
  }

}
