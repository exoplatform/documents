package org.exoplatform.documents.storage.jcr.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import javax.jcr.RepositoryException;
import javax.xml.namespace.QName;

import org.exoplatform.documents.webdav.model.WebDavItemProperty;
import org.exoplatform.services.jcr.access.AccessControlEntry;
import org.exoplatform.services.jcr.access.AccessControlList;
import org.exoplatform.services.jcr.access.PermissionType;
import org.exoplatform.services.jcr.impl.core.NodeImpl;
import org.exoplatform.services.jcr.webdav.util.PropertyConstants;
import org.exoplatform.services.security.IdentityConstants;

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

  /**
   * Defines the name of the element corresponding to read privilege which in
   * current implementation aggregate: READ permission. More details can be
   * found
   * <a href="http://www.webdav.org/specs/rfc3744.html#privileges">here</a>.
   */
  public static final QName READ      = new QName("DAV:", "read");

  /**
   * Gets {@link AccessControlList} and transform it to DAV:acl property view
   * represented by a {@link WebDavItemProperty} instance.
   * 
   * @param node - {@link NodeImpl} from which we are to get an ACL
   * @return WebDavItemProperty - tree like structure corresponding to an
   *         DAV:acl property
   * @throws RepositoryException
   */
  public static WebDavItemProperty getACL(NodeImpl node) throws RepositoryException {
    WebDavItemProperty property = new WebDavItemProperty(ACL);

    AccessControlList acl = node.getACL();

    HashMap<String, List<String>> principals = new HashMap<>();

    List<AccessControlEntry> entryList = acl.getPermissionEntries();
    for (AccessControlEntry entry : entryList) {
      String principal = entry.getIdentity();
      String grant = entry.getPermission();
      principals.computeIfAbsent(principal, p -> new ArrayList<>())
                .add(grant);
    }

    Iterator<String> principalIter = principals.keySet().iterator();
    while (principalIter.hasNext()) {
      WebDavItemProperty aceProperty = new WebDavItemProperty(ACE);

      String curPrincipal = principalIter.next();

      aceProperty.addChild(getPrincipalProperty(curPrincipal));

      aceProperty.addChild(getGrantProperty(principals.get(curPrincipal)));

      property.addChild(aceProperty);
    }

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

  private static WebDavItemProperty getPrincipalProperty(String principal) {
    WebDavItemProperty principalProperty = new WebDavItemProperty(PRINCIPAL);

    if (IdentityConstants.ANY.equals(principal)) {
      WebDavItemProperty all = new WebDavItemProperty(ALL);
      principalProperty.addChild(all);
    } else {
      WebDavItemProperty href = new WebDavItemProperty(HREF);
      href.setValue(principal);
      principalProperty.addChild(href);
    }

    return principalProperty;
  }

  private static WebDavItemProperty getGrantProperty(List<String> grantList) {
    WebDavItemProperty grant = new WebDavItemProperty(GRANT);

    if (grantList.contains(PermissionType.ADD_NODE) || grantList.contains(PermissionType.SET_PROPERTY)
        || grantList.contains(PermissionType.REMOVE)) {
      WebDavItemProperty privilege = new WebDavItemProperty(PRIVILEGE);
      privilege.addChild(new WebDavItemProperty(WRITE));
      grant.addChild(privilege);
    }

    if (grantList.contains(PermissionType.READ)) {
      WebDavItemProperty privilege = new WebDavItemProperty(PRIVILEGE);
      privilege.addChild(new WebDavItemProperty(READ));
      grant.addChild(privilege);
    }

    return grant;
  }

}
