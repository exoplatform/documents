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
<%@ page import="org.exoplatform.documents.utils.DocumentUtils" %>
<%@ page import="org.exoplatform.documents.portlet.DocumentGadgetSettings" %>

<%@ taglib uri="http://java.sun.com/portlet_2_0" prefix="portlet" %>
<portlet:defineObjects/>
<portlet:actionURL var="saveSettingsUrl" />
<%
  String settingName = (String) request.getAttribute("settingName");
  String headerTitle = (String) request.getAttribute("headerTitle");
  boolean canEdit = (boolean) request.getAttribute("canEdit");
  String id = "DocumentGadget-" + renderRequest.getWindowID();

  PortletPreferences preferences = renderRequest.getPreferences();
  String viewOptions = DocumentGadgetSettings.valueOrDefault("viewOptions", preferences.getValue("viewOptions", null), "list");
  String documentType = DocumentGadgetSettings.valueOrDefault("documentType", preferences.getValue("documentType", null), "recentDocument");
  String spaceIdentityId = DocumentGadgetSettings.valueOrDefault("spaceIdentityId", preferences.getValue("spaceIdentityId", null), "");
  boolean customHeader = Boolean.parseBoolean(DocumentGadgetSettings.valueOrDefault("customHeader", preferences.getValue("customHeader", null), "false"));
  boolean displaySeeMore = Boolean.parseBoolean(DocumentGadgetSettings.valueOrDefault("displaySeeMore", preferences.getValue("displaySeeMore", null), "true"));
  int maxDocumentsToList = Integer.parseInt(DocumentGadgetSettings.valueOrDefault("maxDocumentsToList", preferences.getValue("maxDocumentsToList", null), "4"));
  String categoryIds = DocumentGadgetSettings.valueOrDefault("categoryIds", preferences.getValue("categoryIds", null), "[]");
  String excludeCategoryIds = DocumentGadgetSettings.valueOrDefault("excludeCategoryIds", preferences.getValue("excludeCategoryIds", null), "[]");
  String selectedFoldersId = DocumentGadgetSettings.valueOrDefault("selectedFoldersId", preferences.getValue("selectedFoldersId", null), "");
  boolean displayAccessDrive = Boolean.parseBoolean(DocumentGadgetSettings.valueOrDefault("displayAccessDrive", preferences.getValue("displayAccessDrive", null), "false"));
  String driveUrl = DocumentGadgetSettings.valueOrDefault("driveUrl", preferences.getValue("driveUrl", null), "");
  boolean opensInSameTab = Boolean.parseBoolean(DocumentGadgetSettings.valueOrDefault("opensInSameTab", preferences.getValue("opensInSameTab", null), "true"));

%>

<div class="VuetifyApp">
  <div data-app="true"
    class="v-application transparent v-application--is-ltr theme--light"
    id="<%=id%>">
    <script type="text/javascript">
      require(['PORTLET/documents-portlet/DocumentGadget'], app => app.init({
        id: <%=DocumentUtils.toScriptStringLiteral(id)%>,
        settingName: <%=DocumentUtils.toScriptStringLiteral(settingName)%>,
        canEdit: <%=canEdit%>,
        viewOptions: <%=DocumentUtils.toScriptStringLiteral(viewOptions)%>,
        documentType: <%=DocumentUtils.toScriptStringLiteral(documentType)%>,
        spaceIdentityId: <%=DocumentUtils.toScriptStringLiteral(spaceIdentityId)%>,
        customHeader: <%=customHeader%>,
        headerTitle: <%=DocumentUtils.toScriptStringLiteral(headerTitle)%>,
        displaySeeMore: <%=displaySeeMore%>,
        maxDocumentsToList: <%=maxDocumentsToList%>,
        categoryIds: JSON.parse(<%=DocumentUtils.toScriptStringLiteral(categoryIds)%>),
        excludeCategoryIds: JSON.parse(<%=DocumentUtils.toScriptStringLiteral(excludeCategoryIds)%>),
        selectedFoldersId: <%=DocumentUtils.toScriptStringLiteral(selectedFoldersId)%>,
        saveSettingsUrl: <%=DocumentUtils.toScriptStringLiteral(saveSettingsUrl)%>,
        displayAccessDrive: <%=displayAccessDrive%>,
        driveUrl: <%=DocumentUtils.toScriptStringLiteral(driveUrl)%>,
        opensInSameTab: <%=opensInSameTab%>
      }));
    </script>
  </div>
</div>
