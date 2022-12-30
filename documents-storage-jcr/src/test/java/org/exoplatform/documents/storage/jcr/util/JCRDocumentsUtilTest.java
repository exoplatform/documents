package org.exoplatform.documents.storage.jcr.util;

import org.exoplatform.documents.model.AbstractNode;
import org.exoplatform.services.jcr.access.AccessControlList;
import org.exoplatform.services.jcr.core.ExtendedNode;
import org.exoplatform.services.jcr.impl.core.NodeImpl;
import org.exoplatform.services.jcr.impl.core.value.StringValue;
import org.exoplatform.services.security.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.junit.Test;

import javax.jcr.*;

import java.io.IOException;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class JCRDocumentsUtilTest {

  @Test
  public void testRetrieveFileProperties() throws IOException, RepositoryException {
    IdentityManager identityManager = mock(IdentityManager.class);
    NodeImpl node = mock(NodeImpl.class);
    Identity aclIdentity = mock(Identity.class);
    AbstractNode documentNode = mock(AbstractNode.class);
    SpaceService spaceService = mock(SpaceService.class);

    // Build node properties
    NodeImpl parentNode = mock(NodeImpl.class);
    when(((NodeImpl) parentNode).getIdentifier()).thenReturn("identifierOfParentNode");
    when(node.getParent()).thenReturn(parentNode);
    when(node.getName()).thenReturn("NodeName.pdf");
    Property property = mock(Property.class);
    when(((ExtendedNode) node).getACL()).thenReturn(new AccessControlList());

    when(aclIdentity.getUserId()).thenReturn("root");

    // When
    when(node.getProperty(NodeTypeConstants.DC_DESCRIPTION)).thenReturn(property);
    when(node.hasProperty(NodeTypeConstants.DC_DESCRIPTION)).thenReturn(true);
    when(node.getProperty(NodeTypeConstants.DC_DESCRIPTION).getString()).thenThrow(new ValueFormatException());
    // Then
    try {
      JCRDocumentsUtil.retrieveFileProperties(identityManager, node, aclIdentity, documentNode, spaceService);
    } catch (Exception e) {
      // Exception should be catched
      fail();
    }

    // When
    when(node.getProperty(NodeTypeConstants.DC_DESCRIPTION).getValues()).thenReturn(new Value[0]);
    // Then
    JCRDocumentsUtil.retrieveFileProperties(identityManager, node, aclIdentity, documentNode, spaceService);
    verify(documentNode, times(0)).setDescription(anyString());

    // When
    when(node.getProperty(NodeTypeConstants.DC_DESCRIPTION).getValues()).thenReturn(new Value[1]);
    Value[] descriptionValues = new Value[]{new StringValue("File description !")};
    when(node.getProperty(NodeTypeConstants.DC_DESCRIPTION).getValues()).thenReturn(descriptionValues);


    JCRDocumentsUtil.retrieveFileProperties(identityManager, node, aclIdentity, documentNode, spaceService);
    // Then
    verify(documentNode, times(1)).setDescription(anyString());

  }
}
