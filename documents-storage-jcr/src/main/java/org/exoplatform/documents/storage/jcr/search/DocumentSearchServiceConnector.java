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
import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.StringUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import org.exoplatform.commons.search.es.ElasticSearchException;
import org.exoplatform.commons.search.es.client.ElasticSearchingClient;
import org.exoplatform.commons.utils.IOUtil;
import org.exoplatform.commons.utils.PropertyManager;
import org.exoplatform.container.configuration.ConfigurationManager;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.PropertiesParam;
import org.exoplatform.documents.legacy.search.data.SearchResult;
import org.exoplatform.documents.model.DocumentNodeFilter;
import org.exoplatform.documents.model.DocumentTimelineFilter;
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
          + "    \"fields\": [\"title.raw\"],"
          + "    \"query\": \"@term@\""
          + "  }"
          + "},";

  public static final String           EXTENDED_SEARCH_QUERY_TERM   = "\"must\":{"
          + "    \"query_string\":{"
          + "    \"fields\": [\"title\",\"attachment.content\",\"dc:description\"],"
          + "    \"query\": \"*@term@*\""
          + "  }" + "},";

  public static final String           QUERY_TAG_TERM           = "@term@";

  public static final String ASTERISK_STR      = "*";

  private static final String          IMAGES                   =
                                                  "\"image/bmp\",\"image/jpeg\",\"image/webp\",\"image/png\",\"image/gif\",\"image/avif\",\"image/tiff\"";

  private static final String          SHEETS                   =
                                                  "\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet\",\"application/vnd.oasis.opendocument.spreadsheet\",\"officedocument.spreadsheetml.sheet\",\"application/vnd.ms-excel\",\"text/csv\"";

  private static final String          PRESENTATIONS            =
                                                         "\"application/vnd.ms-powerpoint\",\"application/vnd.openxmlformats-officedocument.presentationml.presentation\",\"application/vnd.oasis.opendocument.presentation\"";

  private static final String          PDFS                     = "\"application/pdf\"";

  private static final String          ARCHIVES                 = "\"application/zip\",\"application/vnd.rar\"";

  private static final String          VIDEOS                   =
                                                  "\"video/x-msvideo\",\"video/mp4\",\"video/mpeg\",\"video/ogg\",\"video/webm\",\"video/3gpp\"";

  private static final String          DOCUMENTS                =
                                                     "\"application/vnd.openxmlformats-officedocument.wordprocessingml.document\",\"application/msword\",\"application/rtf\",\"application/vnd.oasis.opendocument.text\"";

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
    if (StringUtils.isBlank(filter.getQuery()) && BooleanUtils.isFalse(filter.getFavorites())
        && StringUtils.isEmpty(filter.getFileTypes())
        && filter.getAfterDate() == null && filter.getBeforeDate() == null && filter.getMaxSize() == null
        && filter.getMinSize() == null) {
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

    String esQuery = buildQueryStatement(userIdentity, workspace, path, filter, sortField, sortDirection, false, offset, limit);
    String jsonResponse = this.client.sendRequest(esQuery, this.index);
    return buildResult(jsonResponse);
  }

  public long getTotalSize(Identity userIdentity, String workspace, String path) {
    if (userIdentity == null) {
      throw new IllegalArgumentException("Viewer identity is mandatory");
    }
    String esQuery = buildQueryStatement(userIdentity,
                                         workspace,
                                         path,
                                         new DocumentTimelineFilter(),
                                         "lastUpdatedDate",
                                         "ASC",
                                         true,
                                         0,
                                         0);
    String jsonResponse = this.client.sendRequest(esQuery, this.index);
    try {
      JSONParser parser = new JSONParser();
      Map json;
      try {
        json = (Map) parser.parse(jsonResponse);
      } catch (ParseException e) {
        throw new ElasticSearchException("Unable to parse JSON response", e);
      }
      JSONObject jsonResult = (JSONObject) json.get("aggregations");
      jsonResult = (JSONObject) jsonResult.get("total_size");
      return ((Double) jsonResult.get("value")).longValue();
    } catch (Exception e) {
      throw new ElasticSearchException("Unable to get documents size", e);
    }
  }

  public String getLimit(int limit) {
    if (limit > 0) {
      return "\"size\":" + limit + ",";
    } else
      return "";
  }

  private String buildQueryStatement(Identity userIdentity,
                                     String workspace,
                                     String path,
                                     DocumentNodeFilter filter,
                                     String sortField,
                                     String sortDirection,
                                     boolean getTotalSize,
                                     int offset,
                                     int limit) {
    Map<String, List<String>> metadataFilters =
                                              buildMetadatasFilter(filter,
                                                                   identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME,
                                                                                                       userIdentity.getUserId()));
    String termQuery = buildTermQueryStatement(filter.getQuery(), BooleanUtils.isTrue(filter.isExtendedSearch()));
    String favoriteQuery = buildFavoriteQueryStatement(metadataFilters.get(FavoriteService.METADATA_TYPE.getName()));
    if (StringUtils.isNotEmpty(path) && !path.endsWith("/")) {
      path += "/";
    }
    return retrieveSearchQuery().replace("@term_query@", termQuery)
                                .replace("@favorite_query@", favoriteQuery)
                                .replace("@permissions@", getPermissionFilter(userIdentity))
                                .replace("@fileTypes_query@", getFileTypesQuery(filter))
                                .replace("@size_query@", getSizeQuery(filter))
                                .replace("@date_query@", getDatesQuery(filter))
                                .replace("@path@", path)
                                .replace("@workspace@", workspace)
                                .replace("@size_agg@", getSizeAgg(getTotalSize))
                                .replace("@sort_field@", sortField)
                                .replace("@sort_direction@", sortDirection)
                                .replace("@offset@", String.valueOf(offset))
                                .replace("@limit@", getLimit(limit));
  }

  private String getFileTypesQuery(DocumentNodeFilter filter) {

    if (StringUtils.isNotEmpty(filter.getFileTypes())) {
      List<String> types = new ArrayList<>();
      for (String type : filter.getFileTypes().split(",")) {
        try {
          types.add((String) this.getClass().getDeclaredField(type.toUpperCase()).get(0));
        } catch (Exception e) {
          LOG.warn("Cannot get list of mimeTypes related to type {}", type, e);
        }
      }
      return "{\n" +
              "   \"bool\":{\n" +
              "      \"should\":[\n" +
              "          {\n" +
              "           \"terms\":{\n" +
              "               \"fileType\":[\n" +
              types.stream().collect(Collectors.joining(",")) +
              "]\n" +
              "            }\n" +
              "         }\n" +
              "      ]\n" +
              "   }\n" +
              "},\n";
    }
    return "";
  }

  private String getSizeQuery(DocumentNodeFilter filter) {
    if (filter.getMinSize() != null || filter.getMaxSize() != null) {
      List<String> sizes = new ArrayList<>();
      if (filter.getMinSize() != null) {
        sizes.add("\"gte\": " + filter.getMinSize().longValue() * 1024 * 1024);
      }
      if (filter.getMaxSize() != null) {
        sizes.add("\"lte\": " + filter.getMaxSize().longValue() * 1024 * 1024);
      }
      return "{\n" +
              "   \"bool\":{\n" +
              "      \"should\":[\n" +
              "         {\n" +
              "           \"range\": {\n" +
              "             \"fileSize\": {\n" +
              sizes.stream().collect(Collectors.joining(",")) +
              "             }\n" +
              "        }\n" +
              "      }\n" +
              "    ]\n" +
              "  }\n" +
              "},\n";
    }
    return "";
  }

  private String getDatesQuery(DocumentNodeFilter filter) {
    if (filter.getAfterDate() != null || filter.getBeforeDate() != null) {
      List<String> dates = new ArrayList<>();
      if (filter.getAfterDate() != null) {
        dates.add("\"gte\": " + filter.getAfterDate());
      }
      if (filter.getBeforeDate() != null) {
        dates.add("\"lte\": " + filter.getBeforeDate());
      }
      return "{\n" +
              "   \"bool\":{\n" +
              "      \"should\":[\n" +
              "         {\n" +
              "           \"range\": {\n" +
              "             \"lastUpdatedDate\": {\n" +
              dates.stream().collect(Collectors.joining(",")) +
              "             }\n" +
              "        }\n" +
              "      }\n" +
              "    ]\n" +
              "  }\n" +
              "},\n";
    }
    return "";
  }

  private String getSizeAgg(boolean getTotalSize) {
    if (getTotalSize) {
      return "\"aggs\": {\n" + "      \"total_size\": { \"sum\": { \"field\": \"fileSize\" } }\n" + "    },";
    }
    return "";
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
    if (term.startsWith("#")) {
      term = term.substring(1);
    }
    String originalTerm = term;
    term = escapeReservedCharacters(term);
    String escapedQueryWithAndOperator = buildEscapedQueryWithAndOperator(term);
    boolean isEscapedReservedCharactersTerm = !originalTerm.equals(term);
    boolean isEscapedQueryWithAndOperator = !escapedQueryWithAndOperator.equals(term);
    if (!extendedSearch) {
      // If the reserved characters are escaped we will escape also the * character in the search query term.
      StringBuilder inputSplittedValue = new StringBuilder();
      String[] splittedValue = term.split(" ");
        for (int i = 0; i < splittedValue.length; i++) {
          if (StringUtils.isNotBlank(splittedValue[i])) {
            if (i!=0) {
              inputSplittedValue.append(" AND ");
            }
            inputSplittedValue.append("(");
            String replaceSpecialCharInputValue = replaceSpecialCharInputValue(splittedValue[i]);
            inputSplittedValue.append(replaceSpecialCharInputValue);
            String inputWithAsteriskStr = ASTERISK_STR.concat(replaceSpecialCharInputValue).concat(ASTERISK_STR);
            inputSplittedValue.append(" OR ").append(inputWithAsteriskStr);
            inputSplittedValue.append(")");
            if (term.contains(" ") && inputSplittedValue.indexOf(" AND (*\\\\ *)")==-1) {
              inputSplittedValue.append(" AND (*\\\\ *)");
            }
          }
        }
      return SEARCH_QUERY_TERM.replace(QUERY_TAG_TERM, inputSplittedValue.toString());
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
    if (BooleanUtils.isTrue(filter.getFavorites())) {
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

  private String replaceSpecialCharInputValue (String inputValue) {
    StringBuilder result = new StringBuilder();
    int i = 0;
    int j = 0;
    int numberLetterOrDigit = 0;
    for (char c : inputValue.toCharArray()) {
      if (!(Character.isLetterOrDigit(c) || !Character.isWhitespace(c))) {
        if ( j!=0 ) {
          result.append("\\\\").append(c);
        } else if ( i==0 ) {
          result.append(c);
          j++;
        } else {
          result.append(c);
          j=0;
        }
      } else {
        result.append(c);
        numberLetterOrDigit++;
        j=0;
      }
      i++;
    }
    if (numberLetterOrDigit == 0) {
      return "*";
    }
    return result.toString();
  }
}
