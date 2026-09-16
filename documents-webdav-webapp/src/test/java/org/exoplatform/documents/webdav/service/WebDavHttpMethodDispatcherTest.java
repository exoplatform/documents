/**
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
package org.exoplatform.documents.webdav.service;

import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Field;
import java.util.List;

import javax.jcr.AccessDeniedException;
import javax.jcr.ItemExistsException;
import javax.jcr.PathNotFoundException;
import javax.jcr.lock.LockException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.exoplatform.documents.webdav.model.WebDavException;
import org.exoplatform.documents.webdav.plugin.WebDavHttpMethodPlugin;
import org.exoplatform.documents.webdav.plugin.impl.WebDavErrorHandler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * EXO-90128 — the repository refuses an operation the user has no right to with
 * a JCR AccessDeniedException, which carries no WebDavException in its chain. It
 * used to fall through to the generic handler, so renaming a file one may not
 * write answered 500 and filed a WARN, instead of answering 403 quietly.
 */
@ExtendWith(MockitoExtension.class)
public class WebDavHttpMethodDispatcherTest {

  @Mock
  private WebDavHttpMethodPlugin      moveHandler;

  @Mock
  private WebDavErrorHandler          errorHandler;

  @Mock
  private HttpServletRequest          request;

  @Mock
  private HttpServletResponse         response;

  private WebDavHttpMethodDispatcher  dispatcher;

  @BeforeEach
  @SuppressWarnings("unchecked")
  public void setUp() throws Exception {
    dispatcher = new WebDavHttpMethodDispatcher();
    setField("handlers", List.of(moveHandler));
    setField("errorHandler", errorHandler);
    lenient().when(moveHandler.getMethod()).thenReturn("MOVE");
    dispatcher.init();
    lenient().when(request.getMethod()).thenReturn("MOVE");
    lenient().when(request.getRequestURI()).thenReturn("/webdav/drives/d/space%20%2825%29/sample.docx");
  }

  @Test
  public void testAccessDeniedAnswersForbidden() throws Exception {
    whenHandlerThrows(new AccessDeniedException("access denied"));

    dispatcher.handle(request, response);

    verify(response).sendError(403, "access denied");
  }

  /**
   * The refusal reaches the dispatcher wrapped as often as bare, so the chain is
   * walked rather than only its head inspected.
   */
  @Test
  public void testWrappedAccessDeniedAnswersForbidden() throws Exception {
    whenHandlerThrows(new RuntimeException("wrapped", new AccessDeniedException("access denied")));

    dispatcher.handle(request, response);

    verify(response).sendError(403, "access denied");
  }

  @Test
  public void testPathNotFoundAnswersNotFound() throws Exception {
    whenHandlerThrows(new PathNotFoundException("no such node"));

    dispatcher.handle(request, response);

    verify(response).sendError(404, "no such node");
  }

  @Test
  public void testLockExceptionAnswersLocked() throws Exception {
    whenHandlerThrows(new LockException("locked by someone else"));

    dispatcher.handle(request, response);

    verify(response).sendError(423, "locked by someone else");
  }

  @Test
  public void testItemExistsAnswersConflict() throws Exception {
    whenHandlerThrows(new ItemExistsException("already there"));

    dispatcher.handle(request, response);

    verify(response).sendError(409, "already there");
  }

  /**
   * A failure the contract does not cover really is a 500 — the mapping must not
   * swallow genuine faults into a tidy status.
   */
  @Test
  public void testUnmappedFailureStillAnswersServerError() throws Exception {
    whenHandlerThrows(new IllegalStateException("boom"));

    dispatcher.handle(request, response);

    verify(response).sendError(500, "boom");
  }

  @Test
  public void testWebDavExceptionKeepsItsOwnStatus() throws Exception {
    whenHandlerThrows(new WebDavException(409, "conflict"));

    dispatcher.handle(request, response);

    verify(response).sendError(409, "conflict");
  }

  private void whenHandlerThrows(Throwable throwable) throws WebDavException {
    doAnswer(invocation -> {
      sneakyThrow(throwable);
      return null;
    }).when(moveHandler).handle(request, response);
  }

  /**
   * The verb handlers reach the dispatcher through Lombok's {@code @SneakyThrows},
   * so a checked repository exception really does escape a signature that does
   * not declare it. The test raises it the same way.
   */
  @SuppressWarnings("unchecked")
  private static <E extends Throwable> void sneakyThrow(Throwable throwable) throws E {
    throw (E) throwable;
  }

  private void setField(String name, Object value) throws Exception {
    Field field = WebDavHttpMethodDispatcher.class.getDeclaredField(name);
    field.setAccessible(true); // NOSONAR
    field.set(dispatcher, value); // NOSONAR
  }

}
