package org.exoplatform.documents.storage.jcr.util;

import javax.jcr.RepositoryException;
import javax.xml.namespace.QName;

import org.exoplatform.documents.webdav.model.WebDavItemProperty;
import org.exoplatform.services.jcr.access.AccessControlList;
import org.exoplatform.services.jcr.access.PermissionType;
import org.exoplatform.services.jcr.impl.core.NodeImpl;
import org.exoplatform.services.jcr.webdav.util.PropertyConstants;

import lombok.SneakyThrows;

public class ACLProperties {

  private ACLProperties() {
    // constant class
  }

  /**
   * Defines the name of the element corresponding to a protected property that
   * specifies the list of access control entries. More details can be found
   * <a href="http://www.webdav.org/specs/rfc3744.html#PROPERTY_acl">here</a>.
   */
  public static final QName ACL       = new QName("DAV:", "acl");

  /**
   * Defines the name of the element corresponding to a property that the set of
   * privileges to be either granted or denied to a single principal. More
   * details can be found
   * <a href="http://www.webdav.org/specs/rfc3744.html#PROPERTY_acl">here</a>.
   */
  public static final QName ACE       = new QName("DAV:", "ace");

  /**
   * Defines the name of the element corresponding to a property which
   * identifies the principal to which this ACE applies. More details can be
   * found
   * <a href='http://www.webdav.org/specs/rfc3744.html#principals'>here</a> and
   * <a href="http://www.webdav.org/specs/rfc3744.html#PROPERTY_acl">here</a>.
   */
  public static final QName PRINCIPAL = new QName("DAV:", "principal");

  /**
   * Defines the name of the element corresponding to a property that can be
   * either an aggregate privilege that contains the entire set of privileges
   * that can be applied to the resource or an aggregate principal that contains
   * the entire set of principals. More details can be found
   * <a href="http://www.webdav.org/specs/rfc3744.html#PRIVILEGE_all">here</a>.
   */
  public static final QName ALL       = new QName("DAV:", "all");

  /**
   * Defines the name of the element corresponding to a property which is used
   * to uniquely identify a principal. More details can be found <a href=
   * "http://www.webdav.org/specs/rfc3744.html#PROPERTY_principal-URL">here</a>.
   */
  public static final QName HREF      = new QName("DAV:", "href");

  /**
   * Defines the name of the element containing privilege's name. More details
   * can be found
   * <a href="http://www.webdav.org/specs/rfc3744.html#privileges">here</a>.
   */
  public static final QName PRIVILEGE = new QName("DAV:", "privilege");

  /**
   * Defines the name of the element containing privileges to be granted. More
   * details can be found <a href=
   * "http://www.webdav.org/specs/rfc3744.html#rfc.section.5.5.2">here</a>.
   */
  public static final QName GRANT     = new QName("DAV:", "grant");

  /**
   * Defines the name of the element containing privileges to be denied. More
   * details can be found <a href=
   * "http://www.webdav.org/specs/rfc3744.html#rfc.section.5.5.2">here</a>.
   */
  public static final QName DENY      = new QName("DAV:", "deny");

  /**
   * Defines the name of the element corresponding to write privilege which in
   * current implementation aggregate: ADD_NODE, SET_PROPERTY, REMOVE
   * permissions. More details can be found
   * <a href="http://www.webdav.org/specs/rfc3744.html#privileges">here</a>.
   */
  public static final QName WRITE     = new QName("DAV:", "write");

  public static final QName UNLOCK    = new QName("DAV:", "unlock");

  /**
   * Defines the name of the element corresponding to read privilege which in
   * current implementation aggregate: READ permission. More details can be
   * found
   * <a href="http://www.webdav.org/specs/rfc3744.html#privileges">here</a>.
   */
  public static final QName READ      = new QName("DAV:", "read");

  public static WebDavItemProperty getReadOnlyACL() {
    WebDavItemProperty property = new WebDavItemProperty(ACL);
    property.addChild(computeReadOnlyAceProperty());
    return property;
  }

  /**
   * Gets {@link AccessControlList} and transform it to DAV:acl property view
   * represented by a {@link WebDavItemProperty} instance.
   * 
   * @param node - {@link NodeImpl} from which we are to get an ACL
   * @return WebDavItemProperty - tree like structure corresponding to an
   *         DAV:acl property
   */
  public static WebDavItemProperty getACL(NodeImpl node) {
    WebDavItemProperty property = new WebDavItemProperty(ACL);
    property.addChild(computeAceProperty(node));
    return property;
  }

  /**
   * Transform owner got from node's {@link AccessControlList} to tree like
   * {@link WebDavItemProperty} instance to use in PROPFIND response body
   * 
   * @param node
   * @return {@link WebDavItemProperty} representation of node owner
   * @throws RepositoryException
   */
  public static WebDavItemProperty getOwner(NodeImpl node) throws RepositoryException {
    WebDavItemProperty ownerProperty = new WebDavItemProperty(PropertyConstants.OWNER);

    WebDavItemProperty href = new WebDavItemProperty(new QName("DAV:", "href"));
    href.setValue(node.getACL().getOwner());

    ownerProperty.addChild(href);

    return ownerProperty;
  }

  private static WebDavItemProperty getAllPrincipalProperty() {
    WebDavItemProperty principalProperty = new WebDavItemProperty(PRINCIPAL);
    WebDavItemProperty all = new WebDavItemProperty(ALL);
    principalProperty.addChild(all);
    return principalProperty;
  }

  private static WebDavItemProperty computeReadOnlyAceProperty() {
    WebDavItemProperty aceProperty = new WebDavItemProperty(ACE);
    aceProperty.addChild(getAllPrincipalProperty());
    WebDavItemProperty grant = aceProperty.addChild(new WebDavItemProperty(GRANT));
    grant.addChild(new WebDavItemProperty(PRIVILEGE))
         .addChild(new WebDavItemProperty(READ));

    WebDavItemProperty deny = aceProperty.addChild(new WebDavItemProperty(DENY));
    deny.addChild(new WebDavItemProperty(PRIVILEGE))
        .addChild(new WebDavItemProperty(WRITE));
    return aceProperty;
  }

  @SneakyThrows
  private static WebDavItemProperty computeAceProperty(NodeImpl node) {
    WebDavItemProperty aceProperty = new WebDavItemProperty(ACE);
    aceProperty.addChild(getAllPrincipalProperty());

    WebDavItemProperty grant = new WebDavItemProperty(GRANT);
    aceProperty.addChild(grant);

    if (node.hasPermission(PermissionType.ADD_NODE)
        || node.hasPermission(PermissionType.SET_PROPERTY)
        || node.hasPermission(PermissionType.REMOVE)) {
      if (node.isLocked()) {
        WebDavItemProperty privilege = new WebDavItemProperty(PRIVILEGE);
        privilege.addChild(new WebDavItemProperty(UNLOCK));
        grant.addChild(privilege);
      }
      WebDavItemProperty privilege = new WebDavItemProperty(PRIVILEGE);
      privilege.addChild(new WebDavItemProperty(WRITE));
      grant.addChild(privilege);
    } else {
      WebDavItemProperty deny = new WebDavItemProperty(DENY);
      WebDavItemProperty privilege = new WebDavItemProperty(PRIVILEGE);
      privilege.addChild(new WebDavItemProperty(WRITE));
      deny.addChild(privilege);
      aceProperty.addChild(deny);
    }

    if (node.hasPermission(PermissionType.READ)) {
      WebDavItemProperty privilege = new WebDavItemProperty(PRIVILEGE);
      privilege.addChild(new WebDavItemProperty(READ));
      grant.addChild(privilege);
    }
    return aceProperty;
  }

}
