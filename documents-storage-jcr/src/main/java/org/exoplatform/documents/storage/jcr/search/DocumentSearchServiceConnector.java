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
package org.exoplatform.documents.storage.jcr.search;

import java.io.InputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import org.exoplatform.commons.api.search.data.SearchResult;
import org.exoplatform.commons.search.es.ElasticSearchException;
import org.exoplatform.commons.search.es.client.ElasticSearchingClient;
import org.exoplatform.commons.utils.IOUtil;
import org.exoplatform.commons.utils.PropertyManager;
import org.exoplatform.container.configuration.ConfigurationManager;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.PropertiesParam;
import org.exoplatform.documents.model.DocumentNodeFilter;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.security.Identity;
import org.exoplatform.services.security.IdentityConstants;
import org.exoplatform.services.security.MembershipEntry;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.metadata.favorite.FavoriteService;

public class DocumentSearchServiceConnector {
  private static final Log             LOG                          = ExoLogger.getLogger(DocumentSearchServiceConnector.class);

  private static final String          SEARCH_QUERY_FILE_PATH_PARAM = "query.file.path";

  public static final String           SEARCH_QUERY_TERM            = "\"must\":{"
          + "    \"query_string\":{"
          + "    \"fields\": [\"title\"],"
          + "    \"query\": \"*@term@*\""
          + "  }"
          + "},";

  public static final String           EXTENDED_SEARCH_QUERY_TERM   = "\"must\":{"
          + "    \"query_string\":{"
          + "    \"fields\": [\"title\",\"attachment.content\",\"dc:description\"],"
          + "    \"query\": \"*@term@*\""
          + "  }" + "},";

  public static final String           QUERY_TAG_TERM           = "@term@";
  private final ConfigurationManager   configurationManager;

  private final IdentityManager        identityManager;

  private final ElasticSearchingClient client;

  private final String                 index;

  private String                       searchQueryFilePath;

  private String                       searchQuery;

  public DocumentSearchServiceConnector(ConfigurationManager configurationManager,
                                        IdentityManager identityManager,
                                        ElasticSearchingClient client,
                                        InitParams initParams) {
    this.configurationManager = configurationManager;
    this.identityManager = identityManager;
    this.client = client;

    PropertiesParam param = initParams.getPropertiesParam("constructor.params");
    this.index = param.getProperty("index");
    if (initParams.containsKey(SEARCH_QUERY_FILE_PATH_PARAM)) {
      searchQueryFilePath = initParams.getValueParam(SEARCH_QUERY_FILE_PATH_PARAM).getValue();
      try {
        retrieveSearchQuery();
      } catch (Exception e) {
        LOG.error("Can't read elasticsearch search query from path {}", searchQueryFilePath, e);
      }
    }
  }

  public Collection<SearchResult> search(Identity userIdentity,
                                         String workspace,
                                         String path,
                                         DocumentNodeFilter filter,
                                         int offset,
                                         int limit,
                                         String sortField,
                                         String sortDirection) {
    if (userIdentity == null) {
      throw new IllegalArgumentException("Viewer identity is mandatory");
    }
    if (offset < 0) {
      throw new IllegalArgumentException("Offset must be positive");
    }
    if (limit < 0) {
      throw new IllegalArgumentException("Limit must be positive");
    }
    if (StringUtils.isBlank(filter.getQuery()) && !filter.getFavorites()) {
      throw new IllegalArgumentException("Filter term is mandatory");
    }
    switch (sortField) {
      case "lastUpdatedDate" :
        sortField = "lastUpdatedDate";
        break;
      case "title" :
        sortField = "title.raw";
        break;
      default:
        sortField = "_score";
    }

    String esQuery = buildQueryStatement(userIdentity, workspace, path, filter, sortField, sortDirection, offset, limit);
    String jsonResponse = this.client.sendRequest(esQuery, this.index);
    return buildResult(jsonResponse);
  }

  private String buildQueryStatement(Identity userIdentity,
                                     String workspace,
                                     String path,
                                     DocumentNodeFilter filter,
                                     String sortField,
                                     String sortDirection,
                                     int offset,
                                     int limit) {
    Map<String, List<String>> metadataFilters =
                                              buildMetadatasFilter(filter,
                                                                   identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME,
                                                                                                       userIdentity.getUserId()));
    String termQuery = buildTermQueryStatement(filter.getQuery(), filter.isExtendedSearch());
    String favoriteQuery = buildFavoriteQueryStatement(metadataFilters.get(FavoriteService.METADATA_TYPE.getName()));
    if (StringUtils.isNotEmpty(path) && !path.endsWith("/")) {
      path += "/";
    }
    return retrieveSearchQuery().replace("@term_query@", termQuery)
                                .replace("@favorite_query@", favoriteQuery)
                                .replace("@permissions@", getPermissionFilter(userIdentity))
                                .replace("@path@", path)
                                .replace("@workspace@", workspace)
                                .replace("@sort_field@", sortField)
                                .replace("@sort_direction@", sortDirection)
                                .replace("@offset@", String.valueOf(offset))
                                .replace("@limit@", String.valueOf(limit));
  }

  private String getPermissionFilter(org.exoplatform.services.security.Identity userIdentity) {
    StringBuilder permissionSB = new StringBuilder();
    Set<String> membershipSet = getUserMemberships(userIdentity);
    if (!membershipSet.isEmpty()) {
      String memberships = StringUtils.join(membershipSet.toArray(new String[membershipSet.size()]), "|");
      permissionSB.append("{\n")
                  .append("  \"term\" : { \"permissions\" : \"")
                  .append(userIdentity.getUserId())
                  .append("\" }\n")
                  .append("},\n")
                  .append("{\n")
                  .append("  \"term\" : { \"permissions\" : \"")
                  .append(IdentityConstants.ANY)
                  .append("\" }\n")
                  .append("},\n")
                  .append("{\n")
                  .append("  \"regexp\" : { \"permissions\" : \"")
                  .append(memberships)
                  .append("\" }\n")
                  .append("}");
    } else {
      permissionSB.append("{\n")
                  .append("  \"term\" : { \"permissions\" : \"")
                  .append(userIdentity.getUserId())
                  .append("\" }\n")
                  .append("},\n")
                  .append("{\n")
                  .append("  \"term\" : { \"permissions\" : \"")
                  .append(IdentityConstants.ANY)
                  .append("\" }\n")
                  .append("}");
    }
    return permissionSB.toString();
  }

  private Set<String> getUserMemberships(org.exoplatform.services.security.Identity userIdentity) {

    if (userIdentity == null) {
      throw new IllegalStateException("No Identity found: ConversationState.getCurrent().getIdentity() is null");
    }
    if (userIdentity.getMemberships() == null) {
      throw new IllegalStateException("No Membership found: ConversationState.getCurrent().getIdentity().getMemberships() is null");
    }
    Set<String> entries = new HashSet<>();
    for (MembershipEntry entry : userIdentity.getMemberships()) {
      if (entry.getMembershipType().equals(MembershipEntry.ANY_TYPE)) {
        entries.add(entry.toString().replace("*", ".*"));
      } else {
        entries.add(entry.toString());
        entries.add("*:" + entry.getGroup());
      }
    }
    return entries;
  }

  protected Collection<SearchResult> buildResult(String jsonResponse) {

    LOG.debug("Search Query response from ES : {} ", jsonResponse);

    Collection<SearchResult> results = new ArrayList<>();
    JSONParser parser = new JSONParser();

    Map json;
    try {
      json = (Map) parser.parse(jsonResponse);
    } catch (ParseException e) {
      throw new ElasticSearchException("Unable to parse JSON response", e);
    }

    JSONObject jsonResult = (JSONObject) json.get("hits");
    if (jsonResult != null) {
      JSONArray jsonHits = (JSONArray) jsonResult.get("hits");

      if (jsonHits != null) {
        for (Object jsonHit : jsonHits) {
          results.add(buildHit((JSONObject) jsonHit));
        }
      }
    }

    return results;

  }

  protected SearchResult buildHit(JSONObject jsonHit) {
    JSONObject hitSource = (JSONObject) jsonHit.get("_source");
    String title = getTitleFromJsonResult(hitSource);
    String url = getUrlFromJsonResult(hitSource);
    Long lastUpdatedDate = getUpdatedDateFromResult(hitSource);
    if (lastUpdatedDate == null)
      lastUpdatedDate = new Date().getTime();
    Double score = (Double) jsonHit.get("_score");
    // Get the excerpt
    JSONObject hitHighlight = (JSONObject) jsonHit.get("highlight");
    Map<String, List<String>> excerpts = new HashMap<>();
    StringBuilder excerpt = new StringBuilder();
    if (hitHighlight != null) {
      Iterator<?> keys = hitHighlight.keySet().iterator();
      while (keys.hasNext()) {
        String key = (String) keys.next();
        JSONArray highlights = (JSONArray) hitHighlight.get(key);

        @SuppressWarnings({ "unchecked", "rawtypes" })
        List<String> excerptsList = (List<String>) ((ArrayList) highlights).stream() // NOSONAR
                                                                           .map(Object::toString)
                                                                           .collect(Collectors.toList());
        excerpts.put(key, excerptsList);
        excerpt.append("... ").append(StringUtils.join(excerptsList, "..."));
      }
    }

    LOG.debug("Excerpt extract from ES response : {}", excerpt.toString());

    SearchResult searchResult = new SearchResult(url,
                                                 title,
                                                 excerpts,
                                                 excerpt.toString(),
                                                 null,
                                                 null,
                                                 lastUpdatedDate,
                                                 score.longValue());
    String id = (String) jsonHit.get("_id");
    String workspace = (String) hitSource.get("workspace");
    String nodePath = (String) hitSource.get("path");
    return new DocumentFileSearchResult(searchResult, id, workspace, nodePath);
  }

  protected Long getUpdatedDateFromResult(JSONObject hitSource) {
    Object date = hitSource.get("lastUpdatedDate");
    if (date instanceof Long) {
      return (Long) date;
    } else if (date != null) {
      try {
        return Long.parseLong(date.toString());
      } catch (Exception ex) {
        LOG.error("Can not parse updatedDate field as Long {}", date);
      }
    }
    return null;
  }

  protected String getUrlFromJsonResult(JSONObject hitSource) {
    return (String) hitSource.get("url");
  }

  protected String getTitleFromJsonResult(JSONObject hitSource) {
    return (String) hitSource.get("title");
  }

  protected String escapeReservedCharacters(String query) {
    return StringUtils.isNotEmpty(query) ? query.replaceAll("[" + Pattern.quote("+-=&|><!(){}\\[\\]^\"*?:\\/") + "]",
                                                            Matcher.quoteReplacement("\\\\") + "$0")
                                         : query;
  }

  private String buildTermQueryStatement(String term, boolean extendedSearch) {
    if (StringUtils.isBlank(term)) {
      return "";
    }
    String originalTerm = term;
    term = escapeReservedCharacters(term);
    String escapedQueryWithAndOperator = buildEscapedQueryWithAndOperator(term);
    boolean isEscapedReservedCharactersTerm = !originalTerm.equals(term);
    boolean isEscapedQueryWithAndOperator = !escapedQueryWithAndOperator.equals(term);
    if (!extendedSearch) {
      // If the reserved characters are escaped we will escape also the * character in the search query term.
      return isEscapedReservedCharactersTerm && !isEscapedQueryWithAndOperator ? SEARCH_QUERY_TERM.replace("*", "\\\\*").replace(QUERY_TAG_TERM, term) : SEARCH_QUERY_TERM.replace(QUERY_TAG_TERM, escapedQueryWithAndOperator);
    }
    return isEscapedReservedCharactersTerm && ! isEscapedQueryWithAndOperator ? EXTENDED_SEARCH_QUERY_TERM.replace("*", "\\\\*").replace(QUERY_TAG_TERM, term ) : EXTENDED_SEARCH_QUERY_TERM.replace(QUERY_TAG_TERM, escapedQueryWithAndOperator);
  }

  private String retrieveSearchQuery() {
    if (StringUtils.isBlank(this.searchQuery) || PropertyManager.isDevelopping()) {
      try {
        InputStream queryFileIS = this.configurationManager.getInputStream(searchQueryFilePath);
        this.searchQuery = IOUtil.getStreamContentAsString(queryFileIS);
      } catch (Exception e) {
        throw new IllegalStateException("Error retrieving search query from file: " + searchQueryFilePath, e);
      }
    }
    return this.searchQuery;
  }

  private String buildFavoriteQueryStatement(List<String> values) {
    if (CollectionUtils.isEmpty(values)) {
      return "";
    }
    return new StringBuilder().append("{\"terms\":{")
                              .append("\"metadatas.favorites.metadataName.keyword\": [\"")
                              .append(StringUtils.join(values, "\",\""))
                              .append("\"]}},")
                              .toString();
  }

  private Map<String, List<String>> buildMetadatasFilter(DocumentNodeFilter filter,
                                                         org.exoplatform.social.core.identity.model.Identity userIdentity) {
    Map<String, List<String>> metadataFilters = new HashMap<>();
    if (filter.getFavorites()) {
      metadataFilters.put(FavoriteService.METADATA_TYPE.getName(), Collections.singletonList(userIdentity.getId()));
    }
    return metadataFilters;
  }
  protected String buildEscapedQueryWithAndOperator(String term) {
    List<String> queryParts = Arrays.asList(term.split(" "));
    if (queryParts.size() > 1) {
      StringBuilder escapedQueryWithAndOperator = new StringBuilder();
      String pattern = "[-+=&|><!(){}\\[\\]^\"*?:\\\\/]";
      Pattern regex = Pattern.compile(pattern);
      for (int i = 0 , j = 1 ; i < queryParts.size() && j < queryParts.size() ; i++ , j++) {
        Matcher matcher = regex.matcher(queryParts.get(i));
        Matcher nextMatcher = regex.matcher(queryParts.get(j));
        escapedQueryWithAndOperator.append(queryParts.get(i));
        if (matcher.find()){
          escapedQueryWithAndOperator.append("\\\\* AND ");
        } else {
          escapedQueryWithAndOperator.append("* AND ");
        }
        if (nextMatcher.find()) {
          escapedQueryWithAndOperator.append("\\\\*");
        } else {
          escapedQueryWithAndOperator.append("*");
        }
        if (j == queryParts.size() - 1) {
          escapedQueryWithAndOperator.append(queryParts.get(j));
          if (nextMatcher.find()){
            escapedQueryWithAndOperator.append("\\\\");
          }
        }
      }
      return escapedQueryWithAndOperator.toString();
    }
    return term;
  }
}
