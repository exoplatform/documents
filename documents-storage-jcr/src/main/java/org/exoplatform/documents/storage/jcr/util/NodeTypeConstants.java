/*
 * Copyright (C) 2021 eXo Platform SAS
 *  
 *  This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <gnu.org/licenses>.
 */
package org.exoplatform.documents.storage.jcr.util;

public class NodeTypeConstants {

  private NodeTypeConstants() {
    // Utils class, no constructor is needed
  }

  public static final String NT_BASE                = "nt:base";

  public static final String NT_HIERARCHY_NODE      = "nt:hierarchyNode";

  public static final String NT_FILE                = "nt:file";

  public static final String NT_FOLDER              = "nt:folder";

  public static final String NT_UNSTRUCTURED        = "nt:unstructured";

  public static final String NT_RESOURCE            = "nt:resource";

  public static final String NT_VERSIONED_CHILD     = "nt:versionedChild";

  public static final String NT_FROZEN_NODE         = "nt:frozenNode";

  public static final String DC_ELEMENT_SET         = "dc:elementSet";

  public static final String DC_TITLE               = "dc:title";

  public static final String DC_DESCRIPTION         = "dc:description";

  public static final String DC_CREATOR             = "dc:creator";

  public static final String DC_SOURCE              = "dc:source";

  public static final String JCR_UUID               = "jcr:uuid";

  public static final String JCR_FROZEN_UUID        = "jcr:frozenUuid";

  public static final String JCR_CONTENT            = "jcr:content";

  public static final String JCR_ENCODING           = "jcr:encoding";

  public static final String JCR_MIME_TYPE          = "jcr:mimeType";

  public static final String JCR_DATA               = "jcr:data";

  public static final String JCR_CREATED_DATE       = "jcr:created";

  public static final String JCR_LAST_MODIFIED      = "jcr:lastModified";

  public static final String JCR_DATE_MODIFIED      = "jcr:dateModified";

  public static final String MIX_REFERENCEABLE      = "mix:referenceable";

  public static final String MIX_VERSIONABLE        = "mix:versionable";

  public static final String EXO_OWNER              = "exo:owner";

  public static final String EXO_OWNEABLE           = "exo:owneable";

  public static final String EXO_MODIFY             = "exo:modify";

  public static final String EXO_SORTABLE           = "exo:sortable";

  public static final String EXO_RSS_ENABLE         = "exo:rss-enable";

  public static final String EXO_PRIVILEGEABLE      = "exo:privilegeable";

  public static final String EXO_TITLE              = "exo:title";

  public static final String EXO_DATE_CREATED       = "exo:dateCreated";

  public static final String EXO_DATE_MODIFIED      = "exo:dateModified";

  public static final String EXO_LAST_MODIFIED_DATE = "exo:lastModifiedDate";

  public static final String EXO_LAST_MODIFIER      = "exo:lastModifier";

  public static final String EXO_HIDDENABLE         = "exo:hiddenable";

  public static final String EXO_SYMLINK            = "exo:symlink";

  public static final String EXO_SYMLINK_UUID       = "exo:uuid";

  public static final String EXO_TARGET_DATA        = "exo:targetData";

  public static final String EXO_WORKSPACE          = "exo:workspace";

  public static final String MIX_I18N               = "mix:i18n";

  public static final String MIX_VOTABLE            = "mix:votable";

  public static final String MIX_COMMENTABLE        = "mix:commentable";

}
