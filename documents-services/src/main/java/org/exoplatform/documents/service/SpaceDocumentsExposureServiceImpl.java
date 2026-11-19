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
package org.exoplatform.documents.service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.documents.model.ExposedDocument;
import org.exoplatform.documents.model.ExposedDocumentPage;
import org.exoplatform.documents.model.PublicDocumentLink;
import org.exoplatform.documents.model.SpaceDocumentsExposure;
import org.exoplatform.documents.storage.PublicDocumentLinkStorage;
import org.exoplatform.documents.storage.SpaceDocumentsExposureStorage;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;

import io.meeds.social.space.security.SpaceSecurityAudit;
import io.meeds.social.space.security.service.SpaceSecurityService;

@Service
public class SpaceDocumentsExposureServiceImpl implements SpaceDocumentsExposureService {

  /** The count of public links the walk reads at a time */
  public static final int                         LINKS_PAGE_SIZE      = 100;

  public static final String                      NOT_ALLOWED_MESSAGE  = "documents.space.exposure.notAllowed";

  public static final String                      INVALID_PAGE_MESSAGE = "documents.space.exposure.invalidPage";

  private static final Comparator<ExposedDocument> NAME_THEN_ID         =
                                                                       Comparator.comparing(ExposedDocument::name,
                                                                                            Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                                                                                 .thenComparing(ExposedDocument::id);

  @Autowired
  private SpaceService                            spaceService;

  @Autowired
  private SpaceSecurityService                    spaceSecurityService;

  @Autowired
  private SpaceDocumentsExposureStorage           exposureStorage;

  @Autowired
  private PublicDocumentLinkStorage               publicDocumentLinkStorage;

  private int                                     linksPageSize        = LINKS_PAGE_SIZE;

  @Override
  public SpaceDocumentsExposure getExposure(long spaceId, String username) throws ObjectNotFoundException, IllegalAccessException {
    Space space = getManagedSpace(spaceId, username);
    return new SpaceDocumentsExposure(getPublicLinkDocuments(space).size(),
                                      exposureStorage.getSharedOutsideDocuments(space).size());
  }

  @Override
  public ExposedDocumentPage getPublicLinkDocuments(long spaceId,
                                                    int offset,
                                                    int limit,
                                                    String username) throws ObjectNotFoundException, IllegalAccessException {
    Space space = getManagedSpace(spaceId, username);
    if (offset < 0 || limit <= 0) {
      throw new IllegalArgumentException(INVALID_PAGE_MESSAGE);
    }
    List<ExposedDocument> documents = getPublicLinkDocuments(space);
    int from = Math.min(offset, documents.size());
    int to = (int) Math.min((long) from + limit, documents.size());
    return new ExposedDocumentPage(documents.subList(from, to), documents.size());
  }

  @Override
  public void removePublicLinks(Space space, boolean documentsKeptInside, String username) {
    long spaceId = Long.parseLong(space.getId());
    long lastId = 0;
    List<PublicDocumentLink> links;
    do {
      links = publicDocumentLinkStorage.getLinksAfter(lastId, linksPageSize);
      if (links.isEmpty()) {
        break;
      }
      lastId = links.get(links.size() - 1).id();
      Set<String> spaceDocumentIds = exposureStorage.getSpaceDocuments(space,
                                                                       links.stream().map(PublicDocumentLink::nodeId).toList())
                                                    .stream()
                                                    .map(ExposedDocument::id)
                                                    .collect(Collectors.toSet());
      for (PublicDocumentLink link : links) {
        if (spaceDocumentIds.contains(link.nodeId())) {
          removePublicLink(spaceId, space, link, documentsKeptInside, username);
        }
      }
    } while (links.size() == linksPageSize);
  }

  /**
   * @param linksPageSize the count of public links the walk reads at a time
   */
  void setLinksPageSize(int linksPageSize) {
    this.linksPageSize = linksPageSize;
  }

  /**
   * Reads the document again, takes its everyone access off on a turn-on,
   * then deletes its link: a document gone or moved out of the space keeps
   * its link and its everyone access, and a link another actor removed first
   * is not reported
   */
  private void removePublicLink(long spaceId,
                                Space space,
                                PublicDocumentLink link,
                                boolean documentsKeptInside,
                                String username) {
    ExposedDocument document = exposureStorage.prepareLinkRemoval(space, link.nodeId(), documentsKeptInside);
    if (document != null && publicDocumentLinkStorage.deleteLink(link.id())) {
      SpaceSecurityAudit.publicLinkRemoved(spaceId, link.nodeId(), document.owner(), username);
    }
  }

  /**
   * @return the space's documents carrying a public link, one per document,
   *         sorted by name then identifier
   */
  private List<ExposedDocument> getPublicLinkDocuments(Space space) {
    Map<String, ExposedDocument> documents = new LinkedHashMap<>();
    long lastId = 0;
    List<PublicDocumentLink> links;
    do {
      links = publicDocumentLinkStorage.getLinksAfter(lastId, linksPageSize);
      if (links.isEmpty()) {
        break;
      }
      lastId = links.get(links.size() - 1).id();
      exposureStorage.getSpaceDocuments(space, links.stream().map(PublicDocumentLink::nodeId).toList())
                     .forEach(document -> documents.putIfAbsent(document.id(), document));
    } while (links.size() == linksPageSize);
    return documents.values().stream().sorted(NAME_THEN_ID).toList();
  }

  /**
   * Decides on the committed rows whether the user manages the space: a space
   * admin, a platform administrator or an administrator of the space's
   * template
   */
  private Space getManagedSpace(long spaceId, String username) throws ObjectNotFoundException, IllegalAccessException {
    Space space = spaceService.getSpaceById(spaceId);
    if (space == null) {
      throw new ObjectNotFoundException("Space with id " + spaceId + " doesn't exist");
    }
    if (StringUtils.isBlank(username) || !spaceSecurityService.canManageSpace(space, username)) {
      throw new IllegalAccessException(NOT_ALLOWED_MESSAGE);
    }
    return space;
  }

}
