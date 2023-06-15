package org.exoplatform.documents.filter;

import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.services.listener.ListenerService;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

import java.io.IOException;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class DocumentPreviewFilterTest {

  @Mock
  private ListenerService                         listenerService;

  private static final MockedStatic<CommonsUtils> COMMONS_UTILS = mockStatic(CommonsUtils.class);

  @Before
  public void setUp() throws Exception {
    COMMONS_UTILS.when(() -> CommonsUtils.getService(ListenerService.class)).thenReturn(listenerService);
  }

  @AfterClass
  public static void afterRunBare() {
    COMMONS_UTILS.close();
  }

  @Test
  public void doFilter() throws Exception {
    DocumentPreviewFilter documentPreviewFilter = new DocumentPreviewFilter();
    ServletResponse servletResponse = mock(ServletResponse.class);
    FilterChain filterChain = mock(FilterChain.class);
    HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
    when(httpServletRequest.getParameter("documentPreviewId")).thenReturn("123", null, null);
    when(httpServletRequest.getParameter("docId")).thenReturn(null, "123", null);
    when(httpServletRequest.getRemoteUser()).thenReturn("user");
    documentPreviewFilter.doFilter(httpServletRequest, servletResponse, filterChain);
    verify(listenerService, times(1)).broadcast("update-document-views-detail", "user", "123");
    clearInvocations(listenerService);
    documentPreviewFilter.doFilter(httpServletRequest, servletResponse, filterChain);
    verify(listenerService, times(1)).broadcast("update-document-views-detail", "user", "123");
    clearInvocations(listenerService);
    documentPreviewFilter.doFilter(httpServletRequest, servletResponse, filterChain);
    verify(listenerService, times(0)).broadcast("update-document-views-detail", "user", "123");
    verify(filterChain, times(3)).doFilter(any(), any());

  }
}
