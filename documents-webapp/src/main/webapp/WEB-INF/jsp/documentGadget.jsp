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
<%@ page import="javax.portlet.PortletPreferences" %>
<%@ page import="org.exoplatform.portal.localization.LocaleContextInfoUtils" %>
<%@ page import="org.apache.commons.text.StringEscapeUtils" %>

<%@ taglib uri="http://java.sun.com/portlet_2_0" prefix="portlet" %>
<portlet:defineObjects/>
<portlet:actionURL var="saveSettingsUrl" />
<%
  String settingName = (String) request.getAttribute("settingName");
  String headerTitle = (String) request.getAttribute("headerTitle");
  boolean canEdit = (boolean) request.getAttribute("canEdit");
  String id = "DocumentGadget-" + renderRequest.getWindowID();

  PortletPreferences preferences = renderRequest.getPreferences();
  String viewOptions = preferences.getValue("viewOptions", "list");
  String documentType = preferences.getValue("documentType", "recentDocument");
  String spaceIdentityId = preferences.getValue("spaceIdentityId", "");
  boolean customHeader = Boolean.parseBoolean(preferences.getValue("customHeader", "false"));
  boolean displaySeeMore = Boolean.parseBoolean(preferences.getValue("displaySeeMore", "true"));
  int maxDocumentsToList = Integer.parseInt(preferences.getValue("maxDocumentsToList", "4"));
  String categoryIds = preferences.getValue("categoryIds", "[]").replace("\"", "`");
  String excludeCategoryIds = preferences.getValue("excludeCategoryIds", "[]").replace("\"", "`");
  String selectedFoldersId = preferences.getValue("selectedFoldersId", "");
  boolean displayAccessDrive = Boolean.parseBoolean(preferences.getValue("displayAccessDrive", "false"));
  String driveUrl = preferences.getValue("driveUrl", "");
  boolean opensInSameTab = Boolean.parseBoolean(preferences.getValue("opensInSameTab", "true"));

%>

<div class="VuetifyApp">
  <div data-app="true"
    class="v-application transparent v-application--is-ltr theme--light"
    id="<%=id%>">
    <script type="text/javascript">
      require(['PORTLET/documents-portlet/DocumentGadget'], app => app.init({
        id: '<%= id %>',
        settingName: '<%= settingName %>',
        canEdit: <%=canEdit%>,
        viewOptions: '<%=viewOptions%>',
        documentType: '<%=documentType%>',
        spaceIdentityId: '<%=spaceIdentityId%>',
        customHeader: <%=customHeader%>,
        headerTitle: <%=headerTitle == null ? null : String.format("'%s'", StringEscapeUtils.escapeJava(headerTitle).replace("\\\"", "\"").replace("\\\\\"", "\\\""))%>,
        displaySeeMore: <%=displaySeeMore%>,
        maxDocumentsToList: <%=maxDocumentsToList%>,
        categoryIds: <%=categoryIds%>,
        excludeCategoryIds: <%=excludeCategoryIds%>,
        selectedFoldersId: '<%=selectedFoldersId%>',
        saveSettingsUrl: '<%=saveSettingsUrl%>',
        displayAccessDrive: <%=displayAccessDrive%>,
        driveUrl: '<%=driveUrl%>',
        opensInSameTab: <%=opensInSameTab%>
      }));
    </script>
  </div>
</div>
