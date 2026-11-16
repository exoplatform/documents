/*
 * Copyright (C) 2026 eXo Platform SAS.
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
package org.exoplatform.documents.storage.jcr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.jcr.PropertyType;
import javax.jcr.version.OnParentVersionAction;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.exoplatform.documents.storage.jcr.util.NodeTypeConstants;
import org.exoplatform.services.jcr.core.nodetype.NodeTypeData;
import org.exoplatform.services.jcr.core.nodetype.PropertyDefinitionData;
import org.exoplatform.services.jcr.impl.core.LocationFactory;
import org.exoplatform.services.jcr.impl.core.NamespaceRegistryImpl;
import org.exoplatform.services.jcr.impl.core.nodetype.registration.NodeTypeConverter;
import org.exoplatform.services.jcr.impl.core.nodetype.registration.XmlNodeTypeDataPersister;

/**
 * Reads conf/documents-nodetypes.xml with the repository's own node type
 * reader, the one AddNodeTypePlugin feeds at startup, so a definition the
 * repository would refuse fails here. Registration itself (supertypes,
 * conflicts with a type already registered) needs a running repository.
 */
class RecycleBinNodeTypesTest {

  private static final String              NODE_TYPES_FILE = "/conf/documents-nodetypes.xml";

  private static LocationFactory           locationFactory;

  private static Map<String, NodeTypeData> nodeTypes;

  @BeforeAll
  static void readNodeTypes() throws Exception {
    locationFactory = new LocationFactory(new NamespaceRegistryImpl());
    NodeTypeConverter converter = new NodeTypeConverter(locationFactory, "optional");
    nodeTypes = new HashMap<>();
    try (InputStream is = RecycleBinNodeTypesTest.class.getResourceAsStream(NODE_TYPES_FILE)) {
      assertNotNull(is, NODE_TYPES_FILE + " is not on the classpath");
      List<NodeTypeData> all = new XmlNodeTypeDataPersister(converter, is).getAllNodeTypes();
      for (NodeTypeData nodeType : all) {
        nodeTypes.put(locationFactory.createJCRName(nodeType.getName()).getAsString(), nodeType);
      }
    }
  }

  @Test
  void recycleBinItemIsAMixinWithItsFacts() throws Exception {
    NodeTypeData item = nodeTypes.get(NodeTypeConstants.EXO_RECYCLE_BIN_ITEM);
    assertNotNull(item);
    assertTrue(item.isMixin());

    Map<String, PropertyDefinitionData> properties = properties(item);
    assertEquals(8, properties.size());
    assertOptionalProperty(properties, NodeTypeConstants.RECYCLE_BIN_OWNER, PropertyType.STRING, false);
    assertOptionalProperty(properties, NodeTypeConstants.RESTORE_PARENT_ID, PropertyType.STRING, false);
    assertOptionalProperty(properties, NodeTypeConstants.DELETED_BY, PropertyType.STRING, false);
    assertOptionalProperty(properties, NodeTypeConstants.DELETED_DATE, PropertyType.DATE, false);
    assertOptionalProperty(properties, NodeTypeConstants.RECYCLE_BIN_SIZE, PropertyType.LONG, false);
    assertOptionalProperty(properties, NodeTypeConstants.RECYCLE_BIN_NAME, PropertyType.STRING, false);
    assertOptionalProperty(properties, NodeTypeConstants.RECYCLE_BIN_OF, PropertyType.STRING, false);
    assertOptionalProperty(properties, NodeTypeConstants.RECYCLE_BIN_PERMISSIONS, PropertyType.STRING, true);
  }

  @Test
  void recycleBinFolderIsAMarkerMixin() {
    NodeTypeData folder = nodeTypes.get(NodeTypeConstants.EXO_RECYCLE_BIN_FOLDER);
    assertNotNull(folder);
    assertTrue(folder.isMixin());
    assertEquals(0, folder.getDeclaredPropertyDefinitions().length);
    assertEquals(0, folder.getDeclaredChildNodeDefinitions().length);
  }

  private static Map<String, PropertyDefinitionData> properties(NodeTypeData nodeType) throws Exception {
    Map<String, PropertyDefinitionData> properties = new HashMap<>();
    for (PropertyDefinitionData property : nodeType.getDeclaredPropertyDefinitions()) {
      properties.put(locationFactory.createJCRName(property.getName()).getAsString(), property);
    }
    return properties;
  }

  private static void assertOptionalProperty(Map<String, PropertyDefinitionData> properties,
                                             String name,
                                             int type,
                                             boolean multiple) {
    PropertyDefinitionData property = properties.get(name);
    assertNotNull(property, name);
    assertEquals(type, property.getRequiredType(), name);
    assertEquals(multiple, property.isMultiple(), name);
    assertFalse(property.isMandatory(), name);
    assertFalse(property.isAutoCreated(), name);
    assertFalse(property.isProtected(), name);
    assertEquals(OnParentVersionAction.IGNORE, property.getOnParentVersion(), name);
  }

}
