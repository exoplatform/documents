package org.exoplatform.documents.storage.jcr;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.exoplatform.services.jcr.impl.core.LocationFactory;
import org.exoplatform.services.jcr.impl.core.NamespaceRegistryImpl;
import org.exoplatform.services.jcr.impl.core.query.DefaultQueryNodeFactory;
import org.exoplatform.services.jcr.impl.core.query.sql.JCRSQLQueryBuilder;

class DocumentCategoryQueryTest {

  @Test
  @DisplayName("Statement parses under the repository's own JCR-SQL grammar; index usage and dialect behavior on a real repository are not covered by this suite")
  void documentCategoryIdsQueryParsesUnderJcrSqlGrammar() throws Exception {
    String statement = String.format(JCRDocumentFileStorage.DOCUMENT_CATEGORY_IDS_QUERY, "/Groups/spaces/test_space/Documents");

    assertNotNull(JCRSQLQueryBuilder.createQuery(statement,
                                                 new LocationFactory(new NamespaceRegistryImpl()),
                                                 new DefaultQueryNodeFactory()));
  }
}
