<%@ taglib uri="http://java.sun.com/portlet_2_0" prefix="portlet" %>
<portlet:defineObjects />
<%
  String id = "downloadDocumentPublicAccess-" + renderRequest.getWindowID();

%>
<div class="VuetifyApp">
  <div data-app="true"
    class="v-application white v-application--is-ltr theme--light downloadDocumentPublicAccess"
    id="<%=id%>">
    <script type="text/javascript">
      require(['PORTLET/documents-portlet/DownloadDocumentsPublicAccess'], app =>app.init('<%=id%>'));
    </script>
  </div>
</div>
