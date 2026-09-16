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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.List;


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
  private DocumentWebDavService       documentWebDavService;

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
    setField("documentWebDavService", documentWebDavService);
    lenient().when(moveHandler.getMethod()).thenReturn("MOVE");
    dispatcher.init();
    lenient().when(request.getMethod()).thenReturn("MOVE");
    lenient().when(request.getRequestURI()).thenReturn("/webdav/drives/d/space%20%2825%29/sample.docx");
  }






  /**
   * A failure the contract does not cover really is a 500 — the mapping must not
   * swallow genuine faults into a tidy status.
   */
  /**
   * With no WebDavException in the chain, the storage implementation is asked
   * what its own failure means — this layer knows nothing of JCR.
   */
  @Test
  public void testStorageTranslationIsUsedWhenThereIsNoWebDavException() throws Exception {
    IllegalStateException failure = new IllegalStateException("denied");
    when(documentWebDavService.toWebDavException(failure)).thenReturn(new WebDavException(403, "denied"));
    whenHandlerThrows(failure);

    dispatcher.handle(request, response);

    verify(response).sendError(403, "denied");
  }

  /**
   * A status the code chose deliberately outranks whatever the storage would
   * infer from the failure carrying it, however far down it sits.
   */
  @Test
  public void testWebDavExceptionOutranksTheStorageTranslation() throws Exception {
    whenHandlerThrows(new RuntimeException("outer", new IllegalStateException("carrier", new WebDavException(404, "gone"))));
    lenient().when(documentWebDavService.toWebDavException(any())).thenReturn(new WebDavException(403, "denied"));

    dispatcher.handle(request, response);

    verify(response).sendError(404, "gone");
    verify(documentWebDavService, never()).toWebDavException(any());
  }

  @Test
  public void testUnmappedFailureStillAnswersServerError() throws Exception {
    whenHandlerThrows(new IllegalStateException("boom"));

    dispatcher.handle(request, response);

    verify(response).sendError(500, "boom");
  }

  /**
   * A WebDavException that arrives <b>wrapped</b> is the only way the chain walk's
   * WebDavException arm is ever reached: one thrown bare is caught a frame above,
   * by the dispatcher's own catch clause. Without this the arm was dead in the
   * suite while a pin appeared to cover it.
   */
  @Test
  public void testWrappedWebDavExceptionKeepsItsOwnStatus() throws Exception {
    whenHandlerThrows(new RuntimeException("wrapped", new WebDavException(404, "gone")));

    dispatcher.handle(request, response);

    verify(response).sendError(404, "gone");
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
