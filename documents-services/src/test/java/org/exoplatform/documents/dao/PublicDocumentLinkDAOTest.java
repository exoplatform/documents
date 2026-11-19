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
package org.exoplatform.documents.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.jpa.HibernatePersistenceConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;

import org.exoplatform.documents.entity.PublicDocumentAccessEntity;
import org.exoplatform.documents.model.PublicDocumentLink;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

/**
 * Runs the statements of {@link PublicDocumentLinkDAO} through the Spring Data
 * proxy on HSQLDB. Index usage and the MySQL and PostgreSQL dialects are not
 * covered here.
 */
class PublicDocumentLinkDAOTest {

  private EntityManagerFactory  entityManagerFactory;

  private EntityManager         entityManager;

  private PublicDocumentLinkDAO publicDocumentLinkDAO;

  private List<Long>            linkIds;

  @BeforeEach
  void setup() {
    entityManagerFactory = new HibernatePersistenceConfiguration("documents-public-link-dao-test")
                                                                                                   .managedClass(PublicDocumentAccessEntity.class)
                                                                                                   .property("jakarta.persistence.jdbc.url",
                                                                                                             "jdbc:hsqldb:mem:documents-public-link-dao;shutdown=true")
                                                                                                   .property("jakarta.persistence.jdbc.user",
                                                                                                             "SA")
                                                                                                   .property("jakarta.persistence.jdbc.password",
                                                                                                             "")
                                                                                                   .property("hibernate.hbm2ddl.auto",
                                                                                                             "create-drop")
                                                                                                   .createEntityManagerFactory();
    entityManager = entityManagerFactory.createEntityManager();
    publicDocumentLinkDAO = new JpaRepositoryFactory(entityManager).getRepository(PublicDocumentLinkDAO.class);
    entityManager.getTransaction().begin();
    linkIds = new ArrayList<>();
    for (String nodeId : List.of("node-a", "node-b", "node-c", "node-d", "node-e")) {
      PublicDocumentAccessEntity link = new PublicDocumentAccessEntity();
      link.setNodeId(nodeId);
      entityManager.persist(link);
      linkIds.add(link.getId());
    }
    entityManager.flush();
    entityManager.clear();
  }

  @AfterEach
  void teardown() {
    entityManager.getTransaction().rollback();
    entityManager.close();
    entityManagerFactory.close();
  }

  @Test
  void findLinksAfterReadsTheLinksAboveTheKeyInAscendingOrder() {
    assertEquals(List.of(link(0), link(1)), publicDocumentLinkDAO.findLinksAfter(0, Limit.of(2)));
    assertEquals(List.of(link(2), link(3)), publicDocumentLinkDAO.findLinksAfter(linkIds.get(1), Limit.of(2)));
    assertEquals(List.of(link(4)), publicDocumentLinkDAO.findLinksAfter(linkIds.get(3), Limit.of(2)));
    assertTrue(publicDocumentLinkDAO.findLinksAfter(linkIds.get(4), Limit.of(2)).isEmpty());
  }

  @Test
  void findLinksAfterExcludesTheKeyItself() {
    assertEquals(link(2), publicDocumentLinkDAO.findLinksAfter(linkIds.get(1), Limit.of(1)).get(0));
  }

  @Test
  void aLinkDeletedBetweenTwoPagesShiftsNoLaterLinkOutOfTheWalk() {
    List<PublicDocumentLink> firstPage = publicDocumentLinkDAO.findLinksAfter(0, Limit.of(2));
    assertEquals(1, publicDocumentLinkDAO.deleteLink(linkIds.get(0)));
    assertEquals(1, publicDocumentLinkDAO.deleteLink(linkIds.get(1)));

    List<PublicDocumentLink> secondPage = publicDocumentLinkDAO.findLinksAfter(firstPage.get(1).id(), Limit.of(2));

    assertEquals(List.of(link(2), link(3)), secondPage);
  }

  @Test
  void deleteLinkDeletesOnlyThatLinkAndCountsZeroOnceItIsGone() {
    assertEquals(1, publicDocumentLinkDAO.deleteLink(linkIds.get(2)));
    assertEquals(0, publicDocumentLinkDAO.deleteLink(linkIds.get(2)));

    assertEquals(List.of(link(0), link(1), link(3), link(4)), publicDocumentLinkDAO.findLinksAfter(0, Limit.of(10)));
  }

  @Test
  void existsByIdTellsWhetherTheLinkIsStillThere() {
    assertTrue(publicDocumentLinkDAO.existsById(linkIds.get(1)));
    assertEquals(1, publicDocumentLinkDAO.deleteLink(linkIds.get(1)));
    assertFalse(publicDocumentLinkDAO.existsById(linkIds.get(1)));
    assertTrue(publicDocumentLinkDAO.existsById(linkIds.get(2)));
  }

  private PublicDocumentLink link(int index) {
    return new PublicDocumentLink(linkIds.get(index), "node-" + (char) ('a' + index));
  }

}
