<%@ taglib uri="http://java.sun.com/portlet_2_0" prefix="portlet" %>
<portlet:defineObjects />
<%
  String id = "DocumentsWidget" + renderRequest.getWindowID();
%>
<div class="VuetifyApp">
  <div data-app="true"
    class="v-application transparent v-application--is-ltr theme--light" flat=""
    id="<%=id%>">
    <script type="text/javascript">
      require(['PORTLET/social-portlet/DocumentsWidget'], app => app.init('<%=id%>'));
    </script>
  </div>
</div>