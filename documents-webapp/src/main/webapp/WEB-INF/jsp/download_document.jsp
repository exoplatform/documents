<%@ page import="org.exoplatform.commons.utils.CommonsUtils"%>
<%@page import="org.json.JSONObject"%>
<%@page import="org.exoplatform.documents.service.ExternalDownloadService"%>
<%@page import="org.exoplatform.documents.service.PublicDocumentAccessService"%>
<%@page import="org.exoplatform.portal.application.PortalRequestContext"%>
<%@page import="org.exoplatform.documents.model.DownloadItem"%>
<%@page import="org.exoplatform.documents.model.PublicDocumentAccess"%>

<%@ taglib uri="http://java.sun.com/portlet_2_0" prefix="portlet" %>
<portlet:defineObjects />
<%

  String id = "downloadDocumentPublicAccess-" + renderRequest.getWindowID();
  ExternalDownloadService externalDownloadService = CommonsUtils.getService(ExternalDownloadService.class);
  PublicDocumentAccessService publicDocumentAccessService = CommonsUtils.getService(PublicDocumentAccessService.class);

  PortalRequestContext rcontext = (PortalRequestContext) PortalRequestContext.getCurrentInstance();
  String documentId = rcontext.getRequest().getParameter("nodeId");
  JSONObject params = new JSONObject();

  if (documentId != null) {

    DownloadItem downloadItem = externalDownloadService.getDocumentDownloadItem(documentId);
    PublicDocumentAccess publicDocumentAccess = publicDocumentAccessService.getPublicDocumentAccess(documentId);

    boolean hasPublicLink = publicDocumentAccess != null;
    boolean isAccessLocked = hasPublicLink && publicDocumentAccess.getPasswordHashKey() != null;
    boolean isAccessExpired = publicDocumentAccessService.isPublicDocumentAccessExpired(documentId);

    params.put("nodeId", documentId);
    params.put("hasPublicLink", hasPublicLink);
    params.put("isAccessLocked", isAccessLocked);
    params.put("isAccessExpired", isAccessExpired);
    if (downloadItem != null) {
      params.put("documentName", downloadItem.getItemName());
      params.put("documentType", downloadItem.getMimeType());
    }

  }
%>
<div class="VuetifyApp">
  <div data-app="true"
    class="v-application white v-application--is-ltr theme--light loginForm"
    id="<%=id%>">
    <script type="text/javascript">
      require(['PORTLET/documents-portlet/DownloadDocumentsPublicAccess'], app =>app.init('<%=id%>',JSON.stringify(<%=params.toString()%>)));
    </script>
  </div>
</div>
