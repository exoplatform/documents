<%@page import="org.exoplatform.services.security.ConversationState"%>
<%
  /**
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
%>
<%@ page import="org.exoplatform.portal.config.model.Page"%>
<%@ page import="org.exoplatform.portal.application.PortalRequestContext"%>
<%@ page import="org.exoplatform.portal.config.UserACL"%>
<%@ page import="org.exoplatform.container.ExoContainerContext"%>
<%@ page import="javax.portlet.PortletPreferences" %>
<%@ page import="org.exoplatform.commons.utils.CommonsUtils" %>
<%@ page import="io.meeds.social.translation.service.TranslationService" %>
<%@ page import="org.exoplatform.portal.localization.LocaleContextInfoUtils" %>
<%@ page import="org.apache.commons.text.StringEscapeUtils" %>


<%@ taglib uri="http://java.sun.com/portlet_2_0" prefix="portlet" %>
<portlet:defineObjects/>
<portlet:actionURL var="saveSettingsUrl" />
<%
  long applicationId;
  Object applicationIdParam = request.getAttribute("applicationId");
  applicationId = Long.parseLong((applicationIdParam instanceof String[]) ? ((String[]) applicationIdParam)[0]
          : (String) applicationIdParam);
  Page currentPage = PortalRequestContext.getCurrentInstance().getPage();
  boolean canEdit = ExoContainerContext.getService(UserACL.class).hasEditPermission(currentPage, ConversationState.getCurrent().getIdentity());

  PortletPreferences preferences = renderRequest.getPreferences();
  String viewOptions = preferences.getValue("viewOptions", "list");
  boolean customHeader = Boolean.parseBoolean(preferences.getValue("customHeader", "true"));
  boolean displaySeeMore = Boolean.parseBoolean(preferences.getValue("displaySeeMore", "true"));
  int maxDocumentsToList = Integer.parseInt(preferences.getValue("maxDocumentsToList", "4"));
  String headerTitle = CommonsUtils.getService(TranslationService.class).getTranslationLabelOrDefault("documentGadget",
          applicationId, "headerTitle", LocaleContextInfoUtils.getUserLocale(request.getRemoteUser()));
%>

<div class="VuetifyApp">
  <div data-app="true"
       class="v-application v-application--is-ltr theme--light"
       id="DocumentGadget">
    <script type="text/javascript">
      require(['PORTLET/documents-portlet/DocumentGadget'], app => app.init({
        applicationId: '<%=applicationId%>',
        viewOptions: '<%=viewOptions%>',
        customHeader: <%=customHeader%>,
        headerTitle: <%=headerTitle == null ? null : String.format("'%s'", StringEscapeUtils.escapeJava(headerTitle).replace("\\\"", "\"").replace("\\\\\"", "\\\""))%>,
        displaySeeMore: <%=displaySeeMore%>,
        maxDocumentsToList: '<%=maxDocumentsToList%>',
        canEdit: <%=canEdit%>,
        saveSettingsUrl: '<%=saveSettingsUrl%>'
      }));
    </script>
  </div>
</div>
