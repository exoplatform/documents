/**
 * Copyright (C) 2025 eXo Platform SAS
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
package org.exoplatform.documents.storage.jcr.webdav.cache;

import static org.exoplatform.documents.webdav.model.constant.PropertyConstants.CHECKEDIN;
import static org.exoplatform.documents.webdav.model.constant.PropertyConstants.GETLASTMODIFIED;
import static org.exoplatform.documents.webdav.model.constant.PropertyConstants.CHILDCOUNT;
import static org.exoplatform.documents.webdav.model.constant.PropertyConstants.HREF;
import static org.exoplatform.documents.webdav.model.constant.PropertyConstants.LOCKDISCOVERY;
import static org.exoplatform.documents.webdav.model.constant.PropertyConstants.SUPPORTEDLOCK;
import static org.exoplatform.documents.webdav.model.constant.PropertyConstants.PREDECESSORSET;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.jcr.NamespaceRegistry;
import javax.jcr.Session;
import javax.jcr.Workspace;
import javax.jcr.observation.ObservationManager;
import javax.xml.namespace.QName;

import org.apache.commons.collections4.CollectionUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import org.exoplatform.documents.storage.jcr.util.ACLProperties;
import org.exoplatform.documents.storage.jcr.webdav.JcrWebDavService;
import org.exoplatform.documents.storage.jcr.webdav.cache.elasticsearch.dao.WebDavItemDao;
import org.exoplatform.documents.storage.jcr.webdav.cache.elasticsearch.entity.WebDavItemEntity;
import org.exoplatform.documents.storage.jcr.webdav.cache.elasticsearch.entity.WebDavItemPropertyEntity;
import org.exoplatform.documents.storage.jcr.webdav.cache.elasticsearch.entity.WebDavItemUserPropertiesEntity;
import org.exoplatform.documents.storage.jcr.webdav.cache.listener.WebDavCacheUpdaterAction;
import org.exoplatform.documents.storage.jcr.webdav.plugin.WebdavReadCommandHandler;
import org.exoplatform.documents.storage.jcr.webdav.plugin.WebdavWriteCommandHandler;
import org.exoplatform.documents.webdav.model.WebDavItem;
import org.exoplatform.documents.webdav.model.WebDavItemProperty;
import org.exoplatform.portal.config.UserACL;
import org.exoplatform.services.jcr.config.ContainerEntry;
import org.exoplatform.services.jcr.config.RepositoryEntry;
import org.exoplatform.services.jcr.config.WorkspaceEntry;
import org.exoplatform.services.jcr.core.ManageableRepository;
import org.exoplatform.services.jcr.impl.RepositoryContainer;
import org.exoplatform.services.jcr.impl.RepositoryServiceImpl;
import org.exoplatform.services.jcr.impl.WorkspaceContainer;

import lombok.SneakyThrows;

@RunWith(MockitoJUnitRunner.Silent.class)
public class CachedJcrWebDavServiceTest {

  private static final String       FILE_PATH       = "/test";                         // NOSONAR

  /**
   * A Space drive segment as emitted by the WebDAV handler: the Space pretty
   * name, then the identity id between percent-encoded parentheses.
   */
  private static final String       DRIVE_PATH      = "/one27_two27_three_1%20%2828%29";

  private static final String       DRIVES_BASE_URI = "https://exo.test/webdav/drives";

  private static final String       DRIVE_BASE_URI  = "https://exo.test/webdav/drives/d";

  private static final String       USERNAME        = "user";

  private static final String       WS_NAME         = "test";

  private static final String       REPOSITORY_NAME = JcrWebDavService.REPOSITORY_NAME;

  @Mock
  private WebdavReadCommandHandler  readCommandHandler;

  @Mock
  private WebdavWriteCommandHandler writeCommandHandler;

  @Mock
  private RepositoryServiceImpl     repositoryService;

  @Mock
  private UserACL                   userAcl;

  @Mock
  private WebDavItemDao             webDavItemRepository;

  @Mock
  private ManageableRepository      repository;

  @Mock
  private RepositoryEntry           repositoryEntry;

  @Mock
  private WorkspaceEntry            workspaceEntry;

  @Mock
  private Session                   session;

  @Mock
  private Workspace                 workspace;

  @Mock
  private NamespaceRegistry         nsRegistry;

  @Mock
  private ObservationManager        observationManager;

  @Mock
  private RepositoryContainer       repositoryContainer;

  @Mock
  private WorkspaceContainer        workspaceContainer;

  @Mock
  private ContainerEntry            containerEntry;

  @InjectMocks
  private CachedJcrWebDavService    service;

  @Before
  @SneakyThrows
  public void setup() {
    when(repositoryService.getDefaultRepository()).thenReturn(repository);
    when(repository.getConfiguration()).thenReturn(repositoryEntry);
    when(repositoryEntry.getDefaultWorkspaceName()).thenReturn(WS_NAME);
    when(repository.getSystemSession(WS_NAME)).thenReturn(session);
    when(session.getWorkspace()).thenReturn(workspace);
    when(workspace.getObservationManager()).thenReturn(observationManager);
    when(repositoryService.getRepositoryContainer(REPOSITORY_NAME)).thenReturn(repositoryContainer);
    when(repositoryContainer.getWorkspaceContainer(WS_NAME)).thenReturn(workspaceContainer);
    when(repository.getSystemSession(anyString())).thenReturn(session);
    when(repository.getNamespaceRegistry()).thenReturn(nsRegistry);
    when(nsRegistry.getPrefixes()).thenReturn(new String[] { "pfx" });
    when(nsRegistry.getURI("pfx")).thenReturn("uri");
    when(workspaceContainer.getComponentInstanceOfType(WorkspaceEntry.class, false)).thenReturn(workspaceEntry);
    when(workspaceEntry.getContainer()).thenReturn(containerEntry);
    service = Mockito.spy(service);
    doReturn(session).when(service).newSession(anyString(), any(), any());
    when(webDavItemRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0, WebDavItemEntity.class));
  }

  @Test
  @SneakyThrows
  public void testInit() {
    service.init();
    verify(webDavItemRepository).deleteAll();
    for (String path : WebDavCacheUpdaterAction.SUPPORTED_PATHS) {
      verify(observationManager).addEventListener(any(WebDavCacheUpdaterAction.class),
                                                  eq(WebDavCacheUpdaterAction.SUPPORTED_EVENT_TYPES),
                                                  eq(path),
                                                  eq(true),
                                                  eq(null),
                                                  eq(WebDavCacheUpdaterAction.SUPPORTED_NODE_TYPES.toArray(String[]::new)),
                                                  eq(false));
    }
  }

  @Test
  @SneakyThrows
  public void testIsFileWhenPathIsBlankShouldReturnFalse() {
    assertFalse(service.isFile(null, USERNAME));
    assertFalse(service.isFile("", USERNAME));
    assertFalse(service.isFile("/", USERNAME));
  }

  @Test
  @SneakyThrows
  public void testIsFileWhenEntityExistsShouldReturnEntityValue() {
    WebDavItemEntity entity = mock(WebDavItemEntity.class);
    when(entity.isFile()).thenReturn(true);
    when(entity.getUsernames()).thenReturn(Set.of(USERNAME));
    when(webDavItemRepository.findById(FILE_PATH)).thenReturn(Optional.of(entity));

    boolean result = service.isFile(FILE_PATH, USERNAME);

    assertTrue(result);
    verify(webDavItemRepository).findById(FILE_PATH);
  }

  @Test
  @SneakyThrows
  public void testGetLastModifiedDateWhenEntityHasProperty() {
    WebDavItemPropertyEntity property = new WebDavItemPropertyEntity();
    property.setName(GETLASTMODIFIED.getNamespaceURI() + ":" + GETLASTMODIFIED.getLocalPart());
    property.setValue("Thu, 01 Jan 2025 00:00:00 GMT");

    WebDavItemEntity entity = new WebDavItemEntity();
    entity.setProperties(Collections.singletonList(property));
    entity.setUserProperties(List.of(new WebDavItemUserPropertiesEntity(USERNAME, List.of())));

    when(webDavItemRepository.findById(FILE_PATH)).thenReturn(Optional.of(entity));

    long result = service.getLastModifiedDate(FILE_PATH, null, USERNAME);

    assertTrue(result > 0);
  }

  /**
   * EXO-90128 — neither isFile nor getLastModifiedDate took a username, so a row
   * one member had populated answered for anybody. getLastModifiedDate is the
   * sharper of the two: GetWebDavHandler calls checkModified before any
   * authoritative read, so a 304 confirmed both the resource's existence and its
   * exact mtime to a user with no right to it.
   */
  @Test
  @SneakyThrows
  public void testIsFileMustNotAnswerFromARowTheCallerIsAbsentFrom() {
    WebDavItemEntity entity = mock(WebDavItemEntity.class);
    when(entity.getUsernames()).thenReturn(Set.of(USERNAME));
    when(webDavItemRepository.findById(FILE_PATH)).thenReturn(Optional.of(entity));
    assertFalse(service.isFile(FILE_PATH, OTHER_USERNAME));
    // answered from the repository under the caller's own session, not the row
    verify(entity, never()).isFile();
    verify(readCommandHandler).isFile(any(), eq(FILE_PATH));
  }

  @Test
  @SneakyThrows
  public void testGetLastModifiedDateMustNotAnswerFromARowTheCallerIsAbsentFrom() {
    WebDavItemPropertyEntity property = new WebDavItemPropertyEntity();
    property.setName(GETLASTMODIFIED.getNamespaceURI() + ":" + GETLASTMODIFIED.getLocalPart());
    property.setValue("Thu, 01 Jan 2025 00:00:00 GMT");
    WebDavItemEntity entity = new WebDavItemEntity();
    entity.setProperties(Collections.singletonList(property));
    entity.setUserProperties(List.of(new WebDavItemUserPropertiesEntity(USERNAME, List.of())));
    when(webDavItemRepository.findById(FILE_PATH)).thenReturn(Optional.of(entity));
    assertEquals(0l, service.getLastModifiedDate(FILE_PATH, null, OTHER_USERNAME));
    verify(readCommandHandler).getLastModifiedDate(any(), eq(FILE_PATH), isNull());
  }

  /**
   * The row carries the head's date only, so a versioned request is never
   * answered from it.
   */
  @Test
  @SneakyThrows
  public void testGetLastModifiedDateOfAVersionBypassesTheCache() {
    WebDavItemPropertyEntity property = new WebDavItemPropertyEntity();
    property.setName(GETLASTMODIFIED.getNamespaceURI() + ":" + GETLASTMODIFIED.getLocalPart());
    property.setValue("Thu, 01 Jan 2025 00:00:00 GMT");
    WebDavItemEntity entity = new WebDavItemEntity();
    entity.setProperties(Collections.singletonList(property));
    entity.setUserProperties(List.of(new WebDavItemUserPropertiesEntity(USERNAME, List.of())));
    when(webDavItemRepository.findById(FILE_PATH)).thenReturn(Optional.of(entity));
    assertEquals(0l, service.getLastModifiedDate(FILE_PATH, "1", USERNAME));
    verify(readCommandHandler).getLastModifiedDate(any(), eq(FILE_PATH), eq("1"));
  }

  @Test
  @SneakyThrows
  public void testGetLastModifiedDateWhenPathIsRootShouldReturnZero() {
    long result = service.getLastModifiedDate("/", "1", USERNAME);
    assertEquals(0L, result);
  }

  @Test
  @SneakyThrows
  public void testClearCacheWhenEntityExistsAndDropTrueShouldDelete() {
    WebDavItemEntity entity = new WebDavItemEntity();
    entity.setModified(false);

    when(webDavItemRepository.findAllByJcrPath(FILE_PATH)).thenReturn(List.of(entity));

    service.clearCache(FILE_PATH, true);

    verify(webDavItemRepository).deleteAll(List.of(entity));
  }

  @Test
  @SneakyThrows
  public void testClearCacheWhenEntityExistsAndDropFalseShouldMarkModified() {
    WebDavItemEntity entity = new WebDavItemEntity();
    entity.setModified(false);
    when(webDavItemRepository.findAllByJcrPath(FILE_PATH)).thenReturn(List.of(entity));

    service.clearCache(FILE_PATH, false);

    assertTrue(entity.isModified());
    verify(webDavItemRepository).save(entity);
  }

  @Test
  @SneakyThrows
  public void testGetWhenCacheIsEmptyShouldCallSuper() {
    when(webDavItemRepository.findById(FILE_PATH)).thenReturn(Optional.empty());

    WebDavItem expectedItem = new WebDavItem();
    expectedItem.setWebDavPath(FILE_PATH);
    when(readCommandHandler.get(any(),
                                any(),
                                any(),
                                anyBoolean(),
                                anyInt(),
                                any(),
                                any())).thenReturn(expectedItem);

    WebDavItem result = service.get(FILE_PATH, "all", Collections.<QName> emptySet(), false, 5, "", "user");

    assertNotNull(result);
    assertEquals(FILE_PATH, result.getWebDavPath());
  }

  /**
   * EXO-89613 — a cache row is keyed by the drive-relative WebDAV path alone,
   * so the same row answers the drive-list mount and the single-drive mount,
   * whose base URIs differ. The href it returns must follow the base URI of the
   * request being answered, never the one that happened to populate the row: a
   * client that receives an href it did not ask for discards the whole
   * multistatus response and the mount fails.
   */
  @Test
  @SneakyThrows
  public void testGetFromCacheShouldBuildIdentifierFromRequestBaseUriInBothMountModes() {
    WebDavItemEntity entity = cachedDriveEntry();
    when(webDavItemRepository.findById(DRIVE_PATH)).thenReturn(Optional.of(entity));

    WebDavItem fromDrivesList = service.get(DRIVE_PATH, "allprop", null, false, 0, DRIVES_BASE_URI, USERNAME);
    WebDavItem fromSingleDrive = service.get(DRIVE_PATH, "allprop", null, false, 0, DRIVE_BASE_URI, USERNAME);

    assertEquals(new URI(DRIVES_BASE_URI + DRIVE_PATH), fromDrivesList.getIdentifier());
    assertEquals(new URI(DRIVE_BASE_URI + DRIVE_PATH), fromSingleDrive.getIdentifier());
    // both answers came from the one cached row, not from a JCR read
    verify(readCommandHandler, never()).get(any(), any(), any(), anyBoolean(), anyInt(), any(), any());
  }

  /**
   * DAV:checked-in carries the same absolute href as the response's own
   * D:href — it is rebuilt from the same base URI.
   */
  @Test
  @SneakyThrows
  public void testGetFromCacheShouldRebuildCheckedInHrefFromRequestBaseUri() {
    WebDavItemEntity entity = cachedDriveEntry();
    when(webDavItemRepository.findById(DRIVE_PATH)).thenReturn(Optional.of(entity));

    WebDavItem result = service.get(DRIVE_PATH, "allprop", null, false, 0, DRIVE_BASE_URI, USERNAME);

    WebDavItemProperty checkedIn = result.getProperty(CHECKEDIN);
    assertNotNull(checkedIn);
    assertEquals(DRIVE_BASE_URI + DRIVE_PATH, checkedIn.getChild(HREF).getValue());
  }

  /**
   * The children a Depth:1 PROPFIND returns are served from their own cache
   * rows and get the same treatment.
   */
  @Test
  @SneakyThrows
  public void testGetChildrenFromCacheShouldBuildIdentifierFromRequestBaseUri() {
    String childPath = DRIVE_PATH + "/folder%20one";
    WebDavItemEntity entity = cachedDriveEntry();
    entity.setDeep(true);
    WebDavItemEntity childEntity = new WebDavItemEntity();
    childEntity.setWebDavPath(childPath);
    childEntity.setUserProperties(List.of(new WebDavItemUserPropertiesEntity(USERNAME, List.of())));

    when(webDavItemRepository.findById(DRIVE_PATH)).thenReturn(Optional.of(entity));
    when(webDavItemRepository.findByParentWebDavPath(DRIVE_PATH)).thenReturn(List.of(childEntity));

    WebDavItem result = service.get(DRIVE_PATH, "allprop", null, false, 1, DRIVE_BASE_URI, USERNAME);

    assertEquals(1, result.getChildren().size());
    assertEquals(new URI(DRIVE_BASE_URI + childPath), result.getChildren().get(0).getIdentifier());
    verify(readCommandHandler, never()).get(any(), any(), any(), anyBoolean(), anyInt(), any(), any());
  }

  /**
   * A property href that is not the bare item URI — the version sets append
   * <code>/?version=&lt;name&gt;</code> to it. Re-basing must move the base and
   * keep the suffix. Nothing caches a versioned read today, which is exactly why
   * this is pinned: the next property derived from the item URI must not
   * silently reintroduce EXO-89613.
   */
  @Test
  @SneakyThrows
  public void testGetFromCacheShouldRebaseHrefAndKeepWhatIsAppendedToIt() {
    WebDavItemProperty predecessors = new WebDavItemProperty(PREDECESSORSET);
    predecessors.addChild(new WebDavItemProperty(HREF)).setValue(DRIVES_BASE_URI + DRIVE_PATH + "/?version=v1");

    WebDavItemEntity entity = cachedDriveEntry();
    entity.setProperties(List.of(new WebDavItemPropertyEntity(predecessors)));
    when(webDavItemRepository.findById(DRIVE_PATH)).thenReturn(Optional.of(entity));

    WebDavItem result = service.get(DRIVE_PATH, "allprop", null, false, 0, DRIVE_BASE_URI, USERNAME);

    assertEquals(DRIVE_BASE_URI + DRIVE_PATH + "/?version=v1",
                 result.getProperty(PREDECESSORSET).getChild(HREF).getValue());
  }


  private static final String       OTHER_USERNAME  = "other";

  /**
   * Pins the read half of the per-user contract: each user is overlaid their own
   * entry, and only theirs. It does not by itself reproduce the EXO-90128 leak —
   * the row here is already in the post-fix shape, and only
   * {@link #testRefreshByOneUserMustNotLeakTheirLockTokenToAnother} reproduces
   * the defect end to end.
   */
  @Test
  @SneakyThrows
  public void testGetFromCacheShouldServeEachUserTheirOwnAcl() {
    WebDavItemEntity entity = cachedDriveEntry();
    entity.setUserProperties(List.of(new WebDavItemUserPropertiesEntity(USERNAME,
                                                                        List.of(property(ACLProperties.ACL, "read-only"))),
                                     new WebDavItemUserPropertiesEntity(OTHER_USERNAME,
                                                                        List.of(property(ACLProperties.ACL, "manager")))));
    when(webDavItemRepository.findById(DRIVE_PATH)).thenReturn(Optional.of(entity));

    WebDavItem asUser = service.get(DRIVE_PATH, "allprop", null, false, 0, DRIVE_BASE_URI, USERNAME);
    WebDavItem asOther = service.get(DRIVE_PATH, "allprop", null, false, 0, DRIVE_BASE_URI, OTHER_USERNAME);

    assertEquals("read-only", value(asUser, ACLProperties.ACL));
    assertEquals("manager", value(asOther, ACLProperties.ACL));
    // exactly one: a write half that left the property in the shared list too
    // would shadow-duplicate it, and getProperty returns only the first match
    assertEquals(1, count(asUser, ACLProperties.ACL));
    // both answers came from the one cached row, not from a JCR read
    verify(readCommandHandler, never()).get(any(), any(), any(), anyBoolean(), anyInt(), any(), any());
  }

  /**
   * Same contract for DAV:lockdiscovery, the property with teeth: JCR returns a
   * lock token only to the session holding the lock, and every write verb
   * accepts a client-supplied token back. A user who holds no lock must be
   * overlaid no token. The end-to-end reproduction is
   * {@link #testRefreshByOneUserMustNotLeakTheirLockTokenToAnother}.
   */
  @Test
  @SneakyThrows
  public void testGetFromCacheShouldNotServeAnotherUsersLockToken() {
    WebDavItemEntity entity = cachedDriveEntry();
    entity.setUserProperties(List.of(new WebDavItemUserPropertiesEntity(USERNAME,
                                                                        List.of(property(LOCKDISCOVERY,
                                                                                         "opaquelocktoken:abc"))),
                                     new WebDavItemUserPropertiesEntity(OTHER_USERNAME, List.of())));
    when(webDavItemRepository.findById(DRIVE_PATH)).thenReturn(Optional.of(entity));

    WebDavItem asHolder = service.get(DRIVE_PATH, "allprop", null, false, 0, DRIVE_BASE_URI, USERNAME);
    WebDavItem asOther = service.get(DRIVE_PATH, "allprop", null, false, 0, DRIVE_BASE_URI, OTHER_USERNAME);

    assertEquals("opaquelocktoken:abc", value(asHolder, LOCKDISCOVERY));
    assertNull(asOther.getProperty(LOCKDISCOVERY));
  }

  /**
   * The write half of the same rule: what is computed from one user's session
   * must land under that user, not in the row's shared list where the next
   * reader would be served it.
   */
  @Test
  @SneakyThrows
  public void testSaveShouldKeepUserDependentPropertiesOutOfTheSharedList() {
    when(webDavItemRepository.findById(DRIVE_PATH)).thenReturn(Optional.empty());
    WebDavItem computed = new WebDavItem();
    computed.setWebDavPath(DRIVE_PATH);
    computed.setProperties(List.of(new WebDavItemProperty(GETLASTMODIFIED, "Thu, 01 Jan 2026 00:00:00 GMT"),
                                   new WebDavItemProperty(ACLProperties.ACL, "read-only"),
                                   new WebDavItemProperty(SUPPORTEDLOCK, "write"),
                                   new WebDavItemProperty(LOCKDISCOVERY, "opaquelocktoken:abc"),
                                   // node.getNodes() filters per child against the
                                   // reading session, so the count is the reader's
                                   new WebDavItemProperty(CHILDCOUNT, "10")));
    when(readCommandHandler.get(any(), any(), any(), anyBoolean(), anyInt(), any(), any())).thenReturn(computed);

    service.get(DRIVE_PATH, "allprop", null, false, 0, DRIVE_BASE_URI, USERNAME);

    ArgumentCaptor<WebDavItemEntity> captor = ArgumentCaptor.forClass(WebDavItemEntity.class);
    verify(webDavItemRepository).save(captor.capture());
    WebDavItemEntity saved = captor.getValue();
    assertEquals(List.of(qname(GETLASTMODIFIED)), saved.getProperties().stream().map(WebDavItemPropertyEntity::getName).toList());
    assertEquals(Set.of(USERNAME), saved.getUsernames());
    assertEquals(List.of(qname(ACLProperties.ACL), qname(SUPPORTEDLOCK), qname(LOCKDISCOVERY), qname(CHILDCOUNT)),
                 saved.getUserProperties(USERNAME).stream().map(WebDavItemPropertyEntity::getName).toList());
  }

  /**
   * The defect itself, end to end, against a stateful cache rather than a
   * single stubbed row: a user reads the item, a second user's read refreshes
   * the row, and the first user reads again. Before this fix the refresh
   * overwrote the row's whole property list with the second user's, so the
   * first user's next read was served the second user's lock token — and every
   * write verb accepts a client-supplied token back.
   */
  @Test
  @SneakyThrows
  public void testRefreshByOneUserMustNotLeakTheirLockTokenToAnother() {
    useInMemoryCache();
    when(readCommandHandler.get(any(), any(), any(), anyBoolean(), anyInt(), any(), eq(OTHER_USERNAME)))
                                                                                                        .thenAnswer(invocation -> computedItem(null));
    when(readCommandHandler.get(any(), any(), any(), anyBoolean(), anyInt(), any(), eq(USERNAME)))
                                                                                                 .thenAnswer(invocation -> computedItem("opaquelocktoken:abc"));

    service.get(DRIVE_PATH, "allprop", null, false, 0, DRIVE_BASE_URI, OTHER_USERNAME);
    service.get(DRIVE_PATH, "allprop", null, false, 0, DRIVE_BASE_URI, USERNAME);
    WebDavItem asOther = service.get(DRIVE_PATH, "allprop", null, false, 0, DRIVE_BASE_URI, OTHER_USERNAME);

    assertNull("the lock token of another user must not survive in the shared row",
               asOther.getProperty(LOCKDISCOVERY));
    assertNotNull("the item is still served from cache", asOther.getProperty(GETLASTMODIFIED));
  }

  /**
   * A row populated by one user must not answer for a user it holds nothing for.
   * WebdavReadCommandHandler#getWebDavIdentityItem returns null when
   * Session#itemExists is false, and itemExists swallows the
   * AccessDeniedException of a user who may not read the drive — so without the
   * guard in get(), a non-member of a space received a member's cached metadata
   * as a 207 where the uncached path answers 404.
   */
  @Test
  @SneakyThrows
  public void testStaleRowMustNotAnswerForAUserItHoldsNothingFor() {
    useInMemoryCache();
    when(readCommandHandler.get(any(), any(), any(), anyBoolean(), anyInt(), any(), eq(USERNAME)))
                                                                                                 .thenAnswer(invocation -> computedItem(null));
    when(readCommandHandler.get(any(), any(), any(), anyBoolean(), anyInt(), any(), eq(OTHER_USERNAME))).thenReturn(null);

    assertNotNull(service.get(DRIVE_PATH, "allprop", null, false, 0, DRIVE_BASE_URI, USERNAME));

    assertNull("a row populated by another user must not be served to one it holds nothing for",
               service.get(DRIVE_PATH, "allprop", null, false, 0, DRIVE_BASE_URI, OTHER_USERNAME));
  }

  /**
   * The other half of that guard, which must stay narrow: when the row *does*
   * hold an entry for this user, a null from the authoritative read is the
   * pre-existing deleted-node or transient-failure case and the row still
   * answers. Returning null here too would be a behaviour change visible to
   * every WebDAV client on a mount path.
   */
  @Test
  @SneakyThrows
  public void testStaleRowStillAnswersForAUserItHoldsPropertiesFor() {
    Map<String, WebDavItemEntity> store = useInMemoryCache();
    when(readCommandHandler.get(any(), any(), any(), anyBoolean(), anyInt(), any(), eq(USERNAME)))
                                                                                                 .thenAnswer(invocation -> computedItem(null));
    service.get(DRIVE_PATH, "allprop", null, false, 0, DRIVE_BASE_URI, USERNAME);

    // a JCR change marks the row modified, and the re-read yields nothing
    store.get(DRIVE_PATH).setModified(true);
    when(readCommandHandler.get(any(), any(), any(), anyBoolean(), anyInt(), any(), eq(USERNAME))).thenReturn(null);

    assertNotNull("the row holds this user's own properties, so it still answers",
                  service.get(DRIVE_PATH, "allprop", null, false, 0, DRIVE_BASE_URI, USERNAME));
  }

  /**
   * The per-user entries are bounded: each costs of the order of 900 bytes of
   * _source and the whole row is fetched on every PROPFIND. Eviction is
   * fail-safe — the evicted user simply refreshes on their next read — so the
   * cap is a size decision, not a correctness one. Oldest goes first, and the
   * user who just refreshed must survive.
   */
  @Test
  @SneakyThrows
  public void testUserPropertiesAreBoundedAndEvictTheLeastRecentlyRefreshed() {
    List<WebDavItemUserPropertiesEntity> existing = new ArrayList<>();
    for (int i = 0; i < 50; i++) {
      existing.add(new WebDavItemUserPropertiesEntity("user" + i, List.of()));
    }
    WebDavItemEntity entity = cachedDriveEntry();
    entity.setUserProperties(existing);
    when(webDavItemRepository.findById(DRIVE_PATH)).thenReturn(Optional.of(entity));
    when(readCommandHandler.get(any(), any(), any(), anyBoolean(), anyInt(), any(), eq(OTHER_USERNAME)))
                                                                                                       .thenAnswer(invocation -> computedItem(null));

    service.get(DRIVE_PATH, "allprop", null, false, 0, DRIVE_BASE_URI, OTHER_USERNAME);

    ArgumentCaptor<WebDavItemEntity> captor = ArgumentCaptor.forClass(WebDavItemEntity.class);
    verify(webDavItemRepository).save(captor.capture());
    Set<String> kept = captor.getValue().getUsernames();
    assertEquals(50, kept.size());
    assertTrue("the user who just refreshed must survive", kept.contains(OTHER_USERNAME));
    assertFalse("the oldest entry is the one evicted", kept.contains("user0"));
  }

  private WebDavItem computedItem(String lockToken) {
    WebDavItem item = new WebDavItem();
    item.setWebDavPath(DRIVE_PATH);
    List<WebDavItemProperty> properties = new ArrayList<>();
    properties.add(new WebDavItemProperty(GETLASTMODIFIED, "Thu, 01 Jan 2026 00:00:00 GMT"));
    if (lockToken != null) {
      properties.add(new WebDavItemProperty(LOCKDISCOVERY, lockToken));
    }
    item.setProperties(properties);
    return item;
  }

  private Map<String, WebDavItemEntity> useInMemoryCache() {
    Map<String, WebDavItemEntity> store = new HashMap<>();
    when(webDavItemRepository.save(any())).thenAnswer(invocation -> {
      WebDavItemEntity entity = invocation.getArgument(0, WebDavItemEntity.class);
      store.put(entity.getWebDavPath(), entity);
      return entity;
    });
    when(webDavItemRepository.findById(anyString())).thenAnswer(invocation -> Optional.ofNullable(store.get(invocation.getArgument(0,
                                                                                                                                  String.class))));
    return store;
  }

  private String value(WebDavItem webDavItem, javax.xml.namespace.QName name) {
    WebDavItemProperty property = webDavItem.getProperty(name);
    return property == null ? null : property.getValue();
  }

  private long count(WebDavItem webDavItem, javax.xml.namespace.QName name) {
    return CollectionUtils.emptyIfNull(webDavItem.getProperties(false))
                          .stream()
                          .filter(p -> name.equals(p.getName()))
                          .count();
  }

  private WebDavItemPropertyEntity property(javax.xml.namespace.QName name, String value) {
    return new WebDavItemPropertyEntity(new WebDavItemProperty(name, value));
  }

  private String qname(javax.xml.namespace.QName name) {
    return String.format("%s:%s", name.getNamespaceURI(), name.getLocalPart());
  }

  private WebDavItemEntity cachedDriveEntry() {
    WebDavItemProperty checkedIn = new WebDavItemProperty(CHECKEDIN);
    checkedIn.addChild(new WebDavItemProperty(HREF)).setValue(DRIVES_BASE_URI + DRIVE_PATH);

    WebDavItemEntity entity = new WebDavItemEntity();
    entity.setWebDavPath(DRIVE_PATH);
    entity.setJcrPath("/Groups/spaces/one27_two27_three_1/Documents");
    entity.setUserProperties(List.of(new WebDavItemUserPropertiesEntity(USERNAME, List.of())));
    entity.setProperties(List.of(new WebDavItemPropertyEntity(checkedIn)));
    return entity;
  }

}
