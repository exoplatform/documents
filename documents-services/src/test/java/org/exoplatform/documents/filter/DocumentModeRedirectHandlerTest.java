/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see<http://www.gnu.org/licenses/>.
 */
package org.exoplatform.documents.filter;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.documents.service.DocumentFileService;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;

/**
 * Tests the un-advised seam of {@link DocumentModeRedirectHandler#doFilter}: a
 * plain call to the {@code @ContainerTransactional} method would boot a Kernel
 * container.
 */
@ExtendWith(MockitoExtension.class)
class DocumentModeRedirectHandlerTest {

  private static final String               DOCUMENT_ID = "123";

  private static final String               EDITOR_URL  = "http://localhost:8080/portal/public/oeditor";

  @Mock
  private DocumentFileService               documentFileService;

  @Mock
  private IdentityManager                   identityManager;

  @Mock
  private HttpServletRequest                request;

  @Mock
  private HttpServletResponse               response;

  @Mock
  private FilterChain                       chain;

  private final Map<String, String>        parameters  = new HashMap<>();

  private MockedStatic<ExoContainerContext> containerContext;

  private DocumentModeRedirectHandler       handler     = new DocumentModeRedirectHandler();

  @BeforeEach
  void setUp() {
    containerContext = mockStatic(ExoContainerContext.class);
    containerContext.when(() -> ExoContainerContext.getService(DocumentFileService.class)).thenReturn(documentFileService);
    containerContext.when(() -> ExoContainerContext.getService(IdentityManager.class)).thenReturn(identityManager);
    lenient().when(request.getParameter(anyString())).thenAnswer(invocation -> parameters.get(invocation.getArgument(0)));
  }

  @AfterEach
  void tearDown() {
    containerContext.close();
  }

  @Test
  void testAnonymousVisitorInViewModePassesThroughOnce() throws Exception {
    when(request.getRemoteUser()).thenReturn(null);
    parameters.put("mode", "view");
    // Stubbed so that a wrong redirect is seen as a redirect, not as an NPE
    lenient().when(request.getRequestURL()).thenReturn(new StringBuffer(EDITOR_URL));

    handler.redirectToViewModeWhenNotEditable(request, response, chain);

    verify(chain, times(1)).doFilter(request, response);
    verify(response, never()).sendRedirect(any());
    verifyNoInteractions(documentFileService, identityManager);
  }

  /**
   * A request with no user and no mode is a public site visitor or a registered
   * user not logged in yet: its link goes on unchanged, so that login keeps it.
   */
  @Test
  void testAnonymousRequestWithoutModePassesThroughUnchanged() throws Exception {
    when(request.getRemoteUser()).thenReturn(null);
    parameters.put("docId", DOCUMENT_ID);
    // Stubbed so that a wrong redirect is seen as a redirect, not as an NPE
    lenient().when(request.getRequestURL()).thenReturn(new StringBuffer(EDITOR_URL));
    lenient().when(request.getQueryString()).thenReturn("docId=" + DOCUMENT_ID);

    handler.redirectToViewModeWhenNotEditable(request, response, chain);

    verify(chain, times(1)).doFilter(request, response);
    verify(response, never()).sendRedirect(any());
    verifyNoInteractions(documentFileService, identityManager);
  }

  @Test
  void testUserWithoutEditRightIsRedirectedToViewMode() throws Exception {
    mockUser(false);
    when(request.getRequestURL()).thenReturn(new StringBuffer(EDITOR_URL));
    when(request.getQueryString()).thenReturn("docId=" + DOCUMENT_ID);

    handler.redirectToViewModeWhenNotEditable(request, response, chain);

    verify(response).sendRedirect(EDITOR_URL + "?docId=" + DOCUMENT_ID + "&mode=view");
    verify(chain, never()).doFilter(any(), any());
  }

  @Test
  void testUserWithoutEditRightInViewModePassesThroughOnce() throws Exception {
    mockUser(false);
    parameters.put("mode", "view");
    // Stubbed so that a wrong redirect is seen as a redirect, not as an NPE
    lenient().when(request.getRequestURL()).thenReturn(new StringBuffer(EDITOR_URL));

    handler.redirectToViewModeWhenNotEditable(request, response, chain);

    verify(chain, times(1)).doFilter(request, response);
    verify(response, never()).sendRedirect(any());
  }

  @Test
  void testUserWithEditRightPassesThroughOnce() throws Exception {
    mockUser(true);
    // Stubbed so that a wrong redirect is seen as a redirect, not as an NPE
    lenient().when(request.getRequestURL()).thenReturn(new StringBuffer(EDITOR_URL));

    handler.redirectToViewModeWhenNotEditable(request, response, chain);

    verify(chain, times(1)).doFilter(request, response);
    verify(response, never()).sendRedirect(any());
  }

  private void mockUser(boolean canEdit) throws Exception {
    when(request.getRemoteUser()).thenReturn("user");
    parameters.put("docId", DOCUMENT_ID);
    Identity identity = mock(Identity.class);
    when(identity.getId()).thenReturn("1");
    when(identityManager.getOrCreateUserIdentity("user")).thenReturn(identity);
    when(documentFileService.hasEditPermissionOnDocument(DOCUMENT_ID, 1L)).thenReturn(canEdit);
  }
}
