/*
 * Copyright (C) 2023 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.exoplatform.documents.storage.jcr.search;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Collection;

import org.exoplatform.commons.api.search.data.SearchResult;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import org.exoplatform.commons.search.es.client.ElasticSearchingClient;
import org.exoplatform.commons.utils.IOUtil;
import org.exoplatform.container.configuration.ConfigurationManager;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.PropertiesParam;
import org.exoplatform.container.xml.ValueParam;
import org.exoplatform.documents.constant.DocumentSortField;
import org.exoplatform.documents.model.DocumentFolderFilter;
import org.exoplatform.documents.model.DocumentNodeFilter;
import org.exoplatform.services.security.Identity;
import org.exoplatform.services.security.IdentityConstants;
import org.exoplatform.social.core.manager.IdentityManager;

@RunWith(MockitoJUnitRunner.class)
public class DocumentSearchServiceConnectorTest {

  public static final String   QUERY_TAG_TERM                    = "@term@";

  private static final String  ES_INDEX                          = "file_alias";

  private static final String  WORKSPACE                         = "collaboration";

  private static final String  SEARCH_QUERY_FILE_PATH_PARAM      = "query.file.path";

  public static String         SEARCH_QUERY_TERM                 = "\"must\":{" + "    \"query_string\":{"
      + "    \"fields\": [\"title\"]," + "    \"query\": \"*@term@*\"" + "  }" + "},";

  private static String        SEARCH_QUERY;

  @Mock
  IdentityManager              identityManager;

  @Mock
  ElasticSearchingClient       client;

  private String               searchResult                      = null;

  private String               searchWithReservedCharacterResult = null;

  private ConfigurationManager configurationManager;

  @Before
  public void setUp() throws Exception {
    configurationManager = mock(ConfigurationManager.class);
    try {
      SEARCH_QUERY = IOUtil.getStreamContentAsString(getClass().getClassLoader()
                                                               .getResourceAsStream("documents-search-query.json"));
      searchResult = IOUtil.getStreamContentAsString(getClass().getClassLoader()
                                                               .getResourceAsStream("document-search-result.json"));
      searchWithReservedCharacterResult =
                                        IOUtil.getStreamContentAsString(getClass().getClassLoader()
                                                                                  .getResourceAsStream("document-search-reserved-character-result.json"));
      Mockito.reset(configurationManager);
      when(configurationManager.getInputStream("FILE_PATH")).thenReturn(new ByteArrayInputStream(SEARCH_QUERY.getBytes()));
    } catch (Exception e) {
      throw new IllegalStateException("Error retrieving ES Query content", e);
    }
  }

  @Test
  public void searchTest() {

    Identity userIdentity = mock(Identity.class);
    when(userIdentity.getUserId()).thenReturn("1");
    when(userIdentity.getMemberships()).thenReturn(new ArrayList<>());
    DocumentNodeFilter filter = new DocumentFolderFilter(null, null, 1L, null);
    filter.setExtendedSearch(false);
    filter.setFavorites(false);
    filter.setIncludeHiddenFiles(false);
    filter.setSortField(DocumentSortField.NAME);
    filter.setAscending(true);
    filter.setQuery("test");
    int limit = 51;
    int offset = 0;
    String path = "/Groups/spaces/test/Documents/";
    String sort_field = "title";
    String sort_direction = "ASC";
    String expectedQuery = SEARCH_QUERY.replace("@term_query@", SEARCH_QUERY_TERM.replace(QUERY_TAG_TERM, filter.getQuery()))
                                       .replace("@favorite_query@", "")
                                       .replace("@permissions@", getPermissionQuery(userIdentity))
                                       .replace("@fileTypes_query@", "")
                                       .replace("@size_query@", "")
                                       .replace("@date_query@", "")
                                       .replace("@path@", path)
                                       .replace("@workspace@", WORKSPACE)
                                       .replace("@sort_field@", sort_field + ".raw")
                                       .replace("@sort_direction@", sort_direction)
                                       .replace("@offset@", String.valueOf(offset))
                                       .replace("@limit@", String.valueOf(limit));
    DocumentSearchServiceConnector documentSearchServiceConnector = new DocumentSearchServiceConnector(configurationManager,
                                                                                                       identityManager,
                                                                                                       client,
                                                                                                       getParams());
    // limit is negative
    assertThrows(IllegalArgumentException.class,
                 () -> documentSearchServiceConnector.search(userIdentity,
                                                             WORKSPACE,
                                                             path,
                                                             filter,
                                                             offset,
                                                             -limit,
                                                             sort_field,
                                                             sort_direction));
    // identity is null
    assertThrows(IllegalArgumentException.class,
                 () -> documentSearchServiceConnector.search(null,
                                                             WORKSPACE,
                                                             path,
                                                             filter,
                                                             offset,
                                                             limit,
                                                             sort_field,
                                                             sort_direction));
    // search term is mandatory
    filter.setQuery(null);
    assertThrows(IllegalArgumentException.class,
                 () -> documentSearchServiceConnector.search(userIdentity,
                                                             WORKSPACE,
                                                             path,
                                                             filter,
                                                             offset,
                                                             -limit,
                                                             sort_field,
                                                             sort_direction));

    // when
    filter.setQuery("test");
    when(client.sendRequest(expectedQuery, ES_INDEX)).thenReturn(searchResult);
    Collection<SearchResult> result = documentSearchServiceConnector.search(userIdentity,
                                                                            WORKSPACE,
                                                                            path,
                                                                            filter,
                                                                            offset,
                                                                            limit,
                                                                            sort_field,
                                                                            sort_direction);

    // then
    // verify call with the expected query
    // assert search result
    verify(client, times(1)).sendRequest(expectedQuery, ES_INDEX);
    assertFalse(result.isEmpty());

    // when
    // search term contains ES reserved character
    filter.setQuery("term with reserved - character");
    String esquipedReservedCharactersTermQuery = "*term* AND *with* AND *reserved* AND \\\\*\\\\-\\\\* AND *character*";
    String oldSearchedTermQuery = "*test*";
    when(client.sendRequest(expectedQuery.replace(oldSearchedTermQuery, esquipedReservedCharactersTermQuery),
                            ES_INDEX)).thenReturn(searchWithReservedCharacterResult);
    result = documentSearchServiceConnector.search(userIdentity,
                                                   WORKSPACE,
                                                   path,
                                                   filter,
                                                   offset,
                                                   limit,
                                                   sort_field,
                                                   sort_direction);

    // verify expected search query
    // assert search result
    verify(client, times(1)).sendRequest(expectedQuery.replace(oldSearchedTermQuery, esquipedReservedCharactersTermQuery),
                                         ES_INDEX);
    assertFalse(result.isEmpty());
  }

  @Test
  public void buildEscapedQueryWithAndOperatorTest() {
    DocumentSearchServiceConnector documentSearchServiceConnector = new DocumentSearchServiceConnector(configurationManager,
                                                                                                       identityManager,
                                                                                                       client,
                                                                                                       getParams());
    // when
    String term = documentSearchServiceConnector.escapeReservedCharacters("termquery");
    String escapedQueryWithAndOperator = documentSearchServiceConnector.buildEscapedQueryWithAndOperator(term);
    // then
    assertEquals("termquery", escapedQueryWithAndOperator);

    //when
    String escapedReservedCharacters = documentSearchServiceConnector.escapeReservedCharacters("test escaped-query");
    escapedQueryWithAndOperator = documentSearchServiceConnector.buildEscapedQueryWithAndOperator(escapedReservedCharacters);
    //then
    assertEquals("test* AND \\\\*escaped\\\\-query\\\\", escapedQueryWithAndOperator);

  }

  private InitParams getParams() {
    InitParams params = new InitParams();
    PropertiesParam propertiesParam = new PropertiesParam();
    propertiesParam.setName("constructor.params");
    propertiesParam.setProperty("index", ES_INDEX);

    ValueParam valueParam = new ValueParam();
    valueParam.setName(SEARCH_QUERY_FILE_PATH_PARAM);
    valueParam.setValue("FILE_PATH");

    params.addParameter(propertiesParam);
    params.addParameter(valueParam);
    return params;
  }

  private String getPermissionQuery(Identity userIdentity) {
    String permissionQuery = "{\n" + "  \"term\" : { \"permissions\" : \"" + userIdentity.getUserId() + "\" }\n" + "},\n" + "{\n"
        + "  \"term\" : { \"permissions\" : \"" + IdentityConstants.ANY + "\" }\n" + "}";
    return permissionQuery;
  }
}
